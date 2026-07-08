package com.fieldbook.tracker.ui.grid.datagrid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.grid.rememberGridColors
import eu.wewox.lazytable.LazyTable
import eu.wewox.lazytable.LazyTableItem
import eu.wewox.lazytable.lazyTableDimensions
import eu.wewox.lazytable.lazyTablePinConfiguration
import eu.wewox.lazytable.rememberSaveableLazyTableState

@Composable
fun DataGridMapView(
    mapGrid: Array<Array<MapPlotData?>>,
    mapGridRows: Int,
    mapGridCols: Int,
    invertRow: Boolean = false,
    invertCol: Boolean = false,
    activeMapFilter: MapFilter,
    activePlotIdString: String? = null,
    /** Bumped by the caller (e.g. a toolbar "locate" action) to force a re-scroll to the active plot. */
    locateTrigger: Int = 0,
    colors: DataGridUiColors,
    columnLocked: Boolean = true,
    wrapContent: Boolean = false,
    zoom: Float = 1f,
    onMissingLayout: () -> Unit,
    onFilterClicked: (MapFilter) -> Unit,
    onPlotClicked: (MapPlotData) -> Unit,
    onToggleLock: () -> Unit = {}
) {
    LaunchedEffect(mapGridRows, mapGridCols) {
        if (mapGridRows == 0 || mapGridCols == 0) {
            onMissingLayout()
        }
    }

    if (mapGridRows == 0 || mapGridCols == 0) {
        Text(
            text = stringResource(R.string.map_view_no_layout),
            color = Color(colors.cellTextColor),
            textAlign = TextAlign.Center
        )
        return
    }

    val gridColors = rememberGridColors()

    val completeColor = Color(0xFF2E7D32)
    val partialColor = Color(0xFFF57C00)
    val emptyColor = Color(0xFFBDBDBD)

    val activeRowIndices: List<Int>
    val activeColIndices: List<Int>
    val isCondensed = activeMapFilter != MapFilter.NONE

    if (isCondensed) {
        val matches = mutableListOf<Pair<Int, Int>>()
        for (r in 0..<mapGridRows) {
            for (c in 0..<mapGridCols) {
                val plot = mapGrid[r][c]
                val isMatch = when (activeMapFilter) {
                    MapFilter.NONE -> true
                    MapFilter.COMPLETE -> plot?.status == MapPlotStatus.COMPLETE
                    MapFilter.PARTIAL -> plot?.status == MapPlotStatus.PARTIAL
                    MapFilter.EMPTY -> plot?.status == MapPlotStatus.EMPTY
                    MapFilter.MISSING -> plot == null
                }
                if (isMatch) matches.add(r to c)
            }
        }
        activeRowIndices = matches.map { it.first }.distinct().sorted()
        activeColIndices = matches.map { it.second }.distinct().sorted()
    } else {
        activeRowIndices = (0..<mapGridRows).toList()
        activeColIndices = (0..<mapGridCols).toList()
    }

    val displayRows = activeRowIndices.size
    val displayCols = activeColIndices.size
    // Headers are duplicated on all four sides: column 0 / last column are row headers,
    // row 0 / last row are column headers.
    val totalCols = displayCols + 2
    val totalRows = displayRows + 2

    val lazyTableState = rememberSaveableLazyTableState()
    var hasScrolledToActive by rememberSaveable { mutableStateOf(false) }

    suspend fun scrollToActivePlot() {
        if (activePlotIdString == null) return

        var gridRow = -1
        var gridCol = -1
        outer@ for (r in 0 until mapGridRows) {
            for (c in 0 until mapGridCols) {
                if (mapGrid[r][c]?.plotId == activePlotIdString) {
                    gridRow = r
                    gridCol = c
                    break@outer
                }
            }
        }

        if (gridRow >= 0) {
            val displayRowPos = activeRowIndices.indexOf(gridRow)
            val displayColPos = activeColIndices.indexOf(gridCol)
            if (displayRowPos >= 0 && displayColPos >= 0) {
                lazyTableState.animateToCell(column = displayColPos + 1, row = displayRowPos + 1)
                hasScrolledToActive = true
            }
        }
    }

    // Auto-scroll once, the first time the active plot becomes locatable.
    LaunchedEffect(activePlotIdString, mapGridRows, mapGridCols, activeRowIndices, activeColIndices) {
        if (!hasScrolledToActive) scrollToActivePlot()
    }

    // Re-scroll on demand (e.g. the toolbar "locate" action), bypassing the one-time guard.
    LaunchedEffect(locateTrigger) {
        if (locateTrigger > 0) scrollToActivePlot()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.TopStart
        ) {
            val availableWidth = maxWidth

            // Row-header column width matches column-header row height (a single-line-of-text
            // size), rather than the wider size needed to fit a multi-digit row number.
            val baseHeaderSize = if (wrapContent) 20.dp else 32.dp

            val baseCellSize = (if (wrapContent) {
                (availableWidth - baseHeaderSize * 2) / maxOf(displayCols, 1)
            } else {
                64.dp
            }).coerceAtLeast(8.dp)

            val headerWidth = baseHeaderSize * zoom
            val headerHeight = baseHeaderSize * zoom
            val cellSize = baseCellSize * zoom

            // Size to content (up to the available viewport) rather than filling it, so the
            // table sits flush at the top instead of being centered within unused space.
            val totalGridHeight = (headerHeight * 2 + cellSize * displayRows).coerceAtMost(maxHeight)

            // Header numbers reflect the true row/column number. When an axis is inverted the
            // grid is mirrored, so grid index g maps to number (dim - g) instead of (g + 1).
            fun colHeader(gridCol: Int) =
                (if (invertCol) mapGridCols - gridCol else gridCol + 1).toString()

            fun rowHeader(gridRow: Int) =
                (if (invertRow) mapGridRows - gridRow else gridRow + 1).toString()

            // Perf: these are constant across all cells in this pass, so compute them once
            // instead of per-cell. Skip drawing the status glyph when cells are too small to
            // read it — this removes hundreds of Text composables when zoomed out, which is
            // the main cause of scroll jank at low zoom.
            val showLabels = cellSize >= 18.dp
            val labelFontSize = when {
                displayRows * displayCols > 500 -> 8 * zoom
                displayRows * displayCols > 200 -> 10 * zoom
                else -> 12 * zoom
            }.sp

            LazyTable(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(totalGridHeight),
                state = lazyTableState,
                dimensions = lazyTableDimensions(
                    columnSize = { col ->
                        if (col == 0 || col == totalCols - 1) headerWidth else cellSize
                    },
                    rowSize = { row ->
                        if (row == 0 || row == totalRows - 1) headerHeight else cellSize
                    }
                ),
                contentPadding = PaddingValues(0.dp),
                pinConfiguration = lazyTablePinConfiguration(
                    columns = if (columnLocked) 1 else 0,
                    rows = if (columnLocked) 1 else 0
                )
            ) {
                items(
                    count = totalCols,
                    layoutInfo = { LazyTableItem(column = it, row = 0) }
                ) { col ->
                    when (col) {
                        0 -> DataGridMapCornerCell(
                            isLocked = columnLocked,
                            textColor = Color(colors.cellTextColor),
                            borderColor = gridColors.borderColor,
                            zoom = zoom,
                            onClick = onToggleLock
                        )

                        totalCols - 1 -> DataGridMapHeaderCell(
                            text = "",
                            textColor = Color(colors.cellTextColor),
                            borderColor = gridColors.borderColor,
                            zoom = zoom
                        )

                        else -> DataGridMapHeaderCell(
                            text = colHeader(activeColIndices[col - 1]),
                            textColor = Color(colors.cellTextColor),
                            borderColor = gridColors.borderColor,
                            zoom = zoom
                        )
                    }
                }

                items(
                    count = totalCols,
                    layoutInfo = { LazyTableItem(column = it, row = totalRows - 1) }
                ) { col ->
                    val text = if (col == 0 || col == totalCols - 1) "" else colHeader(activeColIndices[col - 1])
                    DataGridMapHeaderCell(
                        text = text,
                        textColor = Color(colors.cellTextColor),
                        borderColor = gridColors.borderColor,
                        zoom = zoom
                    )
                }

                items(
                    count = displayRows * totalCols,
                    layoutInfo = {
                        val r = (it / totalCols) + 1
                        val c = it % totalCols
                        LazyTableItem(column = c, row = r)
                    }
                ) { index ->
                    val rPrime = (index / totalCols)
                    val cPrime = index % totalCols

                    if (cPrime == 0 || cPrime == totalCols - 1) {
                        DataGridMapHeaderCell(
                            text = rowHeader(activeRowIndices[rPrime]),
                            textColor = Color(colors.cellTextColor),
                            borderColor = gridColors.borderColor,
                            zoom = zoom
                        )
                    } else {
                        val plotRow = activeRowIndices[rPrime]
                        val plotCol = activeColIndices[cPrime - 1]
                        val plot =
                            if (plotRow < mapGrid.size && plotCol < mapGrid[plotRow].size) {
                                mapGrid[plotRow][plotCol]
                            } else null

                        val matchesFilter = when (activeMapFilter) {
                            MapFilter.NONE -> true
                            MapFilter.COMPLETE -> plot?.status == MapPlotStatus.COMPLETE
                            MapFilter.PARTIAL -> plot?.status == MapPlotStatus.PARTIAL
                            MapFilter.EMPTY -> plot?.status == MapPlotStatus.EMPTY
                            MapFilter.MISSING -> plot == null
                        }

                        val cellColor = when (plot?.status) {
                            MapPlotStatus.COMPLETE -> completeColor
                            MapPlotStatus.PARTIAL -> partialColor
                            MapPlotStatus.EMPTY -> emptyColor
                            null -> Color.Transparent
                        }

                        val fillColor = if (matchesFilter) cellColor else cellColor.copy(alpha = 0.1f)
                        val borderColor =
                            gridColors.borderColor.copy(alpha = if (matchesFilter) 1f else 0.2f)
                        val isActive = plot != null && plot.plotId == activePlotIdString
                        val activeBorderColor = Color.Black

                        // Hoisted unconditionally (never call remember inside the `if` below,
                        // which would corrupt the slot table when a cell flips occupied/empty).
                        val interactionSource = remember { MutableInteractionSource() }

                        // Perf: paint fill + border in a single drawBehind pass (avoids the extra
                        // graphics layer that Modifier.background + Modifier.border allocate per
                        // cell), and use a click handler without ripple indication \u2014 ripple nodes
                        // across hundreds of visible cells are a major source of scroll jank.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .drawBehind {
                                    drawRect(fillColor)
                                    drawRect(borderColor, style = Stroke(width = 1f))
                                    // The active plot gets a bold border inset within the cell,
                                    // rather than a filled background, so its status color stays visible.
                                    if (isActive) {
                                        val strokeWidthPx = ACTIVE_CELL_BORDER_WIDTH.toPx()
                                        drawRect(
                                            color = activeBorderColor,
                                            topLeft = Offset(strokeWidthPx / 2f, strokeWidthPx / 2f),
                                            size = Size(
                                                size.width - strokeWidthPx,
                                                size.height - strokeWidthPx
                                            ),
                                            style = Stroke(width = strokeWidthPx)
                                        )
                                    }
                                }
                                .then(
                                    if (plot != null) Modifier.clickable(
                                        interactionSource = interactionSource,
                                        indication = null
                                    ) { onPlotClicked(plot) } else Modifier
                                )
                        ) {
                            if (showLabels) {
                                val label = plot?.let {
                                    when (it.status) {
                                        MapPlotStatus.EMPTY -> ""
                                        MapPlotStatus.COMPLETE -> "\u2713"
                                        else -> "~"
                                    }
                                } ?: "\u2715"

                                val textColor = if (plot == null) {
                                    Color.Gray.copy(alpha = if (matchesFilter) 0.5f else 0.1f)
                                } else {
                                    Color.White.copy(alpha = if (matchesFilter) 1f else 0.1f)
                                }

                                Text(
                                    text = label,
                                    color = textColor,
                                    textAlign = TextAlign.Center,
                                    fontSize = labelFontSize,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }

        DataGridMapLegend(
            activeMapFilter = activeMapFilter,
            textColor = Color(colors.cellTextColor),
            onFilterClicked = onFilterClicked
        )
    }
}


@Composable
private fun DataGridMapHeaderCell(
    text: String,
    textColor: Color,
    borderColor: Color,
    zoom: Float
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(Color.White)
            .border(Dp.Hairline, borderColor)
    ) {
        Text(
            text = text,
            fontWeight = FontWeight.Bold,
            fontSize = (10 * zoom).sp,
            color = textColor
        )
    }
}

@Composable
private fun DataGridMapCornerCell(
    isLocked: Boolean,
    textColor: Color,
    borderColor: Color,
    zoom: Float,
    onClick: () -> Unit
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .background(Color.White)
            .border(Dp.Hairline, borderColor)
            .clickable(onClick = onClick)
    ) {
        Icon(
            painter = painterResource(id = if (isLocked) R.drawable.ic_tb_lock else R.drawable.ic_tb_unlock),
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size((16 * zoom).dp)
        )
    }
}

@Composable
private fun DataGridMapLegend(
    activeMapFilter: MapFilter,
    textColor: Color,
    onFilterClicked: (MapFilter) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        DataGridLegendItem(
            stringResource(R.string.map_view_legend_complete),
            Color(0xFF2E7D32),
            textColor = textColor,
            isActive = activeMapFilter == MapFilter.COMPLETE,
            onClick = { onFilterClicked(MapFilter.COMPLETE) }
        )
        DataGridLegendItem(
            stringResource(R.string.map_view_legend_partial),
            Color(0xFFF57C00),
            textColor = textColor,
            isActive = activeMapFilter == MapFilter.PARTIAL,
            onClick = { onFilterClicked(MapFilter.PARTIAL) }
        )
        DataGridLegendItem(
            stringResource(R.string.map_view_legend_empty),
            Color(0xFFBDBDBD),
            textColor = textColor,
            isActive = activeMapFilter == MapFilter.EMPTY,
            onClick = { onFilterClicked(MapFilter.EMPTY) }
        )
        DataGridLegendItem(
            stringResource(R.string.map_view_legend_missing),
            Color.Transparent,
            "\u2715",
            Color.Gray,
            textColor = textColor,
            isActive = activeMapFilter == MapFilter.MISSING,
            onClick = { onFilterClicked(MapFilter.MISSING) }
        )
    }
}

@Composable
private fun DataGridLegendItem(
    label: String,
    color: Color,
    symbol: String? = null,
    symbolColor: Color = Color.White,
    textColor: Color,
    isActive: Boolean = false,
    onClick: () -> Unit = {}
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                if (isActive) Color.Gray.copy(alpha = 0.2f) else Color.Transparent,
                shape = RoundedCornerShape(4.dp)
            )
            .border(
                if (isActive) 1.dp else 0.dp,
                Color.Gray,
                shape = RoundedCornerShape(4.dp)
            )
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color)
                .border(0.5.dp, Color.Gray),
            contentAlignment = Alignment.Center
        ) {
            if (symbol != null) {
                Text(symbol, color = symbolColor, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = textColor)
    }
}
