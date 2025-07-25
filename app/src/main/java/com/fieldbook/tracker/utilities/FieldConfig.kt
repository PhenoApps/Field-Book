package com.fieldbook.tracker.utilities

import androidx.compose.ui.Alignment

data class FieldConfig(
    val rows: Int,
    val cols: Int,
    val pattern: FieldPattern = FieldPattern.HORIZONTAL_LINEAR,
    val startCorner: FieldStartCorner = FieldStartCorner.TOP_LEFT,
    val cellTextColor: Int,
    val cellBgColor: Int,
    val headerCellBgColor: Int,
    val showHeaders: Boolean = false
)

enum class FieldStartCorner {
    TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

    companion object {
        fun fromPosition(row: Int, col: Int, rows: Int, cols: Int): FieldStartCorner? {
            return when {
                row == 0 && col == 0 -> TOP_LEFT
                row == 0 && col == cols - 1 -> TOP_RIGHT
                row == rows - 1 && col == 0 -> BOTTOM_LEFT
                row == rows - 1 && col == cols - 1 -> BOTTOM_RIGHT
                else -> null
            }
        }

        val FieldStartCorner.displayText: String
            get() = when (this) {
                TOP_LEFT -> "TL"
                TOP_RIGHT -> "TR"
                BOTTOM_LEFT -> "BL"
                BOTTOM_RIGHT -> "BR"
            }

        fun getCornerAlignment(corner: FieldStartCorner): Alignment {
            return when (corner) {
                TOP_LEFT -> Alignment.TopStart
                TOP_RIGHT -> Alignment.TopEnd
                BOTTOM_LEFT -> Alignment.BottomStart
                BOTTOM_RIGHT -> Alignment.BottomEnd
            }
        }

        fun getAvailableCorners(rows: Int, cols: Int): List<FieldStartCorner> {
            return when {
                rows == 1 && cols == 1 -> listOf(TOP_LEFT)
                rows == 1 -> listOf(TOP_LEFT, TOP_RIGHT)
                cols == 1 -> listOf(TOP_LEFT,BOTTOM_LEFT)
                else -> entries
            }
        }
    }
}

enum class FieldPattern {
    HORIZONTAL_LINEAR,
    HORIZONTAL_ZIGZAG,
    VERTICAL_LINEAR,
    VERTICAL_ZIGZAG
}