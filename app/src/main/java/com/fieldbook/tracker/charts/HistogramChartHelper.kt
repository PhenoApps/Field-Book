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
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.ceil

object HistogramChartHelper {

    private const val TAG = "HistogramChartHelper"

    /**
     * Sets up the histogram chart with the given observations.
     *
     * @param context The context for accessing resources.
     * @param chart An instance of the MPAndroidChart BarChart component.
     * @param observations The data to display in the histogram, represented as a list of BigDecimal values.
     * @param chartTextSize The text size for the chart.
     */
    fun setupHistogram(context: Context, chart: BarChart, observations: List<BigDecimal>, chartTextSize: Float) {

        val minValue = observations.minOrNull() ?: BigDecimal.ZERO
        val maxValue = observations.maxOrNull() ?: BigDecimal.ZERO
        val range = maxValue.subtract(minValue)

        chart.visibility = View.VISIBLE

        val distinctValuesCount = observations.distinct().size
        val binCount = minOf(getMaxVerticalBars(context, 10), distinctValuesCount)

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

        val entries = sortedBinnedObservations.map { (binIndex, count) ->
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

        val xAxis: XAxis = chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setDrawAxisLine(true)
            textColor = Color.BLACK
            textSize = chartTextSize
            axisMinimum = 0f
            axisMaximum = maxBinIndex.toFloat()
            setCenterAxisLabels(true)
            setLabelCount(labels.size, !(isBinSizeOne && binCount > 1))

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val binIndex = value.toInt()
                    return if (binIndex in labels.indices) labels[binIndex] else ""
                }
            }
        }

        val yAxis: YAxis = chart.axisLeft.apply {
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
            setHighlightPerTapEnabled(false)
            legend.isEnabled = false

            description = Description().apply {
                text = ""
            }
            setNoDataText(context.getString(R.string.field_trait_chart_no_data))
            setNoDataTextColor(Color.BLACK)

            // Add extra offsets to avoid label cut-off
            setExtraOffsets(8f, 0f, 0f, 8f)

            notifyDataSetChanged()
            invalidate()
        }
    }

    private fun getMaxVerticalBars(context: Context, referenceMaxBars: Int): Int {
        val displayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val referenceScreenWidth = 1080  // Default smartphone screen width in pixels
        val maxBarsByScreen = (referenceMaxBars * screenWidth) / referenceScreenWidth.coerceAtLeast(1)
        val finalMaxBars = maxBarsByScreen.coerceAtMost(referenceMaxBars)

        return finalMaxBars
    }
}
