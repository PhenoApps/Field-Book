package com.fieldbook.tracker.enums

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