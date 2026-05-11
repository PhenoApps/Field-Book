package com.fieldbook.tracker.charts

import android.content.Context
import android.graphics.Color
import android.util.TypedValue
import android.util.Log
import com.fieldbook.tracker.R
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry

object PieChartHelper {

    private const val DEFAULT_BASE_HEIGHT_DP = 50f // Default base height in density-independent pixels

    /**
     * Sets up the pie chart with completeness data.
     *
     * @param context The context for accessing resources.
     * @param chart An instance of the MPAndroidChart PieChart component.
     * @param completeness The completeness value to display in the pie chart, represented as a float between 0 and 1.
     * @param chartTextSize The text size to be used for the center text of the chart.
     */
    fun setupPieChart(context: Context, chart: PieChart, completeness: Float, chartTextSize: Float) {
        val entries = listOf(
            PieEntry(completeness, ""),
            PieEntry(1 - completeness, "")
        )

        val dataSet = PieDataSet(entries, "").apply {
            val theme = context.theme
            val fbColorPrimaryValue = TypedValue()
            val fbTraitButtonBackgroundTintValue = TypedValue()
            theme.resolveAttribute(R.attr.fb_color_primary, fbColorPrimaryValue, true)
            theme.resolveAttribute(R.attr.fb_trait_button_background_tint, fbTraitButtonBackgroundTintValue, true)

            colors = listOf(fbColorPrimaryValue.data, fbTraitButtonBackgroundTintValue.data)
            setDrawValues(false)
        }

        val data = PieData(dataSet)
        chart.data = data
        chart.description.isEnabled = false
        chart.isRotationEnabled = false
        chart.setDrawEntryLabels(false)
        chart.legend.isEnabled = false
        chart.setTouchEnabled(false)

        // Set center text with the appropriate size
        chart.setCenterText("${(completeness * 100).toInt()}%")
        chart.setCenterTextSize(chartTextSize)
        chart.setCenterTextColor(Color.BLACK)

        // Determine chart height based on base height and text size
        val displayMetrics = context.resources.displayMetrics
        val baseHeightInPx = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, DEFAULT_BASE_HEIGHT_DP, displayMetrics)
        val newHeight = baseHeightInPx + chartTextSize * displayMetrics.scaledDensity
        val layoutParams = chart.layoutParams
        layoutParams.height = newHeight.toInt()
        chart.layoutParams = layoutParams

        chart.holeRadius = 85f
        chart.setTransparentCircleAlpha(0)
        chart.setHoleColor(Color.TRANSPARENT)
        chart.invalidate()
    }
}
