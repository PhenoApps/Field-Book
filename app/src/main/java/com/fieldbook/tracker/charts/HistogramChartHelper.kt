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
import kotlin.math.ceil

object HistogramChartHelper {

    fun setupHistogram(context: Context, chart: BarChart, observations: List<BigDecimal>) {
        val minValue = observations.minOrNull() ?: BigDecimal.ZERO
        val maxValue = observations.maxOrNull() ?: BigDecimal.ZERO
        val range = maxValue.subtract(minValue)

//        if (range.compareTo(BigDecimal.ZERO) == 0) {
//            chart.visibility = View.GONE
//            return
//        }

        chart.visibility = View.VISIBLE

        val distinctValuesCount = observations.distinct().size
        val binCount = minOf(10, distinctValuesCount)
        val binSize = range.divide(BigDecimal(binCount), BigDecimal.ROUND_UP)

        val binnedObservations = mutableMapOf<Int, Int>()
        for (observation in observations) {
            val binIndex = observation.subtract(minValue).divide(binSize, BigDecimal.ROUND_DOWN).toInt()
            binnedObservations[binIndex] = (binnedObservations[binIndex] ?: 0) + 1
        }

        val entries = binnedObservations.map { (binIndex, count) ->
            val binStart = minValue.add(binSize.multiply(BigDecimal(binIndex)))
            val binCenter = binStart.add(binSize.divide(BigDecimal(2)))
            BarEntry(binCenter.toFloat(), count.toFloat())
        }

        val dataSet = BarDataSet(entries, "Observations")
        val theme = context.theme
        val fbColorPrimaryValue = TypedValue()
        theme.resolveAttribute(R.attr.fb_color_primary, fbColorPrimaryValue, true)

        dataSet.color = fbColorPrimaryValue.data
        dataSet.valueTextColor = Color.WHITE
        dataSet.setDrawValues(false)
        dataSet.barBorderColor = Color.BLACK
        dataSet.barBorderWidth = 1f

        val barData = BarData(dataSet)
        barData.barWidth = binSize.toFloat()
        chart.data = barData

        val xAxis: XAxis = chart.xAxis
        xAxis.position = XAxis.XAxisPosition.BOTTOM
        xAxis.setDrawGridLines(false)
        xAxis.setDrawAxisLine(false)
        xAxis.textColor = Color.BLACK
        xAxis.axisMinimum = minValue.toFloat()
        xAxis.axisMaximum = maxValue.toFloat() + binSize.toFloat()
        xAxis.setCenterAxisLabels(observations.any { it.remainder(binSize).compareTo(BigDecimal.ZERO) != 0 })
        xAxis.setLabelCount(binCount + 1, true)

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
