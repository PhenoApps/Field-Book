package com.fieldbook.tracker.ui.grid

import com.fieldbook.tracker.enums.FieldStartCorner
import com.fieldbook.tracker.enums.FieldWalkingPattern
import com.fieldbook.tracker.viewmodels.FieldConfig
import kotlin.math.min

/**
 * Represents the grid's display on the device
 *
 * @param displayRows The total number of rows to display in the grid
 * @param displayCols The total number of columns to display in the grid
 * @param headRowCount Number of rows to show from top before the ellipsis row
 * @param tailRowCount Number of rows to show from bottom after the ellipsis row
 * @param headColCount Number of cols to show from left before the ellipsis row
 * @param tailColCount Number of cols to show from right after the ellipsis row
 * @param rowHasEllipsis Whether ellipsis (...) needs to be displayed for rows
 * @param colHasEllipsis Whether ellipsis (...) needs to be displayed for columns
 * @param rowEllipsisPos The position where row ellipsis should be placed
 * @param colEllipsisPos The position where column ellipsis should be placed
 */
data class GridDisplay(
    val displayRows: Int,
    val displayCols: Int,
    val headRowCount: Int,
    val tailRowCount: Int,
    val headColCount: Int,
    val tailColCount: Int,
    val rowHasEllipsis: Boolean,
    val colHasEllipsis: Boolean,
    val rowEllipsisPos: Int,
    val colEllipsisPos: Int
)

fun calculateGridDisplay(
    totalRows: Int,
    totalCols: Int,
    maxDisplayRows: Int,
    maxDisplayCols: Int
): GridDisplay {
    val collapseRows = totalRows > maxDisplayRows
    val collapseCols = totalCols > maxDisplayCols

    if (!collapseRows && !collapseCols) { // no collapsing needed
        return GridDisplay(
            displayRows = totalRows,
            displayCols = totalCols,
            headRowCount = totalRows,
            tailRowCount = 0,
            headColCount = totalCols,
            tailColCount = 0,
            rowHasEllipsis = false,
            colHasEllipsis = false,
            rowEllipsisPos = -1,
            colEllipsisPos = -1
        )
    }

    // collapsing required
    val showRows = min(maxDisplayRows, totalRows)
    val showCols = min(maxDisplayCols, totalCols)

    // calc number of rows/cols on each side of the ellipsis
    val rowsPerSide = if (collapseRows) (showRows - 1) / 2 else showRows
    val colsPerSide = if (collapseCols) (showCols - 1) / 2 else showCols

    return GridDisplay(
        displayRows = if (collapseRows) rowsPerSide * 2 + 1 else totalRows,
        displayCols = if (collapseCols) colsPerSide * 2 + 1 else totalCols,
        headRowCount = rowsPerSide,
        tailRowCount = if (collapseRows) rowsPerSide else 0,
        headColCount = colsPerSide,
        tailColCount = if (collapseCols) colsPerSide else 0,
        rowHasEllipsis = collapseRows,
        colHasEllipsis = collapseCols,
        rowEllipsisPos = rowsPerSide,
        colEllipsisPos = colsPerSide
    )
}

/**
 * Map display index to actual index
 * -1 represents an ellipsis cell
 */
fun mapDisplayToActual(displayIndex: Int, headCount: Int, tailCount: Int, total: Int, hasEllipsis: Boolean): Int {
    return when {
        !hasEllipsis -> displayIndex
        displayIndex < headCount -> displayIndex
        displayIndex == headCount -> -1
        else -> total - tailCount + (displayIndex - headCount - 1)
    }
}

fun isCornerCell(row: Int, col: Int, rows: Int, cols: Int): Boolean =
    (row == 0 && col == 0) || // top left
    (row == 0 && col == cols - 1) || // top right
    (row == rows - 1 && col == 0) || // bottom left
    (row == rows - 1 && col == cols - 1) // bottom right

fun directionSequenceLabel(actualRow: Int, actualCol: Int, config: FieldConfig): String {
    val startCorner = config.startCorner ?: return ""
    return when (config.isHorizontal) { // for horizontal direction, show sequence across columns
        true -> when (startCorner) {
            FieldStartCorner.TOP_LEFT, FieldStartCorner.BOTTOM_LEFT -> (actualCol + 1).toString() // moving left to right
            FieldStartCorner.TOP_RIGHT, FieldStartCorner.BOTTOM_RIGHT -> (config.cols - actualCol).toString() // moving right to left
        }
        false -> when (startCorner) { // for vertical direction, show sequence down rows
            FieldStartCorner.TOP_LEFT, FieldStartCorner.TOP_RIGHT -> (actualRow + 1).toString() // moving top to bottom
            FieldStartCorner.BOTTOM_LEFT, FieldStartCorner.BOTTOM_RIGHT -> (config.rows - actualRow).toString() // moving bottom to top
        }
        null -> ""
    }
}

fun plotNumberLabel(rowIndex: Int, colIndex: Int, config: FieldConfig): String {
    val pattern = config.pattern ?: return ""

    // adjust indices based on starting corner
    val (r, c) = when (config.startCorner) {
        FieldStartCorner.TOP_LEFT -> rowIndex to colIndex
        FieldStartCorner.TOP_RIGHT -> rowIndex to (config.cols - 1 - colIndex)
        FieldStartCorner.BOTTOM_LEFT -> (config.rows - 1 - rowIndex) to colIndex
        FieldStartCorner.BOTTOM_RIGHT -> (config.rows - 1 - rowIndex) to (config.cols - 1 - colIndex)
        null -> return ""
    }

    val value = when (pattern) {
        FieldWalkingPattern.HORIZONTAL_LINEAR -> r * config.cols + c + 1
        FieldWalkingPattern.HORIZONTAL_ZIGZAG ->
            if (r % 2 == 0) r * config.cols + c + 1
            else r * config.cols + (config.cols - 1 - c) + 1
        FieldWalkingPattern.VERTICAL_LINEAR -> c * config.rows + r + 1
        FieldWalkingPattern.VERTICAL_ZIGZAG ->
            if (c % 2 == 0) c * config.rows + r + 1
            else c * config.rows + (config.rows - 1 - r) + 1
    }
    return value.toString()
}

enum class CellType { REGULAR, HIGHLIGHTED, SELECTED, CORNER }