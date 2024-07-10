package com.fieldbook.tracker.charts

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.util.DisplayMetrics
import android.util.Log
import android.util.TypedValue
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
import kotlin.math.ceil

object BarChartHelper {

    private data class ChartConfig(val maxCharacters: Int, val screenWidth: Int, val textSize: Float)

    private fun getChartConfig(context: Context): ChartConfig {
        val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val textSizeValue = preferences.getString(GeneralKeys.TEXT_THEME, "2")?.toInt() ?: 2

        val maxCharacters = when (textSizeValue) {
            1 -> 18
            2 -> 24
            3 -> 32
            4 -> 40
            else -> 24
        }

        val textSize = when (textSizeValue) {
            1 -> 12f
            2 -> 14f
            3 -> 16f
            4 -> 18f
            else -> 14f
        }

        val displayMetrics: DisplayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels

        return ChartConfig(maxCharacters, screenWidth, textSize)
    }

    private fun getMaxBars(context: Context, labels: List<String>, referenceMaxBars: Int): Int {
        val (maxCharacters, screenWidth, _) = getChartConfig(context)
        val referenceScreenWidth = 1080  // Default smartphone screen width in pixels

        val maxBarsByScreen = (referenceMaxBars * screenWidth) / referenceScreenWidth.coerceAtLeast(1)
        val labelLengthFactor = maxCharacters / (labels.maxOfOrNull { it.length }?.coerceAtLeast(1) ?: 1)

        return maxBarsByScreen * labelLengthFactor
    }

    private fun getLabelRotation(context: Context, labels: List<String>, numberOfBars: Int): Float {
        val (maxCharacters, screenWidth, _) = getChartConfig(context)
        val barWidth = screenWidth / numberOfBars

        // Arbitrary threshold for when to rotate labels, can be adjusted as needed
        val barWidthThreshold = 150 // pixels

        val shouldRotate = labels.any { it.length > maxCharacters } || barWidth < barWidthThreshold

        // Add logging
        Log.d("BarChartHelper", "Checking label rotation:")
        Log.d("BarChartHelper", "maxCharacters: $maxCharacters")
        Log.d("BarChartHelper", "screenWidth: $screenWidth")
        Log.d("BarChartHelper", "barWidth: $barWidth")
        Log.d("BarChartHelper", "numberOfBars: $numberOfBars")
        Log.d("BarChartHelper", "barWidthThreshold: $barWidthThreshold")
        Log.d("BarChartHelper", "shouldRotate: $shouldRotate")
        Log.d("BarChartHelper", "Labels: $labels")

        return if (shouldRotate) 45f else 0f
    }

    fun setupBarChart(context: Context, chart: BarChart, observations: List<Any>): Boolean {
        val categoryCounts = observations.groupingBy { it }.eachCount()
        val sortedCategories = categoryCounts.keys.map { it.toString() }.sorted()
        val maxBars = getMaxBars(context, sortedCategories, 8)
        if (sortedCategories.size > maxBars) {
            return false
        }

        val entries = sortedCategories.mapIndexed { index, category ->
            BarEntry(index.toFloat(), categoryCounts[category]?.toFloat() ?: 0f)
        }

        val dataSet = BarDataSet(entries, "Categories")
        val theme = context.theme
        val fbColorPrimaryValue = TypedValue()
        theme.resolveAttribute(R.attr.fb_color_primary, fbColorPrimaryValue, true)

        dataSet.color = fbColorPrimaryValue.data
        dataSet.valueTextColor = Color.WHITE
        dataSet.setDrawValues(false)
        dataSet.barBorderColor = Color.BLACK
        dataSet.barBorderWidth = 1f

        val barData = BarData(dataSet)
        barData.barWidth = 0.9f
        chart.data = barData

        val (maxCharacters, screenWidth, textSize) = getChartConfig(context)

        val xAxis: XAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(false)
        xAxis.textColor = Color.BLACK
        xAxis.textSize = textSize
        xAxis.granularity = 1f
        xAxis.setLabelCount(sortedCategories.size, false)
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return sortedCategories.getOrNull(value.toInt()) ?: ""
            }
        }

        chart.setFitBars(true)
        xAxis.setAvoidFirstLastClipping(true)
        xAxis.labelRotationAngle = getLabelRotation(context, sortedCategories, sortedCategories.size)

        val leftAxis: YAxis = chart.axisLeft
        leftAxis.setDrawGridLines(false)
        leftAxis.setDrawAxisLine(false)
        leftAxis.textColor = Color.BLACK
        leftAxis.textSize = textSize
        leftAxis.axisMinimum = 0f
        val maxY = entries.maxOfOrNull { it.y.toInt() } ?: 1
        leftAxis.granularity = ceil(maxY / 6f)
        leftAxis.axisMaximum = ceil(maxY / leftAxis.granularity) * leftAxis.granularity
        leftAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return value.toInt().toString()
            }
        }

        chart.axisRight.isEnabled = false
        chart.setScaleEnabled(false)
        chart.setDragEnabled(false)
        chart.setHighlightPerTapEnabled(false)
        chart.legend.isEnabled = false

        val description = Description()
        description.text = ""
        chart.description = description
        chart.setNoDataText(context.getString(R.string.field_trait_chart_no_data))
        chart.setNoDataTextColor(Color.BLACK)

        // Add extra offsets to avoid label cut-off
        chart.setExtraOffsets(0f, 0f, 0f, 16f)

        chart.invalidate()

        return true
    }
}
