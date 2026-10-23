package com.fieldbook.tracker.utilities

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Flags numeric observations that fall outside a 99.9% prediction interval built from the other
 * observations of the same trait in the field.
 *
 * The interval is centred on the median and scaled by the MAD, so extreme values already collected
 * can't drag it toward themselves. Its width uses the Student t quantile for the number of
 * reference values, so it starts wide (only gross entry errors are flagged) and narrows as more
 * data is collected. Because the centre and scale are robust estimates, the 99.9% coverage is
 * approximate and assumes roughly normal data.
 */
object OutlierDetector {

    /** Fewer reference values than this can't give a usable spread estimate. */
    const val MIN_REFERENCE_VALUES = 5

    // factors that turn each spread estimate into a normal standard deviation
    private const val MAD_TO_SIGMA = 1.4826
    private const val IQR_TO_SIGMA = 1.349
    private const val MEAN_AD_TO_SIGMA = 1.2533

    /** Two-sided 99.9% Student t quantiles (t 0.9995), keyed by degrees of freedom. */
    private val T_9995 = sortedMapOf(
        4 to 8.610, 5 to 6.869, 6 to 5.959, 7 to 5.408, 8 to 5.041,
        9 to 4.781, 10 to 4.587, 11 to 4.437, 12 to 4.318, 13 to 4.221, 14 to 4.140,
        15 to 4.073, 16 to 4.015, 17 to 3.965, 18 to 3.922, 19 to 3.883, 20 to 3.850,
        21 to 3.819, 22 to 3.792, 23 to 3.768, 24 to 3.745, 25 to 3.725, 26 to 3.707,
        27 to 3.690, 28 to 3.674, 29 to 3.659, 30 to 3.646, 40 to 3.551, 60 to 3.460,
        120 to 3.373
    )

    /** The normal quantile the t quantiles approach as degrees of freedom grow. */
    private const val Z_9995 = 3.291

    fun isOutlier(value: Double, reference: List<Double>): Boolean {

        val n = reference.size

        if (n < MIN_REFERENCE_VALUES) return false

        val sorted = reference.sorted()
        val center = quantile(sorted, 0.5)
        val scale = robustScale(sorted, center)

        // every reference value is the same, so there is no spread to compare against
        if (scale == 0.0) return false

        val limit = tQuantile(n - 1) * scale * sqrt(1.0 + 1.0 / n)

        return abs(value - center) > limit
    }

    /**
     * MAD is the most robust spread estimate, but it is zero whenever more than half the values are
     * identical, which is common for low-variance integer traits. Fall back to the IQR and then to
     * the mean absolute deviation before giving up.
     */
    private fun robustScale(sorted: List<Double>, center: Double): Double {

        val mad = quantile(sorted.map { abs(it - center) }.sorted(), 0.5)
        if (mad > 0.0) return mad * MAD_TO_SIGMA

        val iqr = quantile(sorted, 0.75) - quantile(sorted, 0.25)
        if (iqr > 0.0) return iqr / IQR_TO_SIGMA

        val mean = sorted.average()
        return sorted.map { abs(it - mean) }.average() * MEAN_AD_TO_SIGMA
    }

    /**
     * Linear interpolation between order statistics, R's default (type 7).
     */
    private fun quantile(sorted: List<Double>, p: Double): Double {

        val h = (sorted.size - 1) * p
        val lower = h.toInt()
        val upper = minOf(lower + 1, sorted.size - 1)

        return sorted[lower] + (h - lower) * (sorted[upper] - sorted[lower])
    }

    /**
     * Exact table value where one exists, otherwise interpolated linearly in 1/df, which is close to
     * linear for t quantiles. Past the last table entry it interpolates toward the normal quantile.
     */
    internal fun tQuantile(df: Int): Double {

        val degrees = df.coerceAtLeast(T_9995.firstKey())

        T_9995[degrees]?.let { return it }

        val lower = T_9995.headMap(degrees).lastKey()
        val upper = T_9995.tailMap(degrees).keys.firstOrNull()

        val lowerInverse = 1.0 / lower
        val lowerT = T_9995.getValue(lower)
        val upperInverse = if (upper == null) 0.0 else 1.0 / upper
        val upperT = if (upper == null) Z_9995 else T_9995.getValue(upper)

        val fraction = (1.0 / degrees - upperInverse) / (lowerInverse - upperInverse)

        return upperT + fraction * (lowerT - upperT)
    }
}
