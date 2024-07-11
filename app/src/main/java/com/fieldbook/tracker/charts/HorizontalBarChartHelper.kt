package com.fieldbook.tracker.charts

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import com.fieldbook.tracker.R
import com.fieldbook.tracker.utilities.ChartUtil
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

    /**
     * Sets up the horizontal bar chart with the given observations and sorted categories.
     *
     * @param context The context for accessing resources.
     * @param chart The HorizontalBarChart to set up.
     * @param observations The data to display in the horizontal bar chart.
     * @param parsedCategories The parsed categories to display in the horizontal bar chart.
     */
    fun setupHorizontalBarChart(context: Context, chart: HorizontalBarChart, observations: List<Any>, parsedCategories: List<String>?) {
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
            barWidth = 0.9f
        }
        chart.data = barData

        val chartConfig = ChartUtil.getChartConfig(context)

        val xAxis: XAxis = chart.xAxis.apply {
            position = XAxis.XAxisPosition.BOTTOM
            setDrawGridLines(false)
            setDrawAxisLine(false)
            textColor = Color.BLACK
            textSize = chartConfig.textSize
            granularity = 1f
            setLabelCount(sortedCategories.size, false)
            valueFormatter = object : ValueFormatter() {
                override fun getFormattedValue(value: Float): String {
                    return sortedCategories.getOrNull(value.toInt()) ?: ""
                }
            }
        }

        val yAxis: YAxis = chart.axisRight.apply {
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
            axisLeft.isEnabled = false
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
            setExtraOffsets(0f, 0f, 0f, 16f)

            invalidate()
        }

        // Calculate and set the dynamic height based on the number of bars
        val baseHeightPerBar = 100 // You can adjust this value as needed
        val totalHeight = sortedCategories.size * baseHeightPerBar
        val layoutParams = chart.layoutParams
        layoutParams.height = totalHeight
        chart.layoutParams = layoutParams
    }
}
