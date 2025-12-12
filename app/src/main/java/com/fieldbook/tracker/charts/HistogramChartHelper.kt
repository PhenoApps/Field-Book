package com.fieldbook.tracker.charts

import android.content.Context
import android.graphics.Color
import android.util.Log
import android.util.TypedValue
import android.view.View
import com.fieldbook.tracker.R
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil
import kotlin.math.pow

object HistogramChartHelper {

    data class HistogramData(
        val binLabels: List<String>,
        val binCounts: List<Int>,
        val isBinSizeOne: Boolean,
        val maxCount: Int
    )

    private const val TAG = "HistogramChartHelper"
    private const val MIN_BAR_WIDTH_DP = 40f // Minimum bar width in in density-independent pixels
    private const val SCREEN_MARGIN_FRACTION = 0.3f // Fraction of screen width not available for bars

    /**
     * Sets up the histogram chart with the given observations.
     *
     * @param context The context for accessing resources.
     * @param chart An instance of the MPAndroidChart BarChart component.
     * @param observations The data to display in the histogram, represented as a list of BigDecimal values.
     */
    fun setupHistogram(context: Context, chart: BarChart, observations: List<BigDecimal>, chartTextSize: Float) {

        chart.visibility = View.VISIBLE

        val histogramData = computeHistogramData(context, observations)

        val labels = histogramData.binLabels
        val counts = histogramData.binCounts
        val isBinSizeOne = histogramData.isBinSizeOne
        val maxBinIndex = counts.size - 1

        val entries = counts.mapIndexed { binIndex, count ->
            val adjustedBinIndex = if (isBinSizeOne) binIndex.toFloat() - 0.5f else binIndex.toFloat()
            BarEntry(adjustedBinIndex, count.toFloat())
        }

        val dataSet = BarDataSet(entries, null).apply {
            val theme = context.theme
            val fbColorPrimaryValue = TypedValue()
            theme.resolveAttribute(R.attr.fb_color_primary, fbColorPrimaryValue, true)

            color = fbColorPrimaryValue.data
            valueTextColor = Color.WHITE
            setDrawValues(false)
            barBorderColor = Color.BLACK
            barBorderWidth = 1f
        }

        val barData = BarData(dataSet).apply {
            barWidth = 1f // Ensure bar width is 1 to match the index spacing
        }
        chart.data = barData

        chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setDrawAxisLine(true)
            textColor = Color.BLACK
            textSize = chartTextSize
            axisMinimum = 0f
            axisMaximum = maxBinIndex.toFloat()
            setCenterAxisLabels(true)
            setLabelCount(labels.size, !(isBinSizeOne && counts.size > 1))

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val binIndex = value.toInt()
                    return if (binIndex in labels.indices) labels[binIndex] else ""
                }
            }
        }

        chart.axisLeft.apply {
            setDrawGridLines(false)
            setDrawAxisLine(true)
            textColor = Color.BLACK
            textSize = chartTextSize
            axisMinimum = 0f
            val maxY = entries.maxOfOrNull { it.y.toInt() } ?: 1
            granularity = ceil(maxY / 6f)
            axisMaximum = ceil(maxY / granularity) * granularity

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return value.toInt().toString()
                }
            }
        }

        chart.apply {
            axisRight.isEnabled = false
            setScaleEnabled(false)
            setDragEnabled(false)
            isHighlightPerTapEnabled = false
            legend.isEnabled = false

            description = Description().apply {
                text = ""
            }
            setNoDataText(context.getString(R.string.chart_no_data))
            setNoDataTextColor(Color.BLACK)

            // Add extra offsets to avoid label cut-off
            setExtraOffsets(8f, 0f, 0f, 8f)

            notifyDataSetChanged()
            invalidate()
        }
    }

    fun computeHistogramData(context: Context, observations: List<BigDecimal>): HistogramData {
        // Calculate min, max, and range of observations
        val minValue = observations.minOrNull() ?: BigDecimal.ZERO
        val maxValue = observations.maxOrNull() ?: BigDecimal.ZERO
        val range = maxValue.subtract(minValue)

        val binCount = getBinCount(context, observations, range)

        // Ensure binSize is non-zero by checking range
        val binSize = if (range > BigDecimal.ZERO) {
            range.divide(BigDecimal(binCount), 0, RoundingMode.UP)
        } else {
            BigDecimal.ONE
        }

        val isBinSizeOne = binSize.compareTo(BigDecimal.ONE) == 0

        val binnedObservations = mutableMapOf<Int, Int>()

        for (observation in observations) {
            val binIndex = 1 + observation.subtract(minValue).divide(binSize, 0, RoundingMode.DOWN).toInt()
            binnedObservations[binIndex] = (binnedObservations[binIndex] ?: 0) + 1
        }

        if (!isBinSizeOne) {
            binnedObservations[binnedObservations.keys.maxOrNull()!! + 1] = 0
        }

        val maxBinIndex = binnedObservations.keys.maxOrNull() ?: binCount
        val sortedBinnedObservations = (0..maxBinIndex).associateWith { binnedObservations[it] ?: 0 }

        val labels = sortedBinnedObservations.keys.map { binIndex ->
            val binStart = minValue.add(binSize.multiply(BigDecimal(binIndex)))
            binStart.toInt().toString()
        }.let {
            if (isBinSizeOne) it.dropLast(1) else it
        }

        return HistogramData(
            binLabels = labels,
            binCounts = sortedBinnedObservations.values.toList(),
            isBinSizeOne = isBinSizeOne,
            maxCount = sortedBinnedObservations.values.maxOrNull() ?: 1
        )
    }

    /**
     * Calculates the appropriate bin count for the histogram using both the screen width and data distribution.
     *
     * @param context The context for accessing resources.
     * @param observations The list of observations to be displayed in the histogram, represented as a list of BigDecimal values.
     * @param range The range of the observations.
     * @return The calculated bin count.
     */
    private fun getBinCount(context: Context, observations: List<BigDecimal>, range: BigDecimal): Int {
        // Calculate maxBinCount based on screen width
        val displayMetrics = context.resources.displayMetrics
        val minBarWidthPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, MIN_BAR_WIDTH_DP, displayMetrics)
        val availableScreenWidth = displayMetrics.widthPixels * (1 - SCREEN_MARGIN_FRACTION)
        val maxBinCount = (availableScreenWidth / minBarWidthPx).toInt()

        // Calculate idealBinCount using modified Freedman-Diaconis rule
        val sortedObservations = observations.sorted()
        val q1Index = (sortedObservations.size * 0.25).toInt()
        val q3Index = (sortedObservations.size * 0.75).toInt()
        val q1 = sortedObservations[q1Index]
        val q3 = sortedObservations[q3Index]
        val iqr = q3.subtract(q1)

        val binWidthFD = if (iqr > BigDecimal.ZERO) {
            (2 * iqr.toDouble() / observations.size.toDouble().pow(1.0 / 2.0)).toBigDecimal()
            // Using a square root here rather than cube root. This avoids producing too few bins
            // when n is low, while the maxBinCount limit prevents too many bins when n is high.
        } else {
            BigDecimal.ONE
        }

        val idealBinCount = if (binWidthFD > BigDecimal.ZERO) {
            (range / binWidthFD).toInt()
        } else {
            maxBinCount
        }

        // Log the calculated max and ideal bin counts
        Log.d(TAG, "Max bin count: $maxBinCount, Ideal bin count: $idealBinCount, Final bin count: ${minOf(maxBinCount, idealBinCount)}")

        // Use the lesser of maxBinCount and idealBinCount
        return maxOf(1, minOf(maxBinCount, idealBinCount)) // at least one bin
    }

}
