package com.fieldbook.tracker.ui.grid.datagrid

enum class DataGridViewMode { GRID, MAP }

data class DataGridUiColors(
    val activeCellBgColor: Int,
    val filledCellBgColor: Int,
    val emptyCellBgColor: Int,
    val activeCellTextColor: Int,
    val cellTextColor: Int
)

data class MapPlotData(
    val plotId: String,
    val rowIndex: Int,
    val colIndex: Int,
    val label: String,
    val observedTraits: Int,
    val totalTraits: Int
) {
    val status: MapPlotStatus
        get() = when {
            totalTraits == 0 -> MapPlotStatus.EMPTY
            observedTraits >= totalTraits -> MapPlotStatus.COMPLETE
            observedTraits > 0 -> MapPlotStatus.PARTIAL
            else -> MapPlotStatus.EMPTY
        }
}

enum class MapPlotStatus { COMPLETE, PARTIAL, EMPTY }

enum class MapFilter { NONE, COMPLETE, PARTIAL, EMPTY, MISSING }
