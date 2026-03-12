package com.fieldbook.tracker.viewmodels

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.database.DataGridCache
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DataGridViewModel @Inject constructor(
    private val database: DataHelper,
    private val dataGridCache: DataGridCache,
    private val preferences: SharedPreferences
) : ViewModel() {

    companion object {
        private const val TAG = "DataGridViewModel"
        private const val PROGRESSIVE_BATCH_SIZE = 100
    }

    sealed class UiState {
        object Loading : UiState()
        data class Loaded(
            val traits: List<TraitObject>,
            val rowHeaders: List<DataGridCache.HeaderData>,
            val plotIds: List<String>,
            val gridData: List<List<DataGridCache.CellData>>
        ) : UiState()
        object Empty : UiState()
        object Error : UiState()
    }

    private val _uiState = MutableStateFlow<UiState>(UiState.Loading)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val _columnLocked = MutableStateFlow(true)
    val columnLocked: StateFlow<Boolean> = _columnLocked.asStateFlow()

    fun toggleColumnLock() {
        _columnLocked.value = !_columnLocked.value
    }

    fun loadGrid(rowHeader: String) {
        if (rowHeader.isBlank()) {
            _uiState.value = UiState.Empty
            return
        }

        _uiState.value = UiState.Loading

        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val uniqueHeader = preferences.getString(GeneralKeys.UNIQUE_NAME, "") ?: ""

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val rowHeaderIndex = database.getAllObservationUnitAttributeNames(studyId)
                    .indexOf(rowHeader).takeIf { it >= 0 } ?: 0

                val allTraitObjects = database.allTraitObjects
                val visibleTraits = allTraitObjects.filter { it.visible }
                val traitIds = visibleTraits.map { it.id }.sorted()

                // Cache check
                val snapshot = dataGridCache.get(studyId, traitIds, rowHeader)
                if (snapshot != null) {
                    val currentCount = database.getObservationCount(studyId.toString())
                    if (currentCount == snapshot.observationCount) {
                        Log.d(TAG, "Cache hit. Serving ${snapshot.rowHeaders.size} rows from cache.")
                        _uiState.value = UiState.Loaded(
                            traits = snapshot.traits,
                            rowHeaders = snapshot.rowHeaders,
                            plotIds = snapshot.plotIds,
                            gridData = snapshot.gridData
                        )
                        return@launch
                    }
                    Log.d(TAG, "Cache stale (obs count changed). Reloading.")
                }

                // Full reload: single batch query for repeated-value counts
                val repeatCounts = database.getBatchRepeatCounts(studyId.toString())

                // Use the lightweight DataGrid query (no ValueProcessorFormatAdapter overhead)
                val cursor = database.getDataGridTableData(studyId, ArrayList(visibleTraits))

                if (cursor == null || !cursor.moveToFirst()) {
                    cursor?.close()
                    _uiState.value = UiState.Empty
                    return@launch
                }

                // Build column-index map once (outside the row loop) to avoid repeated indexOf calls
                val columns = (0 until cursor.columnCount).map { cursor.getColumnName(it) }
                val uniqueIndex = columns.indexOf(uniqueHeader)

                if (uniqueIndex < 0) {
                    cursor.close()
                    _uiState.value = UiState.Empty
                    return@launch
                }

                // Pre-compute cursor column index for each visible trait
                val traitColumnIndices = visibleTraits.map { variable ->
                    columns.indexOf(DataHelper.replaceIdentifiers(variable.name))
                }

                val rowHeaders = mutableListOf<DataGridCache.HeaderData>()
                val plotIds = mutableListOf<String>()
                val gridData = mutableListOf<List<DataGridCache.CellData>>()

                Log.d(TAG, "Query executed. Row count: ${cursor.count}")

                try {
                    do {
                        val id = cursor.getString(uniqueIndex) ?: ""
                        val header = cursor.getString(rowHeaderIndex) ?: ""

                        rowHeaders.add(DataGridCache.HeaderData(header, header))
                        plotIds.add(id)

                        val dataList = visibleTraits.mapIndexed { traitIdx, variable ->
                            val colIdx = traitColumnIndices[traitIdx]
                            if (colIdx < 0) {
                                return@mapIndexed DataGridCache.CellData("", id)
                            }

                            val value = cursor.getString(colIdx) ?: ""
                            val hasRepeats = (repeatCounts[Pair(id, variable.id)] ?: 0) > 1
                            var cellValue = value

                            if (variable.format in setOf("categorical", "qualitative")) {
                                try {
                                    cellValue = CategoryJsonUtil.flattenMultiCategoryValue(
                                        CategoryJsonUtil.decode(value),
                                        !variable.categoryDisplayValue
                                    )
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            if (hasRepeats) DataGridCache.CellData("...", id)
                            else DataGridCache.CellData(cellValue, id)
                        }

                        gridData.add(dataList)

                        // Progressive emit: show grid after first batch so user sees data quickly
                        if (gridData.size % PROGRESSIVE_BATCH_SIZE == 0) {
                            _uiState.value = UiState.Loaded(
                                traits = visibleTraits,
                                rowHeaders = rowHeaders.toList(),
                                plotIds = plotIds.toList(),
                                gridData = gridData.toList()
                            )
                        }

                    } while (cursor.moveToNext())

                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                } finally {
                    cursor.close()
                }

                // Store completed result in cache
                val obsCount = database.getObservationCount(studyId.toString())
                dataGridCache.put(
                    DataGridCache.GridSnapshot(
                        studyId = studyId,
                        traitIds = traitIds,
                        rowHeader = rowHeader,
                        observationCount = obsCount,
                        traits = visibleTraits,
                        rowHeaders = rowHeaders,
                        plotIds = plotIds,
                        gridData = gridData
                    )
                )

                // Final state with all rows
                _uiState.value = UiState.Loaded(
                    traits = visibleTraits,
                    rowHeaders = rowHeaders,
                    plotIds = plotIds,
                    gridData = gridData
                )

            } catch (e: Exception) {
                e.printStackTrace()
                _uiState.value = UiState.Error
            }
        }
    }
}
