package com.fieldbook.tracker.devices.multispeq

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/**
 * Calculates output parameters from a raw RIDES 2.1 device response.
 *
 * The device returns JSON with a `set` array (one entry per sub-protocol that was
 * executed). Each entry has a `label` field matching the sub-protocol name.
 *
 * PAM fluorescence calculations follow the multi-phase flash approach from the
 * RIDES 2.1 macro (photosynthesis_rides_2.1_macro.js).
 * For a simplified first-pass the strongest saturation-flash Fm' is used directly.
 */
object MultispeqCalculator {

    private const val TAG = "MultispeqCalculator"

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * @param rawJson   JSON string received from the MultispeQ device
     * @param params    parameters to calculate
     * @return map from requested parameter to formatted value string (or "N/A" on failure)
     */
    fun calculate(
        rawJson: String,
        params: Set<MultispeqParameter>
    ): Map<MultispeqParameter, String> {
        val result = mutableMapOf<MultispeqParameter, String>()
        if (params.isEmpty()) return result

        val root = try { JSONObject(rawJson) } catch (e: Exception) {
            Log.w(TAG, "Failed to parse response JSON", e); return result
        }

        // Locate the sub-protocol set array — try `set` then `sample[0]`
        val setArray: JSONArray? = root.optJSONArray("set")
            ?: root.optJSONArray("sample")?.optJSONArray(0)
        if (setArray == null) {
            Log.w(TAG, "No 'set' or 'sample[0]' array in response")
            return result
        }

        val byLabel = buildLabelMap(setArray)

        // Grab PAR once since several calculations need it
        val par = extractPar(byLabel)

        for (param in params) {
            val value = try {
                when (param) {
                    // ---- Environment ----
                    MultispeqParameter.PAR         -> formatNumber(par)
                    MultispeqParameter.TEMPERATURE -> extractThp(byLabel, 0)
                    MultispeqParameter.HUMIDITY    -> extractThp(byLabel, 1)
                    MultispeqParameter.PRESSURE    -> extractThp(byLabel, 2)
                    MultispeqParameter.LEAF_TEMP   -> extractContactlessTemp(byLabel)
                    MultispeqParameter.ANGLE       -> extractAngle(byLabel)
                    // ---- Fluorescence (PAM) ----
                    MultispeqParameter.PHI2        -> calcPhi2(byLabel, par)?.let { formatNumber(it) }
                    MultispeqParameter.PHI_NPQ     -> calcPhiNpq(byLabel, par)
                    MultispeqParameter.PHI_NO      -> calcPhiNo(byLabel, par)
                    MultispeqParameter.NPQT        -> calcNpqt(byLabel)
                    MultispeqParameter.QL          -> calcQl(byLabel)
                    MultispeqParameter.LEF         -> calcLef(byLabel, par)
                    // ---- ECS ----
                    MultispeqParameter.ECST        -> calcEcst(byLabel)
                    MultispeqParameter.VHP         -> calcVhp(byLabel)
                    MultispeqParameter.GHP         -> calcGhp(byLabel)
                    // ---- PS1 ----
                    MultispeqParameter.PS1_ACTIVE       -> calcPs1Active(byLabel)
                    MultispeqParameter.PS1_OPEN         -> calcPs1Open(byLabel)
                    MultispeqParameter.PS1_OXIDIZED     -> calcPs1Oxidized(byLabel)
                    MultispeqParameter.PS1_OVER_REDUCED -> calcPs1OverReduced(byLabel)
                    // ---- SPAD ----
                    MultispeqParameter.SPAD_VALUE      -> extractSpad(byLabel)
                    MultispeqParameter.LEAF_THICKNESS  -> extractThickness(byLabel)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Error calculating ${param.name}", e); null
            }
            result[param] = value ?: "N/A"
        }

        return result
    }

    // -------------------------------------------------------------------------
    // Helpers — response parsing
    // -------------------------------------------------------------------------

    private fun buildLabelMap(setArray: JSONArray): Map<String, JSONObject> {
        val map = mutableMapOf<String, JSONObject>()
        for (i in 0 until setArray.length()) {
            val obj = setArray.optJSONObject(i) ?: continue
            val label = obj.optString("label").ifBlank { i.toString() }
            map[label] = obj
        }
        return map
    }

    /** Returns the baseline sub-protocol; falls back to first entry. */
    private fun baseline(byLabel: Map<String, JSONObject>): JSONObject? =
        byLabel["no_leaf_baseline"] ?: byLabel.values.firstOrNull()

    private fun pam(byLabel: Map<String, JSONObject>): JSONObject? = byLabel["PAM"]

    private fun ecs(byLabel: Map<String, JSONObject>): JSONObject? = byLabel["DIRK_ECS"]

    private fun spad(byLabel: Map<String, JSONObject>): JSONObject? = byLabel["SPAD"]

    // -------------------------------------------------------------------------
    // Environment extraction
    // -------------------------------------------------------------------------

    private fun extractPar(byLabel: Map<String, JSONObject>): Double? {
        val bl = baseline(byLabel) ?: return null
        val li = bl.opt("light_intensity")
        return when (li) {
            is Number -> li.toDouble()
            is JSONArray -> if (li.length() > 0) li.optDouble(0) else null
            else -> null
        }.takeIf { it != null && !it.isNaN() }
    }

    private fun extractThp(byLabel: Map<String, JSONObject>, index: Int): String? {
        // temperature_humidity_pressure from no_leaf_baseline or DIRK_ECS
        for (label in listOf("no_leaf_baseline", "DIRK_ECS", "PAM")) {
            val arr = byLabel[label]?.optJSONArray("temperature_humidity_pressure") ?: continue
            val v = arr.optDouble(index)
            if (!v.isNaN()) return formatNumber(v)
        }
        return null
    }

    private fun extractContactlessTemp(byLabel: Map<String, JSONObject>): String? {
        for (label in listOf("no_leaf_baseline", "DIRK_ECS", "PAM")) {
            val obj = byLabel[label] ?: continue
            val v = when (val raw = obj.opt("contactless_temp")) {
                is Number -> raw.toDouble()
                is JSONArray -> raw.optDouble(0)
                else -> null
            }
            if (v != null && !v.isNaN()) return formatNumber(v)
        }
        return null
    }

    private fun extractAngle(byLabel: Map<String, JSONObject>): String? {
        for (label in listOf("no_leaf_baseline", "DIRK_P700", "PAM")) {
            val arr = byLabel[label]?.optJSONArray("compass_and_angle") ?: continue
            // compass_and_angle: [heading, bearing, tilt]
            val tilt = arr.optDouble(2)
            if (!tilt.isNaN()) return formatNumber(tilt)
        }
        return null
    }

    // -------------------------------------------------------------------------
    // PAM fluorescence calculations
    //
    // PAM trace structure (RIDES 2.1):
    //   Two detectors [1,1] fire alternately → raw data interleaved [d1,d2,d1,d2,...]
    //   Detected pulse segments: 100 Fs + 30 Fm_flash + 15×5 MPF steps + 0(600 dark) + 15 Fo' + ...
    //   Total detected pulses ≈ 310 per detector → 620 raw values
    //   Reshape: first 310 = det1 (Chl/red), last 310 = det2 (P700/NIR)
    //
    //   Indices into reshaped det1 (Chl) trace:
    //     0–99   : Fs (steady state under actinic light)
    //     100–129: Fm_flash (saturating pulse at 8000)
    //     130–144: Fm_step1 (7000)
    //     145–159: Fm_step2 (6000)
    //     160–174: Fm_step3 (5000)
    //     175–189: Fm_step4 (4000)
    //     190–204: dark (actinic off)
    //     205–219: Fo' (far-red recovery)
    //
    //   Indices into det2 (P700/NIR) for PS1 (offset by 310):
    //     310–624 total; PSI_trace_beg=310 in full reshaped array → index 0 in det2
    // -------------------------------------------------------------------------

    private fun reshapePam(pamObj: JSONObject): DoubleArray? {
        val raw = pamObj.optJSONArray("data_raw") ?: return null
        val n = raw.length()
        if (n < 2) return null
        val arr = DoubleArray(n) { raw.optDouble(it) }
        // Reshape: even indices → first half, odd indices → second half
        val reshaped = DoubleArray(n)
        var i1 = 0; var i2 = n / 2
        for (i in arr.indices) {
            if (i % 2 == 0) reshaped[i1++] = arr[i] else reshaped[i2++] = arr[i]
        }
        return reshaped
    }

    private data class PamValues(
        val fs: Double,
        val fmPrime: Double,
        val foPrime: Double,
        val par: Double?
    )

    private fun getPamValues(byLabel: Map<String, JSONObject>, par: Double?): PamValues? {
        val pamObj = pam(byLabel) ?: return null
        val data = reshapePam(pamObj) ?: return null
        if (data.size < 310) return null

        val d = data  // det1 occupies indices 0..309

        // Fs = mean of indices 1..4 (skip index 0 which may have artifact)
        val fs = mean(d.slice(1..4))

        // Fm_flash = top-20 percentile mean of indices 100..129 (30-pulse saturation)
        val fmSlice = d.slice(100..129).sortedDescending()
        val fmPrime = fmSlice.take(max(1, fmSlice.size / 5)).average()

        // Fo' = minimum of indices 205..219 (after dark period)
        val foSlice = d.slice(205..minOf(219, d.size - 1))
        val foPrime = if (foSlice.isNotEmpty()) foSlice.min() else fs * 0.5

        return PamValues(fs, fmPrime, foPrime, par)
    }

    private fun calcPhi2(byLabel: Map<String, JSONObject>, par: Double?): Double? {
        val pv = getPamValues(byLabel, par) ?: return null
        val phi2 = (pv.fmPrime - pv.fs) / pv.fmPrime
        return phi2.takeIf { it in -0.1..0.95 }
    }

    private fun calcNpqt(byLabel: Map<String, JSONObject>): String? {
        val pv = getPamValues(byLabel, null) ?: return null
        if (pv.foPrime <= 0) return null
        val npqt = (4.88 / ((pv.fmPrime / pv.foPrime) - 1.0)) - 1.0
        return formatNumber(max(0.0, npqt))
    }

    private fun calcQl(byLabel: Map<String, JSONObject>): String? {
        val pv = getPamValues(byLabel, null) ?: return null
        val denom = (pv.fmPrime - pv.foPrime) * pv.fs
        if (abs(denom) < 1e-9) return null
        val qL = ((pv.fmPrime - pv.fs) * pv.foPrime) / denom
        return formatNumber(qL.coerceIn(0.0, 1.0))
    }

    private fun calcPhiNo(byLabel: Map<String, JSONObject>, par: Double?): String? {
        val pv = getPamValues(byLabel, par) ?: return null
        val phi2 = (pv.fmPrime - pv.fs) / pv.fmPrime
        if (pv.foPrime <= 0) return null
        val npqt = (4.88 / ((pv.fmPrime / pv.foPrime) - 1.0)) - 1.0
        val denom = (pv.fmPrime - pv.foPrime) * pv.fs
        val qL = if (abs(denom) > 1e-9)
            ((pv.fmPrime - pv.fs) * pv.foPrime) / denom
        else 0.0
        val phiNo = 1.0 / (npqt + 1.0 + qL * 4.88)
        return formatNumber(phiNo.coerceIn(0.0, 1.0))
    }

    private fun calcPhiNpq(byLabel: Map<String, JSONObject>, par: Double?): String? {
        val pv = getPamValues(byLabel, par) ?: return null
        val phi2 = (pv.fmPrime - pv.fs) / pv.fmPrime
        if (pv.foPrime <= 0) return null
        val npqt = (4.88 / ((pv.fmPrime / pv.foPrime) - 1.0)) - 1.0
        val denom = (pv.fmPrime - pv.foPrime) * pv.fs
        val qL = if (abs(denom) > 1e-9)
            ((pv.fmPrime - pv.fs) * pv.foPrime) / denom
        else 0.0
        val phiNo = 1.0 / (npqt + 1.0 + qL * 4.88)
        val phiNpq = 1.0 - phi2 - phiNo
        return formatNumber(phiNpq.coerceIn(0.0, 1.0))
    }

    private fun calcLef(byLabel: Map<String, JSONObject>, par: Double?): String? {
        val phi2 = calcPhi2(byLabel, par) ?: return null
        val lightPar = par ?: return null
        if (lightPar <= 0) return null
        return formatNumber(phi2 * 0.45 * lightPar)
    }

    // -------------------------------------------------------------------------
    // ECS calculations
    // DIRK_ECS data_raw: 100 baseline + 6×220 subtraces = 1420 total
    // We average the 6 subtraces and take the amplitude of the decay
    // -------------------------------------------------------------------------

    private fun calcEcst(byLabel: Map<String, JSONObject>): String? {
        val ecsObj = ecs(byLabel) ?: return null
        val raw = ecsObj.optJSONArray("data_raw") ?: return null
        val n = raw.length()
        if (n < 320) return null

        // Average 6 subtraces (each 220 points starting at index 100)
        val averaged = DoubleArray(220)
        val beginning = 100
        val subLength = 220
        val numSubtraces = min(6, (n - beginning) / subLength)
        if (numSubtraces < 1) return null

        for (i in 0 until numSubtraces) {
            val offset = beginning + i * subLength
            for (j in 0 until subLength) {
                averaged[j] += raw.optDouble(offset + j)
            }
        }
        for (j in averaged.indices) averaged[j] /= numSubtraces

        // Compute baseline linear regression on points 75..145
        val baselineSlice = averaged.slice(75..min(145, averaged.size - 1))
        val baselineOffset = linearFit(baselineSlice)

        // Convert to absorbance: -ln(I/I0) and compute amplitude
        var ecsT = 0.0
        for (j in averaged.indices) {
            val bOff = baselineOffset[min(j, baselineOffset.size - 1)]
            if (bOff > 0) {
                averaged[j] = -ln(averaged[j] / bOff)
                ecsT = max(ecsT, abs(averaged[j]))
            }
        }

        // Report in mAU (milli-absorbance units × 1000)
        return formatNumber(ecsT * 1000, 3)
    }

    private fun calcGhp(byLabel: Map<String, JSONObject>): String? {
        // gH+ = 1/tau where tau is the decay time constant of the ECS trace
        // Simplified: use the ratio of amplitudes at two timepoints to estimate tau
        val ecsObj = ecs(byLabel) ?: return null
        val raw = ecsObj.optJSONArray("data_raw") ?: return null
        if (raw.length() < 320) return null

        // Use decay from index 100 to 120 in the averaged trace
        val beginning = 100
        val subLength = 220
        val numSubtraces = min(6, (raw.length() - beginning) / subLength)
        if (numSubtraces < 1) return null
        val averaged = DoubleArray(subLength)
        for (i in 0 until numSubtraces) {
            for (j in 0 until subLength) {
                averaged[j] += raw.optDouble(beginning + i * subLength + j)
            }
        }
        for (j in averaged.indices) averaged[j] /= numSubtraces

        val v0 = averaged.slice(100..119).average()
        val v1 = averaged.slice(115..119).average()
        if (v0 <= 0 || v1 <= 0 || v0 == v1) return null
        // tau in units of 1.5ms per point
        val ratio = v1 / v0
        if (ratio <= 0 || ratio >= 1) return null
        val tauPoints = -15.0 / ln(ratio)  // 15 points span
        val tauMs = tauPoints * 1.5
        val gH = 1000.0 / tauMs  // s⁻¹
        return formatNumber(gH, 3)
    }

    private fun calcVhp(byLabel: Map<String, JSONObject>): String? {
        val ecsStr = calcEcst(byLabel) ?: return null
        val ghpStr = calcGhp(byLabel) ?: return null
        val ecst = ecsStr.toDoubleOrNull() ?: return null
        val ghp = ghpStr.toDoubleOrNull() ?: return null
        return formatNumber(ecst * ghp / 1000.0, 3)
    }

    // -------------------------------------------------------------------------
    // PS1 calculations (from PAM dual-detector, P700/NIR channel)
    // Reshaped PAM.data_raw: det2 (P700) is at indices 310..619 in the full array
    // -------------------------------------------------------------------------

    private fun calcPs1(byLabel: Map<String, JSONObject>): DoubleArray? {
        val pamObj = pam(byLabel) ?: return null
        val data = reshapePam(pamObj) ?: return null
        if (data.size < 615) return null

        // P700/NIR data starts at index 310 in the reshaped array (det2 portion)
        // Relative to PSI_trace_beg=310: use indices 310..614
        val psiData = data.slice(310..614).toDoubleArray()
        val psiLength = psiData.size  // 305

        // Convert to absorbance relative to dark reference
        val darkRef = mean(psiData.slice(195..200))
        if (darkRef <= 0) return null
        val abs = DoubleArray(psiLength) { ln(darkRef / psiData[it]) }

        // PSI steady state: indices 1..18
        val psiSs = mean(abs.slice(1..17)) * 1000.0

        // PSI sat1 (first big flash): indices 25..170 — use top 20%
        val sat1Vals = abs.slice(25..170).sortedDescending()
        val psiSat1 = mean(sat1Vals.take(max(1, sat1Vals.size / 5))) * 1000.0

        // PSI sat2 (second flash): indices 220..270 — use top 20%
        val sat2Vals = abs.slice(220..minOf(270, abs.size - 1)).sortedDescending()
        val psiSat2 = mean(sat2Vals.take(max(1, sat2Vals.size / 5))) * 1000.0

        if (psiSat2 <= 0) return null

        val psiOx = psiSs / psiSat2
        val psiAct = psiSat2
        val psiOpen = (psiSat1 - psiSs) / psiSat2
        val psiOr = 1.0 - psiSat1 / psiSat2

        return doubleArrayOf(psiAct, psiOpen, psiOx, psiOr)
    }

    private fun calcPs1Active(byLabel: Map<String, JSONObject>): String? =
        calcPs1(byLabel)?.let { formatNumber(it[0], 3) }

    private fun calcPs1Open(byLabel: Map<String, JSONObject>): String? =
        calcPs1(byLabel)?.let { formatNumber(it[1], 3) }

    private fun calcPs1Oxidized(byLabel: Map<String, JSONObject>): String? =
        calcPs1(byLabel)?.let { formatNumber(it[2], 3) }

    private fun calcPs1OverReduced(byLabel: Map<String, JSONObject>): String? =
        calcPs1(byLabel)?.let { formatNumber(it[3], 3) }

    // -------------------------------------------------------------------------
    // SPAD extraction
    // -------------------------------------------------------------------------

    private fun extractSpad(byLabel: Map<String, JSONObject>): String? {
        val spadObj = spad(byLabel) ?: return null
        val spadArr = spadObj.optJSONArray("spad")
        if (spadArr != null) {
            val v = spadArr.optDouble(0)
            if (!v.isNaN()) return formatNumber(v, 1)
        }
        // Some firmware returns spad as a direct number
        val spadDirect = spadObj.optDouble("spad")
        if (!spadDirect.isNaN()) return formatNumber(spadDirect, 1)
        return null
    }

    private fun extractThickness(byLabel: Map<String, JSONObject>): String? {
        // thickness from DIRK_ECS or SPAD
        for (label in listOf("DIRK_ECS", "PAM", "SPAD")) {
            val v = byLabel[label]?.optDouble("thickness")
            if (v != null && !v.isNaN() && v > 0) return formatNumber(v, 3)
        }
        return null
    }

    // -------------------------------------------------------------------------
    // Math helpers
    // -------------------------------------------------------------------------

    private fun mean(values: Collection<Double>): Double =
        if (values.isEmpty()) 0.0 else values.sum() / values.size

    private fun mean(arr: DoubleArray): Double =
        if (arr.isEmpty()) 0.0 else arr.sum() / arr.size

    private fun DoubleArray.slice(range: IntRange): List<Double> =
        range.filter { it in indices }.map { this[it] }

    /** Simple linear fit returning an array of fitted values over [0, n-1]. */
    private fun linearFit(points: List<Double>): DoubleArray {
        val n = points.size
        if (n < 2) return DoubleArray(1) { points.firstOrNull() ?: 0.0 }
        val xMean = (n - 1) / 2.0
        val yMean = mean(points)
        var num = 0.0; var den = 0.0
        for (i in points.indices) {
            num += (i - xMean) * (points[i] - yMean)
            den += (i - xMean) * (i - xMean)
        }
        val m = if (den != 0.0) num / den else 0.0
        val b = yMean - m * xMean
        return DoubleArray(n) { i -> m * i + b }
    }

    private fun formatNumber(v: Double?, decimals: Int = 2): String {
        if (v == null || v.isNaN() || v.isInfinite()) return "N/A"
        return String.format("%.${decimals}f", v)
    }
}
