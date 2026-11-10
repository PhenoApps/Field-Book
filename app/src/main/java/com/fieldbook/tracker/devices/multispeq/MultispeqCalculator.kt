package com.fieldbook.tracker.devices.multispeq

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/**
 * Calculates output parameters from a raw MultispeQ device response.
 *
 * RIDES values reproduce the openJII "Photosynthesis RIDES 2.1" macro
 * (res/raw/photosynthesis_rides_2_1_macro.js) as run with the openJII helper library:
 * the same trace windows, multi-phase flash correction, log10 absorbance and rounding.
 * PAR, SPAD and Phi2 follow the JII-MultispeQ-Protocols analyses for those protocols.
 *
 * Values are returned as plain decimal strings, or "N/A" when they can't be calculated.
 */
object MultispeqCalculator {

    private const val TAG = "MultispeqCalculator"
    private const val NA = "N/A"

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * @param rawJson   JSON string received from the MultispeQ device
     * @param params    parameters to calculate
     * @param protocol  protocol the response was measured with
     * @return map from requested parameter to formatted value string (or "N/A" on failure)
     */
    fun calculate(
        rawJson: String,
        params: Set<MultispeqParameter>,
        protocol: MultispeqProtocolType = MultispeqProtocolType.RIDES
    ): Map<MultispeqParameter, String> {
        if (params.isEmpty()) return emptyMap()

        Log.d(TAG, "calculate: protocol=$protocol, rawJson length=${rawJson.length}, params=${params.map { it.name }}")

        val measurement = try {
            measurement(JSONObject(rawJson))
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse response JSON", e)
            return params.associateWith { NA }
        }

        val values: Map<MultispeqParameter, String?> = try {
            when (protocol) {
                MultispeqProtocolType.PAR -> mapOf(
                    MultispeqParameter.PAR to plain(number(measurement, "light_intensity"))
                )
                MultispeqProtocolType.SPAD -> mapOf(
                    MultispeqParameter.SPAD_VALUE to plain(spad(labelMap(measurement)))
                )
                MultispeqProtocolType.PHI2 -> phi2Protocol(measurement)
                MultispeqProtocolType.RIDES -> rides(labelMap(measurement), params)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error calculating $protocol parameters", e)
            emptyMap()
        }

        return params.associateWith { param ->
            (values[param] ?: NA).also { Log.d(TAG, "  ${param.name} = $it") }
        }
    }

    // -------------------------------------------------------------------------
    // Response parsing
    // -------------------------------------------------------------------------

    /**
     * The measurement object holding either the protocol `set` or, for single protocols,
     * `data_raw` and `light_intensity` directly. Firmware wraps it as `sample[0]` or `sample[0][0]`.
     */
    private fun measurement(root: JSONObject): JSONObject {
        val sample = root.opt("sample") as? JSONArray ?: return root
        return when (val first = sample.opt(0)) {
            is JSONObject -> first
            is JSONArray -> first.optJSONObject(0) ?: root
            else -> root
        }
    }

    private fun labelMap(measurement: JSONObject): Map<String, JSONObject> {
        val set = measurement.optJSONArray("set") ?: return emptyMap()
        val map = mutableMapOf<String, JSONObject>()
        for (i in 0 until set.length()) {
            val obj = set.optJSONObject(i) ?: continue
            map.putIfAbsent(obj.optString("label"), obj)
        }
        Log.d(TAG, "Sub-protocol labels found: ${map.keys.toList()}")
        return map
    }

    /** A numeric field, or the first element when the device reports it as an array. */
    private fun number(obj: JSONObject?, key: String): Double? {
        val v = when (val raw = obj?.opt(key)) {
            is Number -> raw.toDouble()
            is JSONArray -> raw.optDouble(0)
            else -> null
        }
        return v?.takeIf { it.isFinite() }
    }

    private fun dataRaw(obj: JSONObject?): DoubleArray? {
        val raw = obj?.optJSONArray("data_raw") ?: return null
        return DoubleArray(raw.length()) { raw.optDouble(it) }
    }

    /** SPAD is labelled "SPAD" in RIDES and "spad" in the stand-alone protocol. */
    private fun spad(byLabel: Map<String, JSONObject>): Double? =
        number(byLabel.entries.firstOrNull { it.key.equals("spad", ignoreCase = true) }?.value, "spad")

    // -------------------------------------------------------------------------
    // Phi2 protocol (JII-MultispeQ-Protocols phi2.py)
    //
    // 20 pulses under ambient light, 50 under a saturating pulse, 20 under ambient light again.
    // -------------------------------------------------------------------------

    private fun phi2Protocol(measurement: JSONObject): Map<MultispeqParameter, String?> {
        val par = number(measurement, "light_intensity")
        val data = dataRaw(measurement)?.takeIf { it.size >= 68 }
            ?: return mapOf(MultispeqParameter.PAR to plain(par))

        val fs = data.meanOf(1, 5)
        val fmPrime = data.meanOf(63, 68)
        val phi2 = (fmPrime - fs) / fmPrime
        val lef = par?.let { phi2 * it * 0.45 }

        return mapOf(
            MultispeqParameter.PHI2 to rounded(phi2, 3),
            MultispeqParameter.LEF to lef?.let { rounded(it, 2) },
            MultispeqParameter.PAR to plain(par)
        )
    }

    // -------------------------------------------------------------------------
    // RIDES 2.1
    // -------------------------------------------------------------------------

    private fun rides(
        byLabel: Map<String, JSONObject>,
        params: Set<MultispeqParameter>
    ): Map<MultispeqParameter, String?> {
        val baseline = byLabel["no_leaf_baseline"]
        val ecs = byLabel["DIRK_ECS"]
        val p700 = byLabel["DIRK_P700"]
        val pam = byLabel["PAM"]

        // The macro reads PAR from DIRK_ECS and ambient conditions from json.set[3], which is PAM
        val par = number(ecs, "light_intensity")
        val ambient = pam ?: baseline

        val out = mutableMapOf<MultispeqParameter, String?>(
            MultispeqParameter.PAR to plain(par),
            MultispeqParameter.TEMPERATURE to plain(number(ambient, "temperature")),
            MultispeqParameter.HUMIDITY to plain(number(ambient, "humidity")),
            MultispeqParameter.PRESSURE to plain(number(ambient, "pressure")),
            MultispeqParameter.LEAF_TEMP to plain(number(ecs, "contactless_temp")),
            MultispeqParameter.ANGLE to plain(number(p700 ?: baseline, "angle")),
            MultispeqParameter.LEAF_THICKNESS to plain(number(ecs, "thickness")),
            MultispeqParameter.SPAD_VALUE to plain(spad(byLabel))
        )

        val needsPam = params.any {
            it.group == MultispeqParameter.Group.FLUORESCENCE || it.group == MultispeqParameter.Group.PS1
        }
        val pamData = if (needsPam) dataRaw(pam)?.let { deinterleave(it) } else null

        if (pamData != null && params.any { it.group == MultispeqParameter.Group.FLUORESCENCE }) {
            fluorescence(pamData)?.let { f ->
                // Phi2 at or above 0.85 is flagged as out of range and not reported
                val phi2 = f.phi2.takeIf { it < 0.85 }
                out[MultispeqParameter.PHI2] = phi2?.let { rounded(it, 3) }
                out[MultispeqParameter.PHI_NPQ] = rounded(f.phiNpq, 3)
                out[MultispeqParameter.PHI_NO] = rounded(f.phiNo, 3)
                out[MultispeqParameter.NPQT] = rounded(f.npqt, 3)
                out[MultispeqParameter.QL] = rounded(f.qL, 3)
                out[MultispeqParameter.LEF] = if (phi2 != null && par != null) {
                    rounded(0.45 * jsRound(phi2, 3) * par, 2)
                } else null
            }
        }

        if (pamData != null && params.any { it.group == MultispeqParameter.Group.PS1 }) {
            ps1(pamData)?.let { p ->
                out[MultispeqParameter.PS1_ACTIVE] = rounded(p.active, 3)
                out[MultispeqParameter.PS1_OPEN] = rounded(p.open, 3)
                out[MultispeqParameter.PS1_OXIDIZED] = rounded(p.oxidized, 3)
                out[MultispeqParameter.PS1_OVER_REDUCED] = rounded(p.overReduced, 3)
            }
        }

        if (params.any { it.group == MultispeqParameter.Group.ECS }) {
            dataRaw(ecs)?.let { ecsDecayFit(it) }?.let { fit ->
                val ecst = jsRound(fit.a, 5)
                val ghp = jsRound(1000.0 / fit.c, 3)
                out[MultispeqParameter.ECST] = rounded(fit.a, 5)
                out[MultispeqParameter.GHP] = rounded(1000.0 / fit.c, 3)
                out[MultispeqParameter.VHP] = rounded(ecst * ghp, 3)
            }
        }

        return out
    }

    /**
     * PAM detectors fire alternately, so data_raw is [d1, d2, d1, d2, ...].
     * Returns [d1..., d2...]: chlorophyll fluorescence at 0..309, P700 at 310..619.
     */
    private fun deinterleave(raw: DoubleArray): DoubleArray {
        val half = (raw.size + 1) / 2
        val out = DoubleArray(raw.size)
        for (i in raw.indices) {
            out[if (i % 2 == 0) i / 2 else half + i / 2] = raw[i]
        }
        return out
    }

    // -------------------------------------------------------------------------
    // PAM fluorescence
    //
    // Chlorophyll trace (first 310 points of the de-interleaved PAM data):
    //   1..3     Fs, steady state under actinic light
    //   101..129 apparent Fm' under the strongest saturating pulse
    //   131..144, 146..159, 161..174  Fm' steps at decreasing intensity (multi-phase flash)
    //   206..219 Fo' after far-red
    //
    // Fm' is extrapolated to infinite intensity by regressing the four Fm' values against
    // inverse intensity (Avenson multi-phase flash). If that slope is positive the
    // extrapolation is unreliable and the apparent Fm' is used instead.
    // -------------------------------------------------------------------------

    private class Fluorescence(
        val phi2: Double,
        val npqt: Double,
        val qL: Double,
        val phiNo: Double,
        val phiNpq: Double
    )

    private val INVERSE_INTENSITY = doubleArrayOf(1 / 8000.0, 1 / 7000.0, 1 / 6000.0, 1 / 5000.0)

    private fun fluorescence(pam: DoubleArray): Fluorescence? {
        if (pam.size < 310) return null
        val d = pam.copyOfRange(0, 310)

        val fs = d.meanOf(1, 4)
        val foPrime = d.copyOfRange(206, 220).min()

        // Mean of the sorted values in [from, to) — the macro's "drop the two lowest" averaging
        fun sortedMean(begin: Int, end: Int, from: Int, to: Int) =
            d.copyOfRange(begin, end).sorted().subList(from, to).average()

        val apparentFmPrime = sortedMean(101, 130, 2, 20)
        val steps = listOf(
            apparentFmPrime,
            sortedMean(131, 145, 2, 6),
            sortedMean(146, 160, 2, 6),
            sortedMean(161, 175, 2, 6)
        )
        val (slope, intercept) = linearRegression(INVERSE_INTENSITY, steps.toDoubleArray())
        val fmPrime = if (slope > 0) apparentFmPrime else intercept

        val phi2 = (fmPrime - fs) / fmPrime
        val npqt = 4.88 / (fmPrime / foPrime - 1) - 1
        val qL = ((fmPrime - fs) * foPrime) / ((fmPrime - foPrime) * fs)
        // Kramer et al. 2004, equations 52 and 53
        val phiNo = 1 / (npqt + 1 + qL * 4.88)
        val phiNpq = 1 - phi2 - phiNo

        return Fluorescence(phi2, npqt, qL, phiNo, phiNpq)
    }

    // -------------------------------------------------------------------------
    // PS1 (P700 channel of the de-interleaved PAM data, points 310..614)
    // -------------------------------------------------------------------------

    private class Ps1(val active: Double, val open: Double, val oxidized: Double, val overReduced: Double)

    private fun ps1(pam: DoubleArray): Ps1? {
        if (pam.size < 615) return null
        val psi = pam.copyOfRange(310, 615)

        val dark = psi.meanOf(195, 200)
        val absorbance = DoubleArray(psi.size) { log10(dark / psi[it]) }

        val ss = 1000 * absorbance.meanOf(1, 18)
        // Top 20% of each saturation window
        val sat1 = 1000 * absorbance.copyOfRange(25, 170).sorted().subList(116, 145).average()
        val sat2 = 1000 * absorbance.copyOfRange(220, 270).sorted().subList(40, 50).average()

        return Ps1(
            active = sat2,
            open = (sat1 - ss) / sat2,
            oxidized = ss / sat2,
            overReduced = 1 - sat1 / sat2
        )
    }

    // -------------------------------------------------------------------------
    // ECS (DIRK_ECS)
    //
    // 100 baseline points, then 6 × 220-point subtraces which are summed.
    // A line fitted to points 75..144 of the summed trace is the I0 baseline; the macro fits it
    // against a 1-based time axis, rounds slope and intercept, then evaluates it at the 0-based
    // index. Absorbance is -log10(I / I0) and point 120 is a light-off spike replaced by 119.
    // The decay at points 100..119 (1.5 ms apart) is fitted to b + a·exp(−t/c).
    // -------------------------------------------------------------------------

    private class ExpDecayFit(val a: Double, val b: Double, val c: Double)

    private fun ecsDecayFit(raw: DoubleArray): ExpDecayFit? {
        val begin = 100
        val length = 220
        val subtraces = 6
        if (raw.size < begin + subtraces * length) return null

        val summed = DoubleArray(length) { j ->
            (0 until subtraces).sumOf { raw[begin + it * length + j] }
        }

        val (m, b) = linearRegression(
            DoubleArray(70) { 76.0 + it },
            summed.copyOfRange(75, 145)
        )
        val slope = jsRound(m, 3)
        val intercept = jsRound(b, 2)

        val absorbance = DoubleArray(length) { j ->
            val ratio = summed[j] / (j * slope + intercept)
            if (ratio > 0) -log10(ratio) else Double.NaN
        }
        absorbance[120] = absorbance[119]

        val ys = absorbance.copyOfRange(100, 120)
        if (ys.any { !it.isFinite() }) return null
        val xs = DoubleArray(ys.size) { it * 1.5 }

        return fitExpDecay(xs, ys, doubleArrayOf(1.0, 1.0, 1.0))
            ?: fitExpDecay(xs, ys, doubleArrayOf(ys.first() - ys.last(), ys.last(), 10.0))
    }

    /**
     * Fits y = b + a·exp(−x/c) by Levenberg-Marquardt from [start] = (a, b, c).
     * Returns null if the fit doesn't produce finite parameters.
     */
    private fun fitExpDecay(xs: DoubleArray, ys: DoubleArray, start: DoubleArray): ExpDecayFit? {
        fun sse(params: DoubleArray): Double {
            val (a, b, c) = params
            if (c == 0.0) return Double.POSITIVE_INFINITY
            var sum = 0.0
            for (i in xs.indices) {
                val r = ys[i] - (b + a * exp(-xs[i] / c))
                sum += r * r
            }
            return if (sum.isNaN()) Double.POSITIVE_INFINITY else sum
        }

        var p = start.copyOf()
        var cost = sse(p)
        if (!cost.isFinite()) return null
        var lambda = 1e-3
        var iterations = 0

        while (iterations++ < 200) {
            val jtj = Array(3) { DoubleArray(3) }
            val jtr = DoubleArray(3)
            for (i in xs.indices) {
                val e = exp(-xs[i] / p[2])
                val grad = doubleArrayOf(e, 1.0, p[0] * xs[i] / (p[2] * p[2]) * e)
                val r = ys[i] - (p[1] + p[0] * e)
                for (row in 0..2) {
                    jtr[row] += grad[row] * r
                    for (col in 0..2) jtj[row][col] += grad[row] * grad[col]
                }
            }

            var improved = false
            while (lambda <= 1e12) {
                val damped = Array(3) { row ->
                    DoubleArray(3) { col -> if (row == col) jtj[row][col] * (1 + lambda) else jtj[row][col] }
                }
                val delta = solve3(damped, jtr) ?: break
                val candidate = DoubleArray(3) { p[it] + delta[it] }
                val candidateCost = sse(candidate)
                if (candidateCost < cost) {
                    val converged = cost - candidateCost <= 1e-12 * cost
                    p = candidate
                    cost = candidateCost
                    lambda = maxOf(lambda / 10, 1e-12)
                    improved = !converged
                    break
                }
                lambda *= 10
            }
            if (!improved) break
        }

        return if (p.all { it.isFinite() } && p[2] != 0.0) ExpDecayFit(p[0], p[1], p[2]) else null
    }

    /** Solves a 3×3 linear system by Gaussian elimination with partial pivoting. */
    private fun solve3(a: Array<DoubleArray>, b: DoubleArray): DoubleArray? {
        val m = Array(3) { i -> DoubleArray(4) { j -> if (j < 3) a[i][j] else b[i] } }
        for (col in 0..2) {
            var pivotRow = col
            for (row in col + 1..2) if (abs(m[row][col]) > abs(m[pivotRow][col])) pivotRow = row
            val tmp = m[col]; m[col] = m[pivotRow]; m[pivotRow] = tmp
            val pivot = m[col][col]
            if (pivot == 0.0 || !pivot.isFinite()) return null
            for (j in col..3) m[col][j] /= pivot
            for (row in 0..2) {
                if (row == col) continue
                val f = m[row][col]
                for (j in col..3) m[row][j] -= f * m[col][j]
            }
        }
        return DoubleArray(3) { m[it][3] }
    }

    // -------------------------------------------------------------------------
    // Math helpers
    // -------------------------------------------------------------------------

    /** Least-squares line through (x, y); returns (slope, intercept). */
    private fun linearRegression(x: DoubleArray, y: DoubleArray): Pair<Double, Double> {
        val n = x.size
        val xSum = x.sum()
        val ySum = y.sum()
        var xySum = 0.0
        var xxSum = 0.0
        for (i in 0 until n) {
            xySum += x[i] * y[i]
            xxSum += x[i] * x[i]
        }
        val slope = (n * xySum - xSum * ySum) / (n * xxSum - xSum * xSum)
        return slope to (ySum - slope * xSum) / n
    }

    /** Mean of the values in [from, to). */
    private fun DoubleArray.meanOf(from: Int, to: Int): Double = copyOfRange(from, to).average()

    /** The macro's MathROUND: JavaScript Math.round, which rounds halves toward +∞. */
    private fun jsRound(v: Double, decimals: Int): Double {
        val scale = 10.0.pow(decimals)
        return floor(v * scale + 0.5) / scale
    }

    private fun rounded(v: Double, decimals: Int): String? = plain(jsRound(v, decimals))

    /** Locale-independent decimal string without trailing zeros or exponent, or null if not finite. */
    private fun plain(v: Double?): String? {
        if (v == null || !v.isFinite()) return null
        return BigDecimal.valueOf(v).stripTrailingZeros().toPlainString()
    }
}
