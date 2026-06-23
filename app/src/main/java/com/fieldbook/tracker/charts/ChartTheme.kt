package com.fieldbook.tracker.charts

import android.content.Context
import android.graphics.Color
import com.fieldbook.tracker.R
import com.google.android.material.color.MaterialColors

object ChartTheme {

    fun graphTextColor(context: Context): Int =
        MaterialColors.getColor(context, R.attr.fb_graph_item_text_color, Color.BLACK)
}
