package com.fieldbook.tracker.charts

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import com.fieldbook.tracker.R
import com.github.mikephil.charting.charts.HorizontalBarChart
import com.github.mikephil.charting.components.Description
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.components.YAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.ValueFormatter
import kotlin.math.ceil

object HorizontalBarChartHelper {

    private const val BASE_HEIGHT_DP = 40f // Base height for the chart in density-independent pixels
    private const val HEIGHT_PER_BAR_DP = 40f // Height to add per bar in density-independent pixels

    /**
     * Sets up the horizontal bar chart with the given observations and sorted categories.
     *
     * @param context The context for accessing resources.
     * @param chart An instance of the MPAndroidChart HorizontalBarChart component.
     * @param observations The data to display in the horizontal bar chart.
     * @param parsedCategories The parsed categories to display in the horizontal bar chart.
     * @param chartTextSize The text size for the chart.
     */
    fun setupHorizontalBarChart(context: Context, chart: HorizontalBarChart, observations: List<Any>, parsedCategories: List<String>?, chartTextSize: Float) {
        val categoryCounts = observations.groupingBy { it }.eachCount()

        // Determine sorted categories: use parsedCategories if available and matching, otherwise use default order
        val sortedCategories = if (!parsedCategories.isNullOrEmpty()) {
            val matchingCategories = parsedCategories.filter { categoryCounts.containsKey(it) }.reversed()
            if (matchingCategories.isNotEmpty()) matchingCategories else categoryCounts.keys.map { it.toString() }.sorted()
        } else {
            categoryCounts.keys.map { it.toString() }.sorted()
        }

        val entries = sortedCategories.mapIndexed { index, category ->
            BarEntry(index.toFloat(), categoryCounts[category]?.toFloat() ?: 0f)
        }

        val dataSet = BarDataSet(entries, "Categories").apply {
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
            granularity = 1f
            setLabelCount(sortedCategories.size, false)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return sortedCategories.getOrNull(value.toInt()) ?: ""
                }
            }
        }

        val yAxisLeft: YAxis = chart.axisLeft.apply {
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
            isEnabled = false // Disable left axis for visibility, but keep configuration
        }

        val yAxisRight: YAxis = chart.axisRight.apply {
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
            isEnabled = true
        }

        chart.apply {
            setScaleEnabled(false)
            setDragEnabled(false)
            setHighlightPerTapEnabled(false)
            legend.isEnabled = false

            description = Description().apply {
                text = ""
            }
            setNoDataText(context.getString(R.string.chart_no_data))
            setNoDataTextColor(Color.BLACK)

            // Avoids label cut-off
            setExtraOffsets(0f, 0f, 0f, 16f)

            // Convert base and per-bar height from density-independent pixels to pixels
            val displayMetrics = context.resources.displayMetrics
            val baseHeightInPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, BASE_HEIGHT_DP, displayMetrics)
            val heightPerBarInPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, HEIGHT_PER_BAR_DP, displayMetrics)

            // Dynamic chart height based on the number of bars
            val totalHeight = baseHeightInPx + (sortedCategories.size * heightPerBarInPx)
            layoutParams.height = totalHeight.toInt()

            invalidate()
            requestLayout()
        }
    }
}
