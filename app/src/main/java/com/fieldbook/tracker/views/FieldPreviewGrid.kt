package com.fieldbook.tracker.views

import android.util.Log
import android.util.TypedValue
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fieldbook.tracker.R
import eu.wewox.lazytable.LazyTable
import eu.wewox.lazytable.LazyTableItem
import eu.wewox.lazytable.lazyTableDimensions
import eu.wewox.lazytable.lazyTablePinConfiguration
import com.fieldbook.tracker.utilities.FieldStartCorner
import com.fieldbook.tracker.utilities.FieldPlotCalculator
import com.fieldbook.tracker.viewmodels.FieldConfig
import eu.wewox.lazytable.LazyTableScope
import kotlin.math.min

@Composable
fun FieldPreviewGrid(
    config: FieldConfig,
    onCornerSelected: ((FieldStartCorner) -> Unit)? = null,
    selectedCorner: FieldStartCorner? = null,
    showPlotNumbers: Boolean = false,
    showHeaders: Boolean = false,
    maxDisplayPercentage: Float = 0.8f,
    forceFullView: Boolean = false,
    onCollapsingStateChanged: ((Boolean) -> Unit)? = null
) {
    if (config.rows <= 0 || config.cols <= 0) return


    BoxWithConstraints {val density = LocalDensity.current
        val cellSize = 40.dp
        val cellSizePx = with(density) { cellSize.toPx() }

        val availableHeightPx = with(density) { (maxHeight * maxDisplayPercentage).toPx() }
        val availableWidthPx = with(density) { (maxWidth * maxDisplayPercentage).toPx() }

        val maxDisplayRows = if (forceFullView) config.rows else (availableHeightPx / cellSizePx).toInt()
        val maxDisplayCols = if (forceFullView) config.cols else (availableWidthPx / cellSizePx).toInt()
        Log.d("TAG", "FieldPreviewGrid: $availableWidthPx $availableWidthPx $maxDisplayRows $maxDisplayCols")

        val gridConfig = calculateGridDisplayConfig(
            totalRows = config.rows,
            totalCols = config.cols,
            maxDisplayRows = maxDisplayRows,
            maxDisplayCols = maxDisplayCols,
            showHeaders = showHeaders
        )

        val needsCollapsing = gridConfig.hasRowEllipsis || gridConfig.hasColEllipsis
        onCollapsingStateChanged?.invoke(needsCollapsing)

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyTable(
                dimensions = lazyTableDimensions(
                    columnSize = { 40.dp },
                    rowSize = { 40.dp }
                ),
                contentPadding = PaddingValues(0.dp),
                pinConfiguration = lazyTablePinConfiguration(
                    columns = 0,
                    rows = 0
                )
            ) {
                if (showHeaders) {
                    renderCollapsedGridWithHeaders(
                        lazyTable = this,
                        config = config,
                        gridConfig = gridConfig,
                        showPlotNumbers = showPlotNumbers,
                        selectedCorner = selectedCorner,
                        onCornerSelected = onCornerSelected
                    )
                } else {
                    renderCollapsedGridWithoutHeaders(
                        lazyTable = this,
                        config = config,
                        gridConfig = gridConfig,
                        showPlotNumbers = showPlotNumbers,
                        selectedCorner = selectedCorner,
                        onCornerSelected = onCornerSelected
                    )
                }
            }
        }
    }
}

private fun calculateGridDisplayConfig(
    totalRows: Int,
    totalCols: Int,
    maxDisplayRows: Int,
    maxDisplayCols: Int,
    showHeaders: Boolean
): GridDisplayConfig {
    val headerOffset = if (showHeaders) 1 else 0
    val effectiveMaxRows = maxDisplayRows - headerOffset
    val effectiveMaxCols = maxDisplayCols - headerOffset

    val needsRowCollapse = totalRows > effectiveMaxRows
    val needsColCollapse = totalCols > effectiveMaxCols

    return if (needsRowCollapse || needsColCollapse) {
        val showRowsCount = min(effectiveMaxRows, totalRows)
        val showColsCount = min(effectiveMaxCols, totalCols)

        val rowsPerSide = if (needsRowCollapse) (showRowsCount - 1) / 2 else showRowsCount
        val colsPerSide = if (needsColCollapse) (showColsCount - 1) / 2 else showColsCount

        GridDisplayConfig(
            displayRows = if (needsRowCollapse) rowsPerSide * 2 + 1 + headerOffset else totalRows + headerOffset,
            displayCols = if (needsColCollapse) colsPerSide * 2 + 1 + headerOffset else totalCols + headerOffset,
            showRowsStart = rowsPerSide,
            showRowsEnd = if (needsRowCollapse) rowsPerSide else 0,
            showColsStart = colsPerSide,
            showColsEnd = if (needsColCollapse) colsPerSide else 0,
            hasRowEllipsis = needsRowCollapse,
            hasColEllipsis = needsColCollapse,
            rowEllipsisPosition = rowsPerSide + headerOffset,
            colEllipsisPosition = colsPerSide + headerOffset
        )
    } else {
        GridDisplayConfig(
            displayRows = totalRows + headerOffset,
            displayCols = totalCols + headerOffset,
            showRowsStart = totalRows,
            showRowsEnd = 0,
            showColsStart = totalCols,
            showColsEnd = 0,
            hasRowEllipsis = false,
            hasColEllipsis = false,
            rowEllipsisPosition = -1,
            colEllipsisPosition = -1
        )
    }
}

private fun renderCollapsedGridWithHeaders(
    lazyTable: LazyTableScope,
    config: FieldConfig,
    gridConfig: GridDisplayConfig,
    showPlotNumbers: Boolean,
    selectedCorner: FieldStartCorner? = null,
    onCornerSelected: ((FieldStartCorner) -> Unit)? = null
) {
    // header row with column numbers
    lazyTable.items(
        count = gridConfig.displayCols,
        layoutInfo = { LazyTableItem(column = it, row = 0) }
    ) { index ->
        if (index == 0) {
            HeaderCell(text = "")
        } else {
            val actualColIndex = getActualColumnIndex(index - 1, gridConfig, config.cols)
            when {
                actualColIndex == -1 -> HeaderCell(text = "...")
                else -> HeaderCell(text = (actualColIndex + 1).toString())
            }
        }
    }

    // data rows
    lazyTable.items(
        count = (gridConfig.displayRows - 1) * gridConfig.displayCols,
        layoutInfo = {
            val row = (it / gridConfig.displayCols) + 1
            val column = it % gridConfig.displayCols
            LazyTableItem(column = column, row = row)
        }
    ) { index ->
        val displayRow = (index / gridConfig.displayCols) + 1
        val displayColumn = index % gridConfig.displayCols

        if (displayColumn == 0) {
            // row headers
            val actualRowIndex = getActualRowIndex(displayRow - 1, gridConfig, config.rows)
            when {
                actualRowIndex == -1 -> HeaderCell(text = "...")
                else -> HeaderCell(text = (actualRowIndex + 1).toString())
            }
        } else {
            // data cells
            val actualRowIndex = getActualRowIndex(displayRow - 1, gridConfig, config.rows)
            val actualColIndex = getActualColumnIndex(displayColumn - 1, gridConfig, config.cols)

            when {
                actualRowIndex == -1 || actualColIndex == -1 -> EllipsisCell()
                else -> {
                    val isCorner = isCornerCell(actualRowIndex, actualColIndex, config.rows, config.cols)
                    val cornerType = if (isCorner)
                        FieldStartCorner.fromPosition(actualRowIndex, actualColIndex, config.rows, config.cols)
                    else null
                    val isSelected = cornerType != null && cornerType == selectedCorner

                    val plotNumber = if (showPlotNumbers && selectedCorner != null) {
                        FieldPlotCalculator.calculatePlotNumber(
                            rowIndex = actualRowIndex,
                            colIndex = actualColIndex,
                            config = config.copy(startCorner = selectedCorner)
                        )
                    } else ""

                    DataCell(
                        value = plotNumber.toString(),
                        isCorner = isCorner,
                        isSelected = isSelected,
                        onClick = if (isCorner && onCornerSelected != null) {
                            { cornerType?.let { onCornerSelected(it) } }
                        } else null
                    )
                }
            }
        }
    }
}

private fun renderCollapsedGridWithoutHeaders(
    lazyTable: LazyTableScope,
    config: FieldConfig,
    gridConfig: GridDisplayConfig,
    showPlotNumbers: Boolean,
    selectedCorner: FieldStartCorner? = null,
    onCornerSelected: ((FieldStartCorner) -> Unit)? = null
) {
    lazyTable.items(
        count = gridConfig.displayRows * gridConfig.displayCols,
        layoutInfo = {
            val row = it / gridConfig.displayCols
            val column = it % gridConfig.displayCols
            LazyTableItem(column = column, row = row)
        }
    ) { index ->
        val displayRow = index / gridConfig.displayCols
        val displayColumn = index % gridConfig.displayCols

        val actualRowIndex = getActualRowIndex(displayRow, gridConfig, config.rows)
        val actualColIndex = getActualColumnIndex(displayColumn, gridConfig, config.cols)

        when {
            actualRowIndex == -1 || actualColIndex == -1 -> EllipsisCell()
            else -> {
                val isCorner = isCornerCell(actualRowIndex, actualColIndex, config.rows, config.cols)
                val cornerType = if (isCorner)
                    FieldStartCorner.fromPosition(actualRowIndex, actualColIndex, config.rows, config.cols)
                else null
                val isSelected = cornerType != null && cornerType == selectedCorner

                val plotNumber = if (showPlotNumbers) {
                    FieldPlotCalculator.calculatePlotNumber(
                        rowIndex = actualRowIndex,
                        colIndex = actualColIndex,
                        config = config
                    )
                } else ""

                DataCell(
                    value = plotNumber.toString(),
                    isCorner = isCorner,
                    isSelected = isSelected,
                    onClick = if (isCorner && onCornerSelected != null) {
                        { cornerType?.let { onCornerSelected(it) } }
                    } else null
                )
            }
        }
    }
}

private fun renderGridWithHeaders(
    lazyTable: LazyTableScope,
    config: FieldConfig,
    showPlotNumbers: Boolean,
    selectedCorner: FieldStartCorner? = null,
    onCornerSelected: ((FieldStartCorner) -> Unit)? = null
) {
    // header row with column numbers
    lazyTable.items(
        count = config.cols + 1,
        layoutInfo = { LazyTableItem(column = it, row = 0) }
    ) { index ->
        if (index == 0) {
            HeaderCell(text = "")
        } else {
            HeaderCell(text = index.toString())
        }
    }

    // rows
    lazyTable.items(
        count = config.rows * (config.cols + 1),
        layoutInfo = {
            val row = (it / (config.cols + 1)) + 1
            val column = it % (config.cols + 1)
            LazyTableItem(column = column, row = row)
        }
    ) { index ->
        val row = (index / (config.cols + 1)) + 1
        val column = index % (config.cols + 1)

        if (column == 0) {
            // row headers
            HeaderCell(text = row.toString())
        } else {
            // data cells
            val isCorner = isCornerCell(row - 1, column - 1, config.rows, config.cols)
            val cornerType = if (isCorner)
                FieldStartCorner.fromPosition(row - 1, column - 1, config.rows, config.cols)
            else null
            val isSelected = cornerType != null && cornerType == selectedCorner

            val plotNumber = if (showPlotNumbers && selectedCorner != null) {
                FieldPlotCalculator.calculatePlotNumber(
                    rowIndex = row - 1,
                    colIndex = column - 1,
                    config = config.copy(startCorner = selectedCorner)
                )
            } else ""

            DataCell(
                value = plotNumber.toString(),
                isCorner = isCorner,
                isSelected = isSelected,
                onClick = if (isCorner && onCornerSelected != null) {
                    { cornerType?.let { onCornerSelected(it) } }
                } else null
            )
        }
    }
}

private fun renderGridWithoutHeaders(
    lazyTable: LazyTableScope,
    config: FieldConfig,
    showPlotNumbers: Boolean
) {
    lazyTable.items(
        count = config.rows * config.cols,
        layoutInfo = {
            val row = it / config.cols
            val column = it % config.cols
            LazyTableItem(column = column, row = row)
        }
    ) { index ->
        val row = index / config.cols
        val column = index % config.cols

        val plotNumber = if (showPlotNumbers) {
            FieldPlotCalculator.calculatePlotNumber(
                rowIndex = row,
                colIndex = column,
                config = config
            )
        } else ""

        DataCell(
            value = plotNumber.toString(),
            isCorner = false,
            isSelected = false,
            onClick = null
        )
    }
}

private fun isCornerCell(row: Int, col: Int, rows: Int, cols: Int): Boolean {
    return (row == 0 && col == 0) ||  // top left
            (row == 0 && col == cols - 1) ||  // top right
            (row == rows - 1 && col == 0) ||  // bottom left
            (row == rows - 1 && col == cols - 1)  // bottom right
}

data class GridDisplayConfig(
    val displayRows: Int,
    val displayCols: Int,
    val showRowsStart: Int,
    val showRowsEnd: Int,
    val showColsStart: Int,
    val showColsEnd: Int,
    val hasRowEllipsis: Boolean,
    val hasColEllipsis: Boolean,
    val rowEllipsisPosition: Int,
    val colEllipsisPosition: Int
)

private fun getActualRowIndex(displayIndex: Int, gridConfig: GridDisplayConfig, totalRows: Int): Int {
    return when {
        !gridConfig.hasRowEllipsis -> displayIndex
        displayIndex < gridConfig.showRowsStart -> displayIndex
        displayIndex == gridConfig.showRowsStart -> -1 // Ellipsis position
        else -> {
            val endRowOffset = displayIndex - gridConfig.showRowsStart - 1 // offset from ellipsis
            totalRows - gridConfig.showRowsEnd + endRowOffset
        }
    }
}

private fun getActualColumnIndex(displayIndex: Int, gridConfig: GridDisplayConfig, totalCols: Int): Int {
    return when {
        !gridConfig.hasColEllipsis -> displayIndex
        displayIndex < gridConfig.showColsStart -> displayIndex
        displayIndex == gridConfig.showColsStart -> -1 // Ellipsis position
        else -> {
            val endColOffset = displayIndex - gridConfig.showColsStart - 1 // offset from ellipsis
            totalCols - gridConfig.showColsEnd + endColOffset
        }
    }
}

@Composable
private fun EllipsisCell() {
    val (cellTextColor, cellBgColor, _) = getColors()

    TableCell(
        text = "⋯",
        backgroundColor = Color(cellBgColor).copy(alpha = 0.7f),
        textColor = Color(cellTextColor).copy(alpha = 0.7f),
        onClick = null
    )
}

@Composable
private fun HeaderCell(text: String) {
    val (cellTextColor, _, headerCellBgColor) = getColors()

    TableCell(
        text = text,
        backgroundColor = Color(headerCellBgColor),
        textColor = if (cellTextColor == 0) Color.Black else Color(cellTextColor),
        isBorderVisible = false
    )
}

@Composable
private fun DataCell(
    value: String,
    isCorner: Boolean = false,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val (cellTextColor, cellBgColor, _) = getColors()

    TableCell(
        text = value,
        backgroundColor = when {
            isSelected -> Color.Blue.copy(alpha = 0.3f)
            isCorner -> Color.Gray.copy(alpha = 0.2f)
            else -> Color(cellBgColor)
        },
        textColor = Color(cellTextColor),
        onClick = onClick
    )
}

@Composable
private fun TableCell(
    text: String,
    backgroundColor: Color,
    textColor: Color,
    isBorderVisible: Boolean = true,
    onClick: (() -> Unit)? = null
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(backgroundColor)
            .border(
                width = if (isBorderVisible) 1.dp else 0.dp,
                color = if (textColor == Color.Transparent) Color.Gray else textColor
            )
            .then(
                if (onClick != null) {
                    Modifier.clickable(onClick = onClick)
                } else {
                    Modifier
                }
            )
    ) {
        Text(
            text = text,
            color = textColor,
            textAlign = TextAlign.Center,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Visible,
            modifier = Modifier
                .fillMaxSize()
                .wrapContentSize(Alignment.Center),
            onTextLayout = { textLayoutResult ->
                // This will be called when text is laid out
            },
            // Use a smaller font size for longer text
            style = LocalTextStyle.current.copy(
                fontSize = when {
                    text.length <= 3 -> 12.sp
                    text.length <= 5 -> 10.sp
                    text.length <= 8 -> 8.sp
                    else -> 6.sp
                }
            )
        )
    }
}

@Composable
private fun getColors(): Triple<Int, Int, Int> {
    val context = LocalContext.current
    return remember {
        val typedValue = TypedValue()
        val theme = context.theme

        theme.resolveAttribute(R.attr.cellTextColor, typedValue, true)
        val cellTextColor = typedValue.data

        theme.resolveAttribute(R.attr.emptyCellColor, typedValue, true)
        val cellBgColor = typedValue.data

        val headerCellBgColor = android.graphics.Color.WHITE

        Triple(cellTextColor, cellBgColor, headerCellBgColor)
    }
}