package com.fieldbook.tracker.ui.grid.datagrid

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.activities.DataGridActivity
import com.fieldbook.tracker.activities.DataGridActivity.HeaderData
import com.fieldbook.tracker.objects.TraitObject
import eu.wewox.lazytable.LazyTable
import eu.wewox.lazytable.LazyTableItem
import eu.wewox.lazytable.lazyTableDimensions
import eu.wewox.lazytable.lazyTablePinConfiguration
import eu.wewox.lazytable.rememberSaveableLazyTableState

@Composable
fun DataGridTable(
    traits: List<TraitObject>,
    rowHeaders: List<HeaderData>,
    gridData: List<List<DataGridActivity.CellData>>,
    plotIds: List<String>,
    rowHeaderName: String,
    activePlotId: Int? = null,
    activeTrait: Int? = null,
    onCellClicked: (row: Int, col: Int) -> Unit,
) {
    if (traits.isEmpty() || rowHeaders.isEmpty()) {
        return
    }

    val lazyTableState = rememberSaveableLazyTableState()

    val columnCount = traits.size + 1 // +1 for rowHeader column
    val rowCount = rowHeaders.size + 1 // +1 for column headers (traits)

    val targetColumn = activeTrait ?: 1
    val targetRow = activePlotId ?: 1

    LaunchedEffect(traits, rowHeaders) {
        // this will trigger when traits or row headers are updated
        Log.d("DataGridActivity", "Data loaded: ${traits.size} traits, ${rowHeaders.size} rows")
        if (traits.isNotEmpty() && rowHeaders.isNotEmpty() && targetColumn <= traits.size && targetRow <= rowHeaders.size) {
            lazyTableState.animateToCell(column = targetColumn, row = targetRow)
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        LazyTable(
            state = lazyTableState,
            dimensions = lazyTableDimensions(
                columnSize = { col ->
                    when (col) {
                        0 -> 120.dp
                        else -> 100.dp
                    }
                },
                rowSize = { 48.dp } // row height
            ),
            contentPadding = PaddingValues(0.dp),
            pinConfiguration = lazyTablePinConfiguration(
                columns = 1,    // pin the rowHeaders (first column)
                rows = 1        // pin the columnHeaders (first row)
            ),
            // modifier = Modifier
            //     .fillMaxWidth()
        ) {
            // set up the header row
            items(
                count = columnCount,
                layoutInfo = { LazyTableItem(column = it, row = 0) }) { index ->
                if (index == 0) {
                    HeaderCell(text = rowHeaderName)
                } else {
                    val traitIndex = index - 1
                    if (traitIndex < traits.size) {
                        HeaderCell(text = traits[traitIndex].alias)
                    } else {
                        HeaderCell(text = "")
                    }
                }
            }

            // set up the remaining grid cells
            items(
                count = (rowCount - 1) * columnCount,
                layoutInfo = {
                    val row = (it / columnCount) + 1  // +1 to skip header row
                    val column = it % columnCount
                    LazyTableItem(column = column, row = row)
                }
            ) { index ->
                val row = (index / columnCount)
                val column = index % columnCount

                if (column == 0) {
                    // rowHeaders (first column)
                    if (row < rowHeaders.size) {
                        val headerText = rowHeaders[row].name
                        RowHeaderCell(text = headerText)
                    } else {
                        RowHeaderCell(text = "")
                    }
                } else {
                    // data cells
                    val columnIndex = column - 1 // -1 for header column
                    val cellData =
                        if (row < gridData.size && columnIndex < gridData[row].size) {
                            gridData[row][columnIndex]
                        } else null

                    DataCell(
                        value = cellData?.value ?: "",
                        isHighlighted = (row + 1 == activePlotId && columnIndex + 1 == activeTrait)
                    ) {
                        if (cellData != null && row < plotIds.size) {
                            onCellClicked(row, columnIndex)
                        }
                    }
                }
            }
        }
    }
}
