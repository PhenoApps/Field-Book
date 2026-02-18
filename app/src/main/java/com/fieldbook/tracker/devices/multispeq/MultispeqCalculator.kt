package com.fieldbook.tracker.devices.multispeq

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.exp
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

        Log.d(TAG, "calculate: rawJson length=${rawJson.length}, params=${params.map { it.name }}")

        val root = try { JSONObject(rawJson) } catch (e: Exception) {
            Log.w(TAG, "Failed to parse response JSON", e); return result
        }

        // Locate the sub-protocol set array.
        // Device response structure: { "sample": [{ "protocol_id": ..., "set": [...] }] }
        // Fallbacks for older/alternate firmware: { "set": [...] } at root level.
        val sampleArray = root.optJSONArray("sample")
        val setArray: JSONArray? = root.optJSONArray("set")
            ?: sampleArray?.optJSONObject(0)?.optJSONArray("set")  // sample[0].set (standard RIDES 2.1)
            ?: sampleArray?.optJSONArray(0)                        // sample = [[{sub-protocols}]]
            ?: sampleArray                                         // sample = [{sub-protocols}] directly
        if (setArray == null) {
            Log.w(TAG, "No 'set' or 'sample' array in response; top-level keys: ${root.keys().asSequence().toList()}")
            return result
        }

        val byLabel = buildLabelMap(setArray)
        Log.d(TAG, "Sub-protocol labels found: ${byLabel.keys.toList()}")

        // Log keys of each sub-protocol to diagnose missing fields
        for ((label, obj) in byLabel) {
            Log.d(TAG, "  sub-protocol '$label' keys: ${obj.keys().asSequence().toList()}")
        }

        // Grab PAR once since several calculations need it
        val par = extractPar(byLabel)
        Log.d(TAG, "PAR extracted: $par")

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
            val finalValue = value ?: "N/A"
            Log.d(TAG, "  ${param.name} = $finalValue")
            result[param] = finalValue
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
        val bl = ecs(byLabel) ?: return null
        val li = bl.opt("light_intensity")
        return when (li) {
            is Number -> li.toDouble()
            is JSONArray -> if (li.length() > 0) li.optDouble(0) else null
            else -> null
        }.takeIf { it != null && !it.isNaN() }
    }

    private fun extractThp(byLabel: Map<String, JSONObject>, index: Int): String? {
        val key = when (index) {
            0 -> "temperature"
            1 -> "humidity"
            2 -> "pressure"
            else -> return null
        }
        for (label in listOf("no_leaf_baseline", "DIRK_ECS", "PAM")) {
            val obj = byLabel[label] ?: continue
            val v = obj.optDouble(key)
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
            val obj = byLabel[label] ?: continue
            val v = obj.optDouble("angle")
            if (!v.isNaN()) return formatNumber(v)
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
    // ECS calculations (DIRK_ECS)
    //
    // Data structure: 100-point baseline, then 6 × 220-point subtraces.
    // Subtraces are summed (not averaged — ratio makes scaling irrelevant).
    // Spike artifact at index 120 (light-on transition) is corrected before
    // the absorbance conversion.
    //
    // Baseline: linear regression on indices 75..144, applied as m*j+b for
    // ALL j (proper extrapolation, matching JS: baseline_offset[j] = j*m + b).
    //
    // Fit window for ECSt/gH+: indices 100..119 (20 points = 30 ms at 1.5 ms/pt).
    // This matches the JS NonLinearRegression window exactly.
    //
    // ECSt ≈ mean absorbance at the start of the fit window (amplitude proxy).
    // gH+  = 1/tau via log-linear regression on the same window.
    // -------------------------------------------------------------------------

    /** Builds the ECS absorbance trace shared by calcEcst and calcGhp. */
    private fun ecsAbsTrace(byLabel: Map<String, JSONObject>): DoubleArray? {
        val ecsObj = ecs(byLabel) ?: return null
        val raw = ecsObj.optJSONArray("data_raw") ?: return null
        val n = raw.length()
        if (n < 320) return null

        val beginning = 100
        val subLength = 220
        val numSubtraces = min(6, (n - beginning) / subLength)
        if (numSubtraces < 1) return null

        // Sum all subtraces (JS initialises with first slice then adds i=1..5)
        val summed = DoubleArray(subLength)
        for (i in 0 until numSubtraces) {
            val offset = beginning + i * subLength
            for (j in 0 until subLength) {
                summed[j] += raw.optDouble(offset + j)
            }
        }

        // Correct spike artifact at index 120 (matches JS: trace[120]=trace[119])
        summed[120] = summed[119]

        // Linear baseline on indices 75..144 using ACTUAL j as the x-axis.
        // Matches JS: baseline_offset[j] = j * reg.m + reg.b
        val bsStart = 75; val bsEnd = 145   // JS: slice(75, 145) → 75..144
        val xMean = (bsStart until bsEnd).map { it.toDouble() }.average()
        val yMean = (bsStart until bsEnd).map { summed[it] }.average()
        var rNum = 0.0; var rDen = 0.0
        for (j in bsStart until bsEnd) {
            rNum += (j - xMean) * (summed[j] - yMean)
            rDen += (j - xMean) * (j - xMean)
        }
        val slope = if (rDen != 0.0) rNum / rDen else 0.0
        val intercept = yMean - slope * xMean

        return DoubleArray(subLength) { j ->
            val bOff = slope * j + intercept
            if (bOff > 0) -ln(summed[j] / bOff) else Double.NaN
        }
    }

    private data class ExpDecayFit(val a: Double, val b: Double, val cMs: Double)

    /**
     * Extracts decay window points from the ECS absorbance trace and fits
     * y = b + a·exp(−x/c) via Gauss-Newton NLR, matching the JS
     * NonLinearRegression(b + a*exp(-x/c)) call on absTrace[100..119].
     * x is in ms (1.5 ms per index step).
     */
    private fun ecsDecayFit(byLabel: Map<String, JSONObject>): ExpDecayFit? {
        val absTrace = ecsAbsTrace(byLabel) ?: return null
        val xs = mutableListOf<Double>()
        val ys = mutableListOf<Double>()
        for (j in 100 until 120) {
            val y = absTrace[j]
            if (!y.isNaN()) {
                xs.add((j - 100).toDouble() * 1.5)
                ys.add(y)
            }
        }
        if (xs.size < 4) {
            Log.d(TAG, "ecsDecayFit: insufficient valid points (${xs.size})")
            return null
        }
        return fitExpDecay(xs.toDoubleArray(), ys.toDoubleArray()).also {
            Log.d(TAG, "ecsDecayFit: a=${it?.a}, b=${it?.b}, c=${it?.cMs} ms")
        }
    }

    /**
     * Fits y = b + a·exp(−x/c) using Gauss-Newton NLR iteration.
     * Returns null if the fit fails or parameters are non-physical (a≤0, c≤0).
     */
    private fun fitExpDecay(xs: DoubleArray, ys: DoubleArray): ExpDecayFit? {
        val n = xs.size
        if (n < 4) return null

        // Initial estimates: b from tail, a from head−b, c from log-linear
        var b = ys.takeLast(minOf(5, n)).average()
        var a = ys.take(minOf(3, n)).average() - b
        if (a <= 0) return null

        var c = run {
            val pairs = xs.zip(ys.toList()).mapNotNull { (x, y) ->
                val s = y - b; if (s > 0) Pair(x, ln(s)) else null
            }
            if (pairs.size < 2) return@run 10.0
            val xM = pairs.map { it.first }.average()
            val yM = pairs.map { it.second }.average()
            var num = 0.0; var den = 0.0
            for ((x, ly) in pairs) { num += (x - xM) * (ly - yM); den += (x - xM) * (x - xM) }
            val slope = if (den != 0.0) num / den else 0.0
            if (slope >= 0) 10.0 else -1.0 / slope
        }
        if (c <= 0) c = 10.0

        // Gauss-Newton: minimise sum((y_i − f(x_i))²) for f(x) = b + a·exp(−x/c)
        // Jacobian columns: ∂f/∂a = exp(−x/c), ∂f/∂b = 1, ∂f/∂c = a·x/c²·exp(−x/c)
        repeat(100) {
            val r  = DoubleArray(n)
            val Ja = DoubleArray(n); val Jb = DoubleArray(n); val Jc = DoubleArray(n)
            for (i in 0 until n) {
                val e = exp(-xs[i] / c)
                r[i]  = ys[i] - (b + a * e)
                Ja[i] = e; Jb[i] = 1.0; Jc[i] = a * xs[i] / (c * c) * e
            }
            val cols = arrayOf(Ja, Jb, Jc)
            val jtj = Array(3) { row ->
                DoubleArray(3) { col -> (0 until n).sumOf { cols[row][it] * cols[col][it] } }
            }
            val jtr = DoubleArray(3) { row -> (0 until n).sumOf { cols[row][it] * r[it] } }
            val delta = gaussianSolve3(jtj, jtr) ?: return@repeat
            a += delta[0]; b += delta[1]; c += delta[2]
            if (c <= 0) c = 1e-6
        }

        return if (a > 0 && c > 0 && !a.isNaN() && !b.isNaN() && !c.isNaN())
            ExpDecayFit(a, b, c) else null
    }

    /** Solves 3×3 linear system Ax = b via Gaussian elimination with partial pivoting. */
    private fun gaussianSolve3(A: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val m = Array(3) { i -> DoubleArray(4) { j -> if (j < 3) A[i][j] else b[i] } }
        for (col in 0..2) {
            var maxRow = col
            for (row in col + 1..2) if (abs(m[row][col]) > abs(m[maxRow][col])) maxRow = row
            val tmp = m[col]; m[col] = m[maxRow]; m[maxRow] = tmp
            if (abs(m[col][col]) < 1e-14) return null
            val piv = m[col][col]
            for (j in col..3) m[col][j] /= piv
            for (row in 0..2) {
                if (row == col) continue
                val f = m[row][col]
                for (j in col..3) m[row][j] -= f * m[col][j]
            }
        }
        return DoubleArray(3) { m[it][3] }
    }

    private fun calcEcst(byLabel: Map<String, JSONObject>): String? {
        val fit = ecsDecayFit(byLabel) ?: return null
        // ECSt = NLR amplitude 'a' (raw absorbance AU), matching JS NonLinearRegression
        return formatNumber(fit.a, 5)
    }

    private fun calcGhp(byLabel: Map<String, JSONObject>): String? {
        val fit = ecsDecayFit(byLabel) ?: return null
        // gH⁺ = 1000 / c [s⁻¹], c in ms
        return formatNumber(1000.0 / fit.cMs, 3)
    }

    private fun calcVhp(byLabel: Map<String, JSONObject>): String? {
        val fit = ecsDecayFit(byLabel) ?: return null
        return formatNumber(fit.a * (1000.0 / fit.cMs), 5)
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
        // PSI_dark_beg=195, PSI_dark_end=200 → slice(195,200) = indices 195..199 (5 pts)
        val darkRef = mean(psiData.slice(195 until 200))
        if (darkRef <= 0) return null
        val abs = DoubleArray(psiLength) { ln(darkRef / psiData[it]) }

        // PSI steady state: PSI_ss_beg=1, PSI_ss_end=18 → slice(1,18) = indices 1..17
        val psiSs = mean(abs.slice(1 until 18)) * 1000.0

        // PSI sat1: PSI_sat1_beg=25, PSI_sat1_end=170 → slice(25,170) = 145 pts, top 20% = 29
        val sat1Vals = abs.slice(25 until 170).sortedDescending()
        val psiSat1 = mean(sat1Vals.take(max(1, sat1Vals.size / 5))) * 1000.0

        // PSI sat2: PSI_sat2_beg=220, PSI_sat2_end=270 → slice(220,270) = 50 pts, top 20% = 10
        val sat2Vals = abs.slice(220 until minOf(270, abs.size)).sortedDescending()
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
