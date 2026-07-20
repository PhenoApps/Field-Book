package com.fieldbook.tracker.ui.grid.datagrid

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.viewmodels.DataGridViewModel
import eu.wewox.lazytable.LazyTable
import eu.wewox.lazytable.LazyTableItem
import eu.wewox.lazytable.lazyTableDimensions
import eu.wewox.lazytable.lazyTablePinConfiguration
import eu.wewox.lazytable.rememberSaveableLazyTableState
import java.time.LocalDate
import kotlin.math.ceil

@Composable
fun DataGridTable(
    state: DataGridViewModel.UiState.Loaded,
    colors: DataGridUiColors,
    lockedColumnIds: Set<String> = emptySet(),
    sortState: DataGridViewModel.SortState = DataGridViewModel.SortState(),
    wrapContent: Boolean = false,
    heatmapEnabled: Boolean = false,
    zoom: Float = 1f,
    activePlotId: Int? = null,
    activePlotIdString: String? = null,
    activeTrait: Int? = null,
    onSortByColumn: (Int) -> Unit,
    onToggleColumn: (String) -> Unit = {},
    onToggleLock: () -> Unit = {},
    onNavigateFromValue: (plotId: String, traitIndex: Int, rep: Int) -> Unit
) {
    val traits = state.traits
    val rowHeaders = state.rowHeaders
    val plotIds = state.plotIds
    val gridData = state.gridData
    val extraHeaderNames = state.extraHeaderNames
    val extraHeaderData = state.extraHeaderData
    val extraCount = extraHeaderNames.size

    if (traits.isEmpty() || rowHeaders.isEmpty()) {
        return
    }

    val lazyTableState = rememberSaveableLazyTableState()

    val columnCount = traits.size + extraCount
    val rowCount = rowHeaders.size + 1

    // A column's key is its field (header) name, or the trait id for trait columns.
    fun columnKey(rawIndex: Int): String =
        if (rawIndex < extraCount) extraHeaderNames[rawIndex] else traits[rawIndex - extraCount].id

    // Locked columns — field or trait — are moved to the front so they can be pinned together.
    // The underlying table only supports pinning a contiguous leading run of columns.
    val displayOrder: List<Int> = remember(extraHeaderNames, traits, lockedColumnIds) {
        (0 until columnCount).sortedBy { rawIdx -> if (columnKey(rawIdx) in lockedColumnIds) 0 else 1 }
    }
    val rawToDisplayPos: IntArray = remember(displayOrder) {
        IntArray(displayOrder.size).also { arr ->
            displayOrder.forEachIndexed { displayPos, rawIdx -> arr[rawIdx] = displayPos }
        }
    }
    val pinnedColumnCount = remember(extraHeaderNames, traits, lockedColumnIds) {
        (0 until columnCount).count { rawIdx -> columnKey(rawIdx) in lockedColumnIds }
    }

    val activeTraitIdx: Int = activeTrait?.let { pos ->
        traits.indexOfFirst { it.realPosition == pos }
    }?.takeIf { it >= 0 } ?: 0
    val targetColumn = rawToDisplayPos.getOrNull(extraCount + activeTraitIdx) ?: (extraCount + activeTraitIdx)
    val targetRow = activePlotId ?: 1

    var hasScrolled by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(rowHeaders.size) {
        Log.d("DataGridTable", "Data loaded: ${traits.size} traits, ${rowHeaders.size} rows")
        if (!hasScrolled && traits.isNotEmpty() && rowHeaders.isNotEmpty()
            && targetColumn < columnCount && targetRow <= rowHeaders.size
        ) {
            lazyTableState.animateToCell(column = targetColumn, row = targetRow)
            hasScrolled = true
        }
    }

    val columnWidths: List<Dp> = remember(state, wrapContent, displayOrder) {
        if (!wrapContent) emptyList()
        else (0 until columnCount).map { col ->
            val rawIdx = displayOrder.getOrNull(col) ?: col
            val headerLen = when {
                rawIdx < extraCount -> extraHeaderNames[rawIdx].length
                else -> traits.getOrNull(rawIdx - extraCount)?.alias?.length ?: 0
            }
            val maxDataLen = when {
                rawIdx < extraCount -> extraHeaderData.maxOfOrNull {
                    it.getOrNull(rawIdx)?.length ?: 0
                } ?: 0

                else -> gridData.maxOfOrNull { row ->
                    row.getOrNull(rawIdx - extraCount)?.value?.length ?: 0
                } ?: 0
            }
            val maxLen = maxOf(headerLen, maxDataLen).coerceAtLeast(1)
            (maxLen * 10f + 16f).dp.coerceAtLeast(60.dp)
        }
    }

    val rowHeights: List<Dp> = remember(state, wrapContent, columnWidths, displayOrder) {
        if (!wrapContent) emptyList()
        else (0 until rowCount).map { row ->
            val maxLines = (0 until columnCount).maxOf { col ->
                val rawIdx = displayOrder.getOrNull(col) ?: col
                val colWidthPx = (columnWidths.getOrNull(col) ?: 100.dp).value
                val charsPerLine = ((colWidthPx - 16f) / 10f).toInt().coerceAtLeast(1)
                val textLen = when {
                    row == 0 -> when {
                        rawIdx < extraCount -> extraHeaderNames[rawIdx].length
                        else -> traits.getOrNull(rawIdx - extraCount)?.alias?.length ?: 0
                    }

                    rawIdx < extraCount -> extraHeaderData.getOrNull(row - 1)
                        ?.getOrNull(rawIdx)?.length ?: 0

                    else -> gridData.getOrNull(row - 1)
                        ?.getOrNull(rawIdx - extraCount)?.value?.length ?: 0
                }.coerceAtLeast(1)
                ceil(textLen.toFloat() / charsPerLine).toInt().coerceAtLeast(1)
            }
            (maxLines * 20 + 16).dp.coerceAtLeast(48.dp)
        }
    }

    val columnHeatmapRanges: List<Pair<Double, Double>?> = remember(state, heatmapEnabled) {
        if (!heatmapEnabled) List(traits.size) { null }
        else traits.indices.map { colIdx ->
            val nums =
                gridData.mapNotNull { row -> row.getOrNull(colIdx)?.value?.toHeatmapDouble() }
            if (nums.size < 2) null else Pair(nums.min(), nums.max())
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyTable(
            state = lazyTableState,
            dimensions = lazyTableDimensions(
                columnSize = { col ->
                    val rawIdx = displayOrder.getOrNull(col) ?: col
                    if (wrapContent) (columnWidths.getOrNull(col) ?: 100.dp) * zoom
                    else if (rawIdx < extraCount) 120.dp * zoom else 100.dp * zoom
                },
                rowSize = { row ->
                    if (wrapContent) (rowHeights.getOrNull(row) ?: 48.dp) * zoom
                    else 48.dp * zoom
                }
            ),
            contentPadding = PaddingValues(0.dp),
            pinConfiguration = lazyTablePinConfiguration(
                columns = pinnedColumnCount,
                rows = 1
            )
        ) {
            items(
                count = columnCount,
                layoutInfo = { LazyTableItem(column = it, row = 0) }) { index ->
                val rawIdx = displayOrder.getOrNull(index) ?: index
                val isSorted = sortState.columnIndex == rawIdx
                val sortIcon = when {
                    !isSorted -> null
                    sortState.ascending -> R.drawable.ic_chevron_up
                    else -> R.drawable.ic_chevron_down
                }
                val key = columnKey(rawIdx)
                val isLocked = key in lockedColumnIds
                if (rawIdx < extraCount) {
                    DataGridHeaderCell(
                        text = extraHeaderNames[rawIdx],
                        colors = colors,
                        sortIconRes = sortIcon,
                        isLocked = isLocked,
                        showUnlockIcon = index == 0,
                        onClick = { onSortByColumn(rawIdx) },
                        onLongClick = { if (index == 0) onToggleLock() else onToggleColumn(key) },
                        wrapContent = wrapContent,
                        zoom = zoom
                    )
                } else {
                    val trait = traits.getOrNull(rawIdx - extraCount)
                    DataGridHeaderCell(
                        text = trait?.alias ?: "",
                        colors = colors,
                        sortIconRes = sortIcon,
                        isLocked = isLocked,
                        onClick = { onSortByColumn(rawIdx) },
                        onLongClick = { onToggleColumn(key) },
                        wrapContent = wrapContent,
                        zoom = zoom
                    )
                }
            }

            items(
                count = (rowCount - 1) * columnCount,
                layoutInfo = {
                    val row = (it / columnCount) + 1
                    val column = it % columnCount
                    LazyTableItem(column = column, row = row)
                }
            ) { index ->
                val row = (index / columnCount)
                val column = index % columnCount
                val rawIdx = displayOrder.getOrNull(column) ?: column

                if (rawIdx < extraCount) {
                    DataGridRowHeaderCell(
                        text = extraHeaderData.getOrNull(row)?.getOrNull(rawIdx) ?: "",
                        colors = colors,
                        wrapContent = wrapContent,
                        zoom = zoom
                    )
                } else {
                    val columnIndex = rawIdx - extraCount
                    val cellData =
                        if (row < gridData.size && columnIndex < gridData[row].size)
                            gridData[row][columnIndex]
                        else null

                    val heatmapColor: Color? = run {
                        val range = columnHeatmapRanges.getOrNull(columnIndex)
                        val numVal = cellData?.value?.toHeatmapDouble()
                        if (range != null && numVal != null && range.first != range.second) {
                            val t =
                                ((numVal - range.first) / (range.second - range.first)).toFloat()
                                    .coerceIn(0f, 1f)
                            lerpHeatmapColor(t)
                        } else null
                    }

                    DataGridDataCell(
                        value = cellData?.value ?: "",
                        colors = colors,
                        isHighlighted = (plotIds.getOrNull(row) == activePlotIdString && columnIndex == activeTraitIdx),
                        isSelected = false,
                        isLocked = columnKey(rawIdx) in lockedColumnIds,
                        heatmapColor = heatmapColor,
                        wrapContent = wrapContent,
                        zoom = zoom,
                        onClick = {
                            if (cellData != null && row < plotIds.size) {
                                onNavigateFromValue(plotIds[row], columnIndex, 1)
                            }
                        },
                        onLongClick = {
                            if (cellData != null && row < plotIds.size) {
                                onNavigateFromValue(plotIds[row], columnIndex, 1)
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun String.toHeatmapDouble(): Double? {
    if (isBlank() || equals("NA", ignoreCase = true) || this == "...") return null
    toDoubleOrNull()?.let { return it }
    toEpochDayOrNull()?.let { return it.toDouble() }
    return when (lowercase()) {
        "true", "yes" -> 1.0
        "false", "no" -> 0.0
        else -> null
    }
}

/** Parses a "yyyy-MM-dd" date trait value (as decoded by the view model) into an epoch day for gradient coloring. */
private fun String.toEpochDayOrNull(): Long? {
    val parts = split("-")
    if (parts.size != 3) return null
    val year = parts[0].toIntOrNull() ?: return null
    val month = parts[1].toIntOrNull() ?: return null
    val day = parts[2].toIntOrNull() ?: return null
    return try {
        LocalDate.of(year, month, day).toEpochDay()
    } catch (e: Exception) {
        null
    }
}

private fun lerpHeatmapColor(t: Float): Color {
    val low = Color(0xFFF44336.toInt())
    val mid = Color(0xFFFFEB3B.toInt())
    val high = Color(0xFF4CAF50.toInt())
    return if (t < 0.5f) {
        val s = t * 2f
        Color(
            red = low.red + (mid.red - low.red) * s,
            green = low.green + (mid.green - low.green) * s,
            blue = low.blue + (mid.blue - low.blue) * s,
            alpha = 1f
        )
    } else {
        val s = (t - 0.5f) * 2f
        Color(
            red = mid.red + (high.red - mid.red) * s,
            green = mid.green + (high.green - mid.green) * s,
            blue = mid.blue + (high.blue - mid.blue) * s,
            alpha = 1f
        )
    }
}
