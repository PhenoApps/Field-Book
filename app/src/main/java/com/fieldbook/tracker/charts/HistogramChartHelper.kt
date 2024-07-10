package com.fieldbook.tracker.charts

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
import android.view.View
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys
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

    private data class ChartConfig(val textSize: Float)

    private fun getChartConfig(context: Context): ChartConfig {
        val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val textSizeValue = preferences.getString(GeneralKeys.TEXT_THEME, "2")?.toInt() ?: 2

        val textSize = when (textSizeValue) {
            1 -> 12f
            2 -> 14f
            3 -> 16f
            4 -> 18f
            else -> 14f
        }

        return ChartConfig(textSize)
    }

    private fun getMaxBins(context: Context, referenceMaxBins: Int): Int {
        val displayMetrics: DisplayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val referenceScreenWidth = 1080  // Default smartphone screen width in pixels

        return (referenceMaxBins * screenWidth) / referenceScreenWidth.coerceAtLeast(1)
    }

    fun setupHistogram(context: Context, chart: BarChart, observations: List<BigDecimal>) {
        Log.d(TAG, "Observations: $observations")

        val minValue = observations.minOrNull() ?: BigDecimal.ZERO
        val maxValue = observations.maxOrNull() ?: BigDecimal.ZERO
        val range = maxValue.subtract(minValue)

        Log.d(TAG, "Min Value: $minValue, Max Value: $maxValue, Range: $range")

        chart.visibility = View.VISIBLE

        val distinctValuesCount = observations.distinct().size
        val binCount = minOf(getMaxBins(context, 10), distinctValuesCount)
        val binSize = range.divide(BigDecimal(binCount), 0, RoundingMode.UP)

        Log.d(TAG, "Distinct Values Count: $distinctValuesCount, Bin Count: $binCount, Bin Size: $binSize")

        val binnedObservations = mutableMapOf<Int, Int>()
        val offset = if (binSize.compareTo(BigDecimal.ONE) == 0) 0 else 1
        for (observation in observations) {
            val binIndex = offset + observation.subtract(minValue).divide(binSize, 0, RoundingMode.DOWN).toInt()
            binnedObservations[binIndex] = (binnedObservations[binIndex] ?: 0) + 1
        }

        val sortedBinnedObservations = (0 until binCount).associateWith { binnedObservations[it] ?: 0 }

        Log.d(TAG, "Sorted Binned Observations: $sortedBinnedObservations")
        
        val labels = sortedBinnedObservations.keys.map { binIndex ->
            val binStart = minValue.add(binSize.multiply(BigDecimal(binIndex)))
            binStart.toInt().toString()
        }

        val entries = sortedBinnedObservations.map { (binIndex, count) ->
            BarEntry(binIndex.toFloat(), count.toFloat())
        }

        Log.d(TAG, "Generated Labels: $labels")
        Log.d(TAG, "Entries: $entries")

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

        val chartConfig = getChartConfig(context)

        val xAxis: XAxis = chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setDrawAxisLine(true)
            textColor = Color.BLACK
            textSize = chartConfig.textSize

            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    val binIndex = value.toInt()
                    return if (binIndex in labels.indices) labels[binIndex] else ""
                }
            }
        }

        xAxis.axisMinimum = 0f
        xAxis.axisMaximum = labels.size.toFloat()

        if (binSize.compareTo(BigDecimal.ONE) == 0) {
            Log.d(TAG, "binSize is ONE")
            xAxis.axisMinimum = -1f
            xAxis.setLabelCount(binCount, )
        } else {
            Log.d(TAG, "binSize is NOT ONE")
            xAxis.setLabelCount(binCount + 1, true)
            xAxis.setCenterAxisLabels(true)
        }

        val leftAxis: YAxis = chart.axisLeft.apply {
            setDrawGridLines(false)
            setDrawAxisLine(false)
            textColor = Color.BLACK
            textSize = chartConfig.textSize
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

            Log.d(TAG, "Chart Data: ${data}")

            notifyDataSetChanged()
            invalidate()
        }

        Log.d(TAG, "mEntries are " + xAxis.mEntries.joinToString(", "))
    }
}










