package com.fieldbook.tracker.ui.grid

import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.fieldbook.tracker.R

@Immutable
data class GridColors(
    @ColorInt val text: Int,
    @ColorInt val cellBg: Int,
    @ColorInt val highlight: Int,
    @ColorInt val border: Int
) {
    val textColor get() = Color(text)
    val cellBgColor get() = Color(cellBg)
    val highlightColor get() = Color(highlight)
    val borderColor get() = Color(border)
}

@Composable
fun rememberGridColors(): GridColors {
    val ctx = LocalContext.current
    return remember(ctx) {
        val tv = TypedValue()
        val th = ctx.theme

        fun resolve(@AttrRes id: Int): Int {
            th.resolveAttribute(id, tv, true)
            return tv.data
        }

        GridColors(
            text = resolve(R.attr.cellTextColor),
            cellBg = resolve(R.attr.emptyCellColor),
            highlight = resolve(R.attr.fb_color_primary),
            border = resolve(R.attr.tableBorderColor)
        )
    }
}
