package com.fieldbook.shared.screens.datagrid

data class DataGridState(
    val loading: Boolean = true,
    val rowHeaderName: String = "",
    val availableRowHeaders: List<String> = emptyList(),
    val traits: List<DataGridTrait> = emptyList(),
    val rows: List<DataGridRow> = emptyList(),
    val activeCell: DataGridCellKey? = null,
    val error: String? = null,
)

data class DataGridTrait(
    val id: Long,
    val name: String,
    val format: String?,
)

data class DataGridRow(
    val plotId: String,
    val header: String,
    val cells: List<DataGridCell>,
)

data class DataGridCell(
    val value: String,
    val repeated: Boolean,
)

data class DataGridCellKey(
    val rowIndexOneBased: Int,
    val traitIndexOneBased: Int,
)

data class DataGridSelection(
    val plotId: String,
    val traitIndex: Int,
    val traitId: Long? = null,
    val rep: Int = 1,
)
