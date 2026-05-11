package com.fieldbook.tracker.ui.utils

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Measures the maximum width required to display a list of text strings without truncation.
 *
 * [padding] can be optionally specified for any extra horizontal
 * spaces to be considered while measuring the text width
 */
@Composable
fun getMaxTextWidth(
    texts: List<String>,
    textStyle: TextStyle,
    padding: Dp = 0.dp,
): Dp {
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current

    return texts.maxOfOrNull { text ->
        val measured = textMeasurer.measure(
            text = text,
            style = textStyle,
            constraints = Constraints()
        )
        with(density) { measured.size.width.toDp() }
    }?.plus(padding) ?: 0.dp
}