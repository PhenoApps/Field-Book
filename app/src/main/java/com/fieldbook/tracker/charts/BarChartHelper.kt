package com.fieldbook.tracker.charts

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import com.fieldbook.tracker.R
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

    fun setupBarChart(context: Context, chart: BarChart, observations: List<Any>) {
        val categoryCounts = observations.groupingBy { it }.eachCount()

        val entries = categoryCounts.entries.mapIndexed { index, entry ->
            BarEntry(index.toFloat(), entry.value.toFloat())
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
        chart.data = barData

        val xAxis: XAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(false)
        xAxis.textColor = Color.BLACK
        xAxis.granularity = 1f
        xAxis.valueFormatter = object : ValueFormatter() {
            override fun getFormattedValue(value: Float): String {
                return categoryCounts.keys.elementAt(value.toInt()).toString()
            }
        }

        val leftAxis: YAxis = chart.axisLeft
        leftAxis.setDrawGridLines(false)
        leftAxis.setDrawAxisLine(false)
        leftAxis.textColor = Color.BLACK
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
        chart.setNoDataText("No data available")
        chart.setNoDataTextColor(Color.BLACK)

        chart.invalidate()
    }
}
