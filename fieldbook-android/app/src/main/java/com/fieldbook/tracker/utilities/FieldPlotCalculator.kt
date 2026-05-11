package com.fieldbook.tracker.utilities

import com.fieldbook.tracker.enums.FieldWalkingPattern
import com.fieldbook.tracker.enums.FieldStartCorner

object FieldPlotCalculator {

    /**
     * Calculates the plot number from start to end (1,2,3...,N) based on the field configuration and coordinates
     */
    fun calculatePlotNumber(
        rowIndex: Int,
        colIndex: Int,
        config: com.fieldbook.tracker.viewmodels.FieldConfig
    ): Int {
        val pattern = config.pattern ?: return 1

        // adjust indices based on starting corner
        val (r, c) = when (config.startCorner) {
            FieldStartCorner.TOP_LEFT -> Pair(rowIndex, colIndex)
            FieldStartCorner.TOP_RIGHT -> Pair(rowIndex, config.cols - 1 - colIndex)
            FieldStartCorner.BOTTOM_LEFT -> Pair(config.rows - 1 - rowIndex, colIndex)
            FieldStartCorner.BOTTOM_RIGHT -> Pair(config.rows - 1 - rowIndex, config.cols - 1 - colIndex)
            else -> TODO()
        }

        return when (pattern) {
            FieldWalkingPattern.HORIZONTAL_LINEAR -> r * config.cols + c + 1
            FieldWalkingPattern.HORIZONTAL_ZIGZAG ->
                if (r % 2 == 0)
                    r * config.cols + c + 1
                else
                    r * config.cols + (config.cols - 1 - c) + 1
            FieldWalkingPattern.VERTICAL_LINEAR -> c * config.rows + r + 1
            FieldWalkingPattern.VERTICAL_ZIGZAG ->
                if (c % 2 == 0)
                    c * config.rows + r + 1
                else
                    c * config.rows + (config.rows - 1 - r) + 1
        }
    }

    /**
     * Calculates physical coordinates for a plot based on row and column
     */
    fun calculatePositionCoordinates(
        row: Int,
        col: Int,
        config: com.fieldbook.tracker.viewmodels.FieldConfig
    ): Pair<Int, Int> {
        return when (config.startCorner) {
            FieldStartCorner.TOP_LEFT -> Pair(row, col)
            FieldStartCorner.TOP_RIGHT -> Pair(row, config.cols - col + 1)
            FieldStartCorner.BOTTOM_LEFT -> Pair(config.rows - row + 1, col)
            FieldStartCorner.BOTTOM_RIGHT -> Pair(config.rows - row + 1, config.cols - col + 1)
            null -> TODO()
        }
    }
}