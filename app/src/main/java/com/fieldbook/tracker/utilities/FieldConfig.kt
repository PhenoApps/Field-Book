package com.fieldbook.tracker.utilities

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
    }
}

enum class FieldPattern {
    HORIZONTAL_LINEAR,
    HORIZONTAL_ZIGZAG,
    VERTICAL_LINEAR,
    VERTICAL_ZIGZAG
}