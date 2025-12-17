package com.fieldbook.tracker.utilities

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

class LazyTableTextHeightCalc {
    companion object {
        /**
         * Calculates the rowSize for a lazyTable row
         */
        fun calculateTextHeight(
            textBlocks: List<String>, // each block will be placed one below the other
            fontSize: Float,
            availableWidth: Float,
            horizontalPadding: Float = 8f, // for left + right
            verticalPadding: Float = 8f, // for top + bottom
            minHeightInDp: Float = 60f,
            lineHeightMultiplier: Float = 1.4f, // for spacing between lines
            charWidthMultiplier: Float = 0.6f, // average width for a character (60% of font size)
            verticalSpacing: Float = 2f, // extra spacing between textBlocks
        ): Dp {
            // vertical space each line occupies
            val lineHeight = fontSize * lineHeightMultiplier

            // actual available space for text
            val textWidth = availableWidth - horizontalPadding

            if (textWidth <= 0) return minHeightInDp.dp

            val charWidth = fontSize * charWidthMultiplier
            val charsPerLine = (textWidth / charWidth).toInt().coerceAtLeast(1)

            // calculate total lines across all text blocks
            val totalLines = textBlocks.sumOf { text ->
                if (text.isEmpty()) 1
                else { // wrapped lines
                    ceil(text.length.toDouble() / charsPerLine).toInt()
                }
            }

            // spacing between text blocks. for n blocks, we will have n-1 spacings
            val totalVerticalSpacing = if (textBlocks.size > 1) {
                verticalSpacing * (textBlocks.size - 1)
            } else 0f

            // content + padding + spacing
            val calculatedHeight = (totalLines * lineHeight) + verticalPadding + totalVerticalSpacing

            return calculatedHeight.coerceAtLeast(minHeightInDp).dp
        }
    }
}