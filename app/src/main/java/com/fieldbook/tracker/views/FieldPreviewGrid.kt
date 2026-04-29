package com.fieldbook.tracker.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import eu.wewox.lazytable.LazyTable
import eu.wewox.lazytable.LazyTableItem
import eu.wewox.lazytable.lazyTableDimensions
import eu.wewox.lazytable.lazyTablePinConfiguration
import com.fieldbook.tracker.enums.FieldStartCorner
import com.fieldbook.tracker.viewmodels.FieldConfig
import com.fieldbook.tracker.enums.GridPreviewMode
import com.fieldbook.tracker.ui.grid.CellType
import com.fieldbook.tracker.ui.grid.FieldGridCell
import com.fieldbook.tracker.ui.grid.GridColors
import com.fieldbook.tracker.ui.grid.GridDisplay
import com.fieldbook.tracker.ui.grid.calculateGridDisplay
import com.fieldbook.tracker.ui.grid.directionSequenceLabel
import com.fieldbook.tracker.ui.grid.isCornerCell
import com.fieldbook.tracker.ui.grid.mapDisplayToActual
import com.fieldbook.tracker.ui.grid.plotNumberLabel
import com.fieldbook.tracker.ui.grid.rememberGridColors
import eu.wewox.lazytable.LazyTableScope
import kotlin.math.max
import kotlin.math.min

@Composable
fun FieldPreviewGrid(
    config: FieldConfig,
    gridPreviewMode: GridPreviewMode = GridPreviewMode.FINAL_PREVIEW,
    onCornerSelected: ((FieldStartCorner) -> Unit)? = null,
    selectedCorner: FieldStartCorner? = null,
    showPlotNumbers: Boolean = false,
    maxDisplayPercentage: Float = 0.8f,
    forceFullView: Boolean = false,
    onCollapsingStateChanged: ((Boolean) -> Unit)? = null,
    highlightedCells: Set<Pair<Int, Int>> = emptySet(),
    useReferenceGridDimensions: Pair<Int, Int>? = null,
    onGridDimensionsCalculated: ((displayRows: Int, displayCols: Int) -> Unit)? = null
) {
    if (config.rows <= 0 || config.cols <= 0) return

    val colors = rememberGridColors()

    val effectiveShowNumbers by remember(config, gridPreviewMode, selectedCorner) {
        derivedStateOf {
            when (gridPreviewMode) {
                GridPreviewMode.BASIC_GRID -> false
                GridPreviewMode.CORNER_SELECTION -> showPlotNumbers && selectedCorner != null
                GridPreviewMode.DIRECTION_PREVIEW -> showPlotNumbers && config.startCorner != null
                GridPreviewMode.PATTERN_PREVIEW -> showPlotNumbers && config.pattern != null
                GridPreviewMode.FINAL_PREVIEW -> showPlotNumbers
            }
        }
    }

    BoxWithConstraints {
        val density = LocalDensity.current
        val cellSize = 40.dp
        val cellSizePx = with(density) { cellSize.toPx() }

        val availableHeightPx = with(density) { (maxHeight * maxDisplayPercentage).toPx() }
        val availableWidthPx = with(density) { (maxWidth * maxDisplayPercentage).toPx() }

        // for forced full view, show all rows/cols
        // if reference grid dimensions available, use that, else calc using default cellSize
        val maxDisplayRows =
            if (forceFullView) config.rows
            else useReferenceGridDimensions?.first  // use stored reference dimensions
            ?: (availableHeightPx / cellSizePx).toInt()

        val maxDisplayCols =
            if (forceFullView) config.cols
            else useReferenceGridDimensions?.second  // use stored reference dimensions
            ?: (availableWidthPx / cellSizePx).toInt()

        val gridDisplay by remember(config.rows, config.cols, maxDisplayRows, maxDisplayCols) {
            derivedStateOf {
                calculateGridDisplay(config.rows, config.cols, maxDisplayRows, maxDisplayCols)
            }
        }

        // save the calculated grid dimensions
        onGridDimensionsCalculated?.invoke(gridDisplay.displayRows, gridDisplay.displayCols)

        // calculate dynamic cell size if using reference dimensions
        val dynamicCellSize: Dp by remember(
            useReferenceGridDimensions, forceFullView, availableWidthPx, availableHeightPx, gridDisplay.displayCols, gridDisplay.displayRows
        ) {
            derivedStateOf {
                if (useReferenceGridDimensions != null && !forceFullView) {
                    val maxCellW = availableWidthPx / gridDisplay.displayCols
                    val maxCellH = availableHeightPx / gridDisplay.displayRows
                    val px = min(min(maxCellW, maxCellH), cellSizePx)
                    with(density) { px.toDp() }
                } else cellSize
            }
        }

        val maxDigits by remember(config, gridPreviewMode, effectiveShowNumbers) {
            derivedStateOf {
                if (!effectiveShowNumbers) 1 else when (gridPreviewMode) {
                    GridPreviewMode.CORNER_SELECTION -> 1
                    GridPreviewMode.DIRECTION_PREVIEW -> max(config.rows, config.cols).toString().length
                    else -> (config.rows * config.cols).toString().length
                }
            }
        }

        // set uniform cell font size
        val uniformFontSize: TextUnit by remember(maxDigits) {
            derivedStateOf {
                when {
                    maxDigits <= 3 -> 12.sp
                    maxDigits <= 5 -> 10.sp
                    maxDigits <= 8 -> 8.sp
                    else -> 6.sp
                }
            }
        }

        val needsCollapsing by remember(gridDisplay) {
            derivedStateOf { gridDisplay.rowHasEllipsis || gridDisplay.colHasEllipsis }
        }
        onCollapsingStateChanged?.invoke(needsCollapsing)

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LazyTable(
                dimensions = lazyTableDimensions(
                    columnSize = { dynamicCellSize },
                    rowSize = { dynamicCellSize }
                ),
                contentPadding = PaddingValues(0.dp),
                pinConfiguration = lazyTablePinConfiguration(columns = 0, rows = 0)
            ) {
                renderGrid(
                    lazyTable = this,
                    config = config,
                    gridDisplay = gridDisplay,
                    showPlotNumbers = effectiveShowNumbers,
                    onCornerSelected = onCornerSelected,
                    highlightedCells = highlightedCells,
                    gridPreviewMode = gridPreviewMode,
                    colors = colors,
                    fontSize = uniformFontSize
                )
            }
        }
    }
}

private fun renderGrid(
    lazyTable: LazyTableScope,
    config: FieldConfig,
    gridDisplay: GridDisplay,
    showPlotNumbers: Boolean,
    onCornerSelected: ((FieldStartCorner) -> Unit)? = null,
    highlightedCells: Set<Pair<Int, Int>> = emptySet(),
    gridPreviewMode: GridPreviewMode,
    colors: GridColors,
    fontSize: TextUnit
) {
    lazyTable.items(
        count = gridDisplay.displayRows * gridDisplay.displayCols,
        layoutInfo = {
            val row = it / gridDisplay.displayCols
            val column = it % gridDisplay.displayCols
            LazyTableItem(column = column, row = row)
        }
    ) { index ->
        val displayRow = index / gridDisplay.displayCols
        val displayColumn = index % gridDisplay.displayCols

        val actualRowIndex = mapDisplayToActual(displayRow, gridDisplay.headRowCount, gridDisplay.tailRowCount, config.rows, gridDisplay.rowHasEllipsis)
        val actualColIndex = mapDisplayToActual(displayColumn, gridDisplay.headColCount, gridDisplay.tailColCount, config.cols, gridDisplay.colHasEllipsis)

        if (actualRowIndex == -1 || actualColIndex == -1) { // ellipsis cell
            FieldGridCell("⋯", CellType.REGULAR, colors.copy(highlight = colors.highlight), fontSize = fontSize, onClick = null)
            return@items
        }

        val isCorner = isCornerCell(actualRowIndex, actualColIndex, config.rows, config.cols)
        val cornerType = if (isCorner) {
            FieldStartCorner.fromPosition(actualRowIndex, actualColIndex, config.rows, config.cols)
        } else null
        val isSelected = cornerType != null && cornerType == config.startCorner
        val isHighlighted = highlightedCells.contains(actualRowIndex to actualColIndex)

        val cellLabel = if (!showPlotNumbers) "" else when (gridPreviewMode) {
            GridPreviewMode.CORNER_SELECTION -> if (isSelected) "1" else ""
            GridPreviewMode.DIRECTION_PREVIEW -> {
                if (isSelected) "1"
                else if (isHighlighted && config.startCorner != null && config.isHorizontal != null) {
                    directionSequenceLabel(actualRowIndex, actualColIndex, config)
                } else ""
            }
            else -> plotNumberLabel(actualRowIndex, actualColIndex, config)
        }

        val cellType = when {
            isSelected -> CellType.SELECTED
            isHighlighted -> CellType.HIGHLIGHTED
            isCorner && gridPreviewMode == GridPreviewMode.CORNER_SELECTION -> CellType.CORNER
            else -> CellType.REGULAR
        }


        val onClick = if (isCorner && gridPreviewMode == GridPreviewMode.CORNER_SELECTION && onCornerSelected != null) {
            { cornerType?.let { onCornerSelected(it) }; Unit }
        } else null

        FieldGridCell(cellLabel, cellType, colors, fontSize, onClick)
    }
}