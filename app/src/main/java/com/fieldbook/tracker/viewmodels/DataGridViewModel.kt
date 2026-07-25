package com.fieldbook.tracker.viewmodels

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.database.DataGridCache
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.ObservationChangeTracker
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.formats.coders.DateJsonCoder
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
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
            val gridData: List<List<DataGridCache.CellData>>,
            val extraHeaderNames: List<String> = emptyList(),
            val extraHeaderData: List<List<String>> = emptyList()
        ) : UiState()
        object Empty : UiState()
        object Error : UiState()
    }

    data class SortState(
        val columnIndex: Int = -1, // -1 = default DB order; 0 = row-header; 1+ = trait col (traitIdx = columnIndex-1)
        val ascending: Boolean = true
    )

    private val _rawUiState = MutableStateFlow<UiState>(UiState.Loading)
    private val _sortState = MutableStateFlow(
        SortState(
            columnIndex = preferences.getInt(GeneralKeys.DATAGRID_SORT_COLUMN, -1),
            ascending = preferences.getBoolean(GeneralKeys.DATAGRID_SORT_ASCENDING, true)
        )
    )
    val sortState: StateFlow<SortState> = _sortState.asStateFlow()

    val uiState: StateFlow<UiState> = combine(_rawUiState, _sortState) { raw, sort ->
        applySorting(raw, sort)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState.Loading)

    /** The plot IDs in original DB order — used to resolve the incoming activePlotId integer index. */
    val rawPlotIds: List<String>
        get() = (_rawUiState.value as? UiState.Loaded)?.plotIds ?: emptyList()

    // Locked columns — both field (row header) columns, keyed by header name, and trait
    // columns, keyed by trait id — share one set so any column can be pinned individually.
    private val _lockedColumnIds = MutableStateFlow(
        preferences.getStringSet(GeneralKeys.DATAGRID_LOCKED_COLUMN_IDS, null)?.toSet() ?: emptySet()
    )
    val lockedColumnIds: StateFlow<Set<String>> = _lockedColumnIds.asStateFlow()

    /** True when every currently visible field column is locked. Drives the toolbar lock icon and map view pin. */
    val columnLocked: StateFlow<Boolean> = combine(_lockedColumnIds, _rawUiState) { locked, raw ->
        val fieldColumns = (raw as? UiState.Loaded)?.extraHeaderNames ?: emptyList()
        fieldColumns.isNotEmpty() && fieldColumns.all { it in locked }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private fun persistLockedColumns(newValue: Set<String>) {
        _lockedColumnIds.value = newValue
        preferences.edit().putStringSet(GeneralKeys.DATAGRID_LOCKED_COLUMN_IDS, newValue).apply()
    }

    /** Bulk toggle for the toolbar action: locks/unlocks all field columns together. */
    fun toggleColumnLock() {
        val fieldColumns = (_rawUiState.value as? UiState.Loaded)?.extraHeaderNames ?: emptyList()
        if (fieldColumns.isEmpty()) return
        val allLocked = fieldColumns.all { it in _lockedColumnIds.value }
        val newValue = if (allLocked) _lockedColumnIds.value - fieldColumns.toSet()
                       else _lockedColumnIds.value + fieldColumns.toSet()
        persistLockedColumns(newValue)
    }

    /** Per-column toggle used by long-pressing a field or trait column header. */
    fun toggleColumn(columnId: String) {
        val current = _lockedColumnIds.value
        val newValue = if (columnId in current) current - columnId else current + columnId
        persistLockedColumns(newValue)
    }

    /** The first time the grid ever loads with no saved lock preference, pin the first field column. */
    private fun applyDefaultColumnLock(extraHeaders: List<String>) {
        if (extraHeaders.isEmpty()) return
        if (preferences.contains(GeneralKeys.DATAGRID_LOCKED_COLUMN_IDS)) return
        persistLockedColumns(setOf(extraHeaders.first()))
    }

    private val _wrapContent = MutableStateFlow(preferences.getBoolean(GeneralKeys.DATAGRID_WRAP_CONTENT, false))
    val wrapContent: StateFlow<Boolean> = _wrapContent.asStateFlow()

    fun toggleWrapContent() {
        val newValue = !_wrapContent.value
        _wrapContent.value = newValue
        preferences.edit().putBoolean(GeneralKeys.DATAGRID_WRAP_CONTENT, newValue).apply()
    }

    private val _heatmapEnabled = MutableStateFlow(preferences.getBoolean(GeneralKeys.DATAGRID_HEATMAP, false))
    val heatmapEnabled: StateFlow<Boolean> = _heatmapEnabled.asStateFlow()

    fun toggleHeatmap() {
        val newValue = !_heatmapEnabled.value
        _heatmapEnabled.value = newValue
        preferences.edit().putBoolean(GeneralKeys.DATAGRID_HEATMAP, newValue).apply()
    }

    // Grid and map views zoom independently of each other, each with its own saved preference.
    private val _zoomLevel = MutableStateFlow(preferences.getFloat(GeneralKeys.DATAGRID_ZOOM, 1f))
    val zoomLevel: StateFlow<Float> = _zoomLevel.asStateFlow()

    fun zoomIn() = setZoom(_zoomLevel.value + 0.25f)

    fun zoomOut() = setZoom(_zoomLevel.value - 0.25f)

    fun setZoom(zoom: Float) {
        val newValue = zoom.coerceIn(0.5f, 2f)
        _zoomLevel.value = newValue
        preferences.edit().putFloat(GeneralKeys.DATAGRID_ZOOM, newValue).apply()
    }

    private val _mapZoomLevel = MutableStateFlow(preferences.getFloat(GeneralKeys.DATAGRID_MAP_ZOOM, 1f))
    val mapZoomLevel: StateFlow<Float> = _mapZoomLevel.asStateFlow()

    fun mapZoomIn() = setMapZoom(_mapZoomLevel.value + 0.25f)

    fun mapZoomOut() = setMapZoom(_mapZoomLevel.value - 0.25f)

    fun setMapZoom(zoom: Float) {
        val newValue = zoom.coerceIn(0.5f, 2f)
        _mapZoomLevel.value = newValue
        preferences.edit().putFloat(GeneralKeys.DATAGRID_MAP_ZOOM, newValue).apply()
    }

    override fun onCleared() {
        super.onCleared()
    }

    fun sortByColumn(columnIndex: Int) {
        val cur = _sortState.value
        val newState = if (cur.columnIndex == columnIndex) SortState(columnIndex, !cur.ascending)
                       else SortState(columnIndex, true)
        _sortState.value = newState
        preferences.edit()
            .putInt(GeneralKeys.DATAGRID_SORT_COLUMN, newState.columnIndex)
            .putBoolean(GeneralKeys.DATAGRID_SORT_ASCENDING, newState.ascending)
            .apply()
    }

    fun resetSort() {
        _sortState.value = SortState()
        preferences.edit()
            .putInt(GeneralKeys.DATAGRID_SORT_COLUMN, -1)
            .putBoolean(GeneralKeys.DATAGRID_SORT_ASCENDING, true)
            .apply()
    }

    private fun applySorting(raw: UiState, sort: SortState): UiState {
        if (sort.columnIndex < 0 || raw !is UiState.Loaded) return raw
        val extraCount = raw.extraHeaderNames.size
        val comparator = Comparator<Int> { a, b ->
            val aStr = when {
                sort.columnIndex < extraCount -> raw.extraHeaderData.getOrNull(a)?.getOrNull(sort.columnIndex) ?: ""
                else -> raw.gridData.getOrNull(a)?.getOrNull(sort.columnIndex - extraCount)?.value ?: ""
            }
            val bStr = when {
                sort.columnIndex < extraCount -> raw.extraHeaderData.getOrNull(b)?.getOrNull(sort.columnIndex) ?: ""
                else -> raw.gridData.getOrNull(b)?.getOrNull(sort.columnIndex - extraCount)?.value ?: ""
            }
            numericAwareCompare(aStr, bStr)
        }
        val indices = raw.rowHeaders.indices.sortedWith(if (sort.ascending) comparator else comparator.reversed())
        return UiState.Loaded(
            traits           = raw.traits,
            rowHeaders       = indices.map { raw.rowHeaders[it] },
            plotIds          = indices.map { raw.plotIds[it] },
            gridData         = indices.map { raw.gridData[it] },
            extraHeaderNames = raw.extraHeaderNames,
            extraHeaderData  = indices.map { raw.extraHeaderData.getOrNull(it) ?: emptyList() }
        )
    }

    private fun numericAwareCompare(a: String, b: String): Int {
        val aNum = a.toDoubleOrNull()
        val bNum = b.toDoubleOrNull()
        return when {
            aNum != null && bNum != null -> aNum.compareTo(bNum)
            aNum != null && bNum == null -> 1   // numeric (a) sorts before NA (b)
            aNum == null && bNum != null -> -1  // NA (a) sorts after numeric (b)
            else -> a.compareTo(b, ignoreCase = true)
        }
    }

    fun loadGrid(rowHeader: String, extraHeaders: List<String> = emptyList()) {
        applyDefaultColumnLock(extraHeaders)

        if (rowHeader.isBlank()) {
            _rawUiState.value = UiState.Empty
            return
        }

        _rawUiState.value = UiState.Loading

        val studyId = preferences.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)
        val uniqueHeader = preferences.getString(GeneralKeys.UNIQUE_NAME, "") ?: ""

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val allTraitObjects = database.allTraitObjects
                val visibleTraits = allTraitObjects.filter { it.visible }
                // Order matters here (not just membership) so the cache key changes when the
                // user reorders traits, even if the set of visible traits is unchanged.
                val traitsFingerprint = dataGridCache.fingerprintTraits(visibleTraits)

                // Captured before the query runs, so a write that lands mid-query leaves the
                // snapshot marked with the older revision and is caught on the next open.
                val observationsRevision = ObservationChangeTracker.current

                // Cache check
                val snapshot = dataGridCache.get(studyId, traitsFingerprint, rowHeader, extraHeaders)
                if (snapshot != null) {
                    Log.d(TAG, "Cache hit. Serving ${snapshot.rowHeaders.size} rows from cache.")
                    _rawUiState.value = UiState.Loaded(
                        traits = snapshot.traits,
                        rowHeaders = snapshot.rowHeaders,
                        plotIds = snapshot.plotIds,
                        gridData = snapshot.gridData,
                        extraHeaderNames = snapshot.extraHeaders,
                        extraHeaderData = snapshot.extraHeaderData
                    )
                    return@launch
                }

                // Full reload: single batch query for repeated-value counts and latest values
                val repeatSummaries = database.getBatchRepeatSummaries(studyId.toString())

                // Only pivot the attribute columns the grid actually displays.
                val requiredAttributes = (listOf(uniqueHeader, rowHeader) + extraHeaders).distinct()

                // Use the lightweight DataGrid query (no ValueProcessorFormatAdapter overhead)
                val cursor = database.getDataGridTableData(studyId, ArrayList(visibleTraits), requiredAttributes)

                if (cursor == null || !cursor.moveToFirst()) {
                    cursor?.close()
                    _rawUiState.value = UiState.Empty
                    return@launch
                }

                // Build column-index map once (outside the row loop) to avoid repeated indexOf calls
                val columns = (0 until cursor.columnCount).map { cursor.getColumnName(it) }
                val uniqueIndex = columns.indexOf(uniqueHeader)
                val rowHeaderIndex = columns.indexOf(rowHeader)

                if (uniqueIndex < 0) {
                    cursor.close()
                    _rawUiState.value = UiState.Empty
                    return@launch
                }

                // Pre-compute cursor column index for each visible trait
                val traitColumnIndices = visibleTraits.map { variable ->
                    columns.indexOf(DataHelper.replaceIdentifiers(variable.name))
                }

                // Pre-compute cursor column indices for extra headers
                val extraHeaderColumnIndices = extraHeaders.map { name -> columns.indexOf(name) }

                val rowHeaders = mutableListOf<DataGridCache.HeaderData>()
                val plotIds = mutableListOf<String>()
                val gridData = mutableListOf<List<DataGridCache.CellData>>()
                val extraHeaderDataList = mutableListOf<List<String>>()

                Log.d(TAG, "Query executed. Row count: ${cursor.count}")

                try {
                    do {
                        val id = cursor.getString(uniqueIndex) ?: ""
                        val header = cursor.getString(rowHeaderIndex) ?: ""

                        rowHeaders.add(DataGridCache.HeaderData(header))
                        plotIds.add(id)

                        val extraData = extraHeaders.indices.map { idx ->
                            val colIdx = extraHeaderColumnIndices[idx]
                            if (colIdx >= 0) cursor.getString(colIdx) ?: "" else ""
                        }
                        extraHeaderDataList.add(extraData)

                        val dataList = visibleTraits.mapIndexed { traitIdx, variable ->
                            val colIdx = traitColumnIndices[traitIdx]
                            if (colIdx < 0) {
                                return@mapIndexed DataGridCache.CellData("", id)
                            }

                            val repeats = repeatSummaries[Pair(id, variable.id)]

                            // Repeats only collapse to an ellipsis for traits that actually have
                            // repeated measures enabled — those get a picker dialog on tap. A trait
                            // without the parameter can still accumulate repeats (BrAPI import, or
                            // the parameter disabled after collection); there the latest rep is the
                            // meaningful value, so show it rather than an ellipsis the user can't
                            // expand.
                            if (repeats != null && variable.repeatedMeasures) {
                                return@mapIndexed DataGridCache.CellData("...", id)
                            }

                            // The pivoted column is MAX(value) across reps, which is lexicographic
                            // rather than latest, so prefer the summary's highest-rep value.
                            val value = repeats?.latestValue
                                ?: cursor.getString(colIdx)
                                ?: ""
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
                            } else if (variable.format == "date") {
                                try {
                                    val decoded = DateJsonCoder().decode(value)
                                    if (decoded is DateJsonCoder.DateJson) {
                                        cellValue = decoded.formattedDate
                                    }
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }

                            DataGridCache.CellData(cellValue, id)
                        }

                        gridData.add(dataList)

                        // Progressive emit: show grid after first batch so user sees data quickly
                        if (gridData.size % PROGRESSIVE_BATCH_SIZE == 0) {
                            _rawUiState.value = UiState.Loaded(
                                traits = visibleTraits,
                                rowHeaders = rowHeaders.toList(),
                                plotIds = plotIds.toList(),
                                gridData = gridData.toList(),
                                extraHeaderNames = extraHeaders,
                                extraHeaderData = extraHeaderDataList.toList()
                            )
                        }

                    } while (cursor.moveToNext())

                } catch (e: IllegalStateException) {
                    e.printStackTrace()
                    _rawUiState.value = UiState.Error
                    return@launch
                } finally {
                    cursor.close()
                }

                // Store completed result in cache
                dataGridCache.put(
                    DataGridCache.GridSnapshot(
                        studyId = studyId,
                        traitsFingerprint = traitsFingerprint,
                        rowHeader = rowHeader,
                        extraHeaders = extraHeaders,
                        observationsRevision = observationsRevision,
                        traits = visibleTraits,
                        rowHeaders = rowHeaders.toList(),
                        plotIds = plotIds.toList(),
                        gridData = gridData.toList(),
                        extraHeaderData = extraHeaderDataList.toList()
                    )
                )

                // Final state with all rows
                _rawUiState.value = UiState.Loaded(
                    traits = visibleTraits,
                    rowHeaders = rowHeaders,
                    plotIds = plotIds,
                    gridData = gridData,
                    extraHeaderNames = extraHeaders,
                    extraHeaderData = extraHeaderDataList
                )

            } catch (e: Exception) {
                e.printStackTrace()
                _rawUiState.value = UiState.Error
            }
        }
    }
}
