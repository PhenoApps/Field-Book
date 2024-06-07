package com.fieldbook.tracker.charts

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import com.fieldbook.tracker.R
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry

object PieChartHelper {

    fun setupPieChart(context: Context, chart: PieChart, completeness: Float) {
        val entries = listOf(
            PieEntry(completeness, ""),
            PieEntry(1 - completeness, "")
        )

        val dataSet = PieDataSet(entries, "")
        val theme = context.theme
        val fbColorPrimaryValue = TypedValue()
        val fbTraitButtonBackgroundTintValue = TypedValue()
        theme.resolveAttribute(R.attr.fb_color_primary, fbColorPrimaryValue, true)
        theme.resolveAttribute(R.attr.fb_trait_button_background_tint, fbTraitButtonBackgroundTintValue, true)

        dataSet.colors = listOf(fbColorPrimaryValue.data, fbTraitButtonBackgroundTintValue.data)
        dataSet.setDrawValues(false)

        val data = PieData(dataSet)
        chart.data = data
        chart.description.isEnabled = false
        chart.isRotationEnabled = false
        chart.setDrawEntryLabels(false)
        chart.legend.isEnabled = false

        chart.setCenterText("${(completeness * 100).toInt()}%")
        chart.setCenterTextSize(12f)
        chart.setCenterTextColor(Color.BLACK)

        chart.holeRadius = 85f
        chart.setTransparentCircleAlpha(0)
        chart.setHoleColor(Color.TRANSPARENT)
        chart.invalidate()
    }
}