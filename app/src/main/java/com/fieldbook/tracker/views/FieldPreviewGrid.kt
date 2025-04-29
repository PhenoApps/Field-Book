package com.fieldbook.tracker.views

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import eu.wewox.lazytable.LazyTable
import eu.wewox.lazytable.LazyTableItem
import eu.wewox.lazytable.lazyTableDimensions
import eu.wewox.lazytable.lazyTablePinConfiguration
import com.fieldbook.tracker.utilities.FieldConfig
import com.fieldbook.tracker.utilities.FieldStartCorner
import com.fieldbook.tracker.utilities.FieldPlotCalculator
import eu.wewox.lazytable.LazyTableScope

@Composable
fun FieldPreviewGrid(
    config: FieldConfig,
    onCornerSelected: ((FieldStartCorner) -> Unit)? = null,
    selectedCorner: FieldStartCorner? = null,
    showPlotNumbers: Boolean = false,
    height: Float = 200f
) {
    if (config.rows <= 0 || config.cols <= 0) return

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
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
            if (config.showHeaders) {
                renderGridWithHeaders(
                    lazyTable = this,
                    config = config,
                    showPlotNumbers = showPlotNumbers,
                    selectedCorner = selectedCorner,
                    onCornerSelected = onCornerSelected
                )
            } else {
                renderGridWithoutHeaders(
                    lazyTable = this,
                    config = config,
                    showPlotNumbers = showPlotNumbers
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
            HeaderCell(text = "", config = config)
        } else {
            HeaderCell(text = index.toString(), config = config)
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
            HeaderCell(text = row.toString(), config = config)
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
            } else null

            DataCell(
                value = plotNumber?.toString() ?: "",
                config = config,
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
        } else null

        DataCell(
            value = plotNumber?.toString() ?: "",
            config = config,
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

@Composable
private fun HeaderCell(text: String, config: FieldConfig) {
    TableCell(
        text = text,
        backgroundColor = Color(config.headerCellBgColor),
        textColor = if (config.cellTextColor == 0) Color.Black else Color(config.cellTextColor),
        isBorderVisible = false
    )
}

@Composable
private fun DataCell(
    value: String,
    config: FieldConfig,
    isCorner: Boolean = false,
    isSelected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    TableCell(
        text = value,
        backgroundColor = when {
            isSelected -> Color.Blue.copy(alpha = 0.3f)
            isCorner -> Color.Gray.copy(alpha = 0.2f)
            else -> Color(config.cellBgColor)
        },
        textColor = Color(config.cellTextColor),
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
            textAlign = TextAlign.Center
        )
    }
}