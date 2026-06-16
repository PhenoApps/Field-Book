package com.fieldbook.tracker.ui.grid.datagrid

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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

@Composable
fun DataGridMapView(
    mapGrid: Array<Array<MapPlotData?>>,
    mapGridRows: Int,
    mapGridCols: Int,
    activeMapFilter: MapFilter,
    selectedMapPlotId: String?,
    isSpatial: Boolean,
    colors: DataGridUiColors,
    columnLocked: Boolean = true,
    wrapContent: Boolean = false,
    zoom: Float = 1f,
    onMissingLayout: () -> Unit,
    onFilterClicked: (MapFilter) -> Unit,
    onPlotClicked: (MapPlotData) -> Unit
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
    val columnCount = displayCols + 1

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            DataGridMapLegend(
                activeMapFilter = activeMapFilter,
                textColor = Color(colors.cellTextColor),
                onFilterClicked = onFilterClicked
            )

            BoxWithConstraints(modifier = Modifier.weight(1f)) {
                val availableWidth = maxWidth
                val availableHeight = maxHeight

                LazyTable(
                    modifier = Modifier.fillMaxSize(),
                    dimensions = lazyTableDimensions(
                        columnSize = { col ->
                            if (col == 0) (48 * zoom).dp
                            else if (wrapContent) {
                                (64 * zoom).dp
                            } else {
                                val cellW =
                                    ((availableWidth - 48.dp) / maxOf(displayCols, 1)) * zoom
                                cellW.coerceIn(32.dp, 80.dp)
                            }
                        },
                        rowSize = { row ->
                            if (row == 0) (32 * zoom).dp
                            else if (wrapContent) {
                                (48 * zoom).dp
                            } else {
                                val cellH =
                                    ((availableHeight - 32.dp) / maxOf(displayRows, 1)) * zoom
                                cellH.coerceIn(32.dp, 80.dp)
                            }
                        }
                    ),
                    contentPadding = PaddingValues(4.dp),
                    pinConfiguration = lazyTablePinConfiguration(
                        columns = if (columnLocked) 1 else 0,
                        rows = if (columnLocked) 1 else 0
                    )
                ) {
                    items(
                        count = columnCount,
                        layoutInfo = { LazyTableItem(column = it, row = 0) }
                    ) { col ->
                        val text = when {
                            col == 0 -> if (isSpatial) "Lat\\Lon" else "Y\\X"
                            else -> (activeColIndices[col - 1] + 1).toString()
                        }
                        DataGridMapHeaderCell(
                            text = text,
                            textColor = Color(colors.cellTextColor),
                            borderColor = gridColors.borderColor,
                            zoom = zoom
                        )
                    }

                    items(
                        count = displayRows * columnCount,
                        layoutInfo = {
                            val r = (it / columnCount) + 1
                            val c = it % columnCount
                            LazyTableItem(column = c, row = r)
                        }
                    ) { index ->
                        val rPrime = (index / columnCount)
                        val cPrime = index % columnCount

                        if (cPrime == 0) {
                            DataGridMapHeaderCell(
                                text = "${activeRowIndices[rPrime] + 1}",
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

                            val isSelectedMap =
                                plot != null && plot.plotId == selectedMapPlotId
                            val cellBorder = if (isSelectedMap) {
                                Modifier.border(3.dp, Color(colors.activeCellBgColor))
                            } else {
                                Modifier.border(
                                    Dp.Hairline,
                                    gridColors.borderColor.copy(alpha = if (matchesFilter) 1f else 0.2f)
                                )
                            }

                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .background(
                                        if (matchesFilter) cellColor else cellColor.copy(
                                            alpha = 0.1f
                                        )
                                    )
                                    .then(cellBorder)
                                    .then(if (plot != null) Modifier.clickable {
                                        onPlotClicked(plot)
                                    } else Modifier)
                            ) {
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
                                    fontSize = when {
                                        displayRows * displayCols > 500 -> (8 * zoom).sp
                                        displayRows * displayCols > 200 -> (10 * zoom).sp
                                        else -> (12 * zoom).sp
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
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
            .background(Color.LightGray.copy(alpha = 0.3f))
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
