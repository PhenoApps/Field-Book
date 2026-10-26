package com.fieldbook.tracker.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.utilities.sort.FieldSortKey
import com.fieldbook.tracker.utilities.sort.FieldSortSpec
import com.fieldbook.tracker.utilities.sort.FieldSorter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Holds the sort being edited in the field sort dialog, and previews the resulting entry order.
 * Nothing is saved here, the dialog hands [sortSpec] to its FieldSortController on OK.
 */
@HiltViewModel
class FieldSortViewModel @Inject constructor(
    private val database: DataHelper,
) : ViewModel() {

    companion object {
        const val PREVIEW_SIZE = 100
    }

    /**
     * @param values the entry's value for each of the preview's columns
     * @param groupIndex counts changes of the first sort column, so the preview can shade alternate groups e.g. rows
     */
    data class PreviewRow(
        val position: Int,
        val values: List<String>,
        val groupIndex: Int,
    )

    /**
     * @param previewColumns the preview table's headers: the unique id (unless it is a sort column) then each sort column
     */
    data class UiState(
        val isLoading: Boolean = true,
        val attributes: List<String> = emptyList(),
        val previewColumns: List<String> = emptyList(),
        val preview: List<PreviewRow> = emptyList(),
        val entryCount: Int = 0,
    )

    /** Every entry's attribute values in import order, aligned with [attributes]. */
    private class FieldData(
        val attributes: List<String>,
        val uniqueName: String,
        val rows: List<Array<String?>>,
    ) {
        val columnIndex = attributes.withIndex().associate { it.value to it.index }
    }

    private var studyId: Int? = null

    private val _keys = MutableStateFlow<List<FieldSortKey>>(emptyList())
    private val fieldData = MutableStateFlow<FieldData?>(null)

    /** The sort being edited, separate from [uiState] so reordering updates the list without waiting on the preview. */
    val keys: StateFlow<List<FieldSortKey>> = _keys.asStateFlow()

    val uiState: StateFlow<UiState> = combine(_keys, fieldData) { keys, data ->
        if (data == null) UiState() else buildState(keys, data)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    /** Loads the field's current sort and attribute values, once per study so edits survive configuration changes. */
    fun load(studyId: Int) {

        if (this.studyId == studyId) return

        this.studyId = studyId

        viewModelScope.launch(Dispatchers.IO) {

            val field = database.getFieldObject(studyId)
            val attributes = database.getAllObservationUnitAttributeNames(studyId).toList()

            _keys.value = database.getStudySort(studyId).keys

            fieldData.value = FieldData(
                attributes = attributes,
                uniqueName = field?.uniqueId ?: "",
                rows = database.getObservationUnitAttributeValues(studyId, attributes),
            )
        }
    }

    fun sortSpec() = FieldSortSpec(_keys.value)

    fun addKey(column: String) = updateKeys { it + FieldSortKey(column) }

    fun removeKey(index: Int) = updateKeys { it.filterIndexed { i, _ -> i != index } }

    fun moveKey(from: Int, to: Int) = updateKeys { it.toMutableList().apply { add(to, removeAt(from)) } }

    fun toggleDirection(index: Int) = updateKey(index) { it.copy(ascending = !it.ascending) }

    fun toggleSerpentine(index: Int) = updateKey(index) { it.copy(serpentine = !it.serpentine) }

    fun clearKeys() = updateKeys { emptyList() }

    private fun updateKey(index: Int, transform: (FieldSortKey) -> FieldSortKey) = updateKeys { keys ->
        keys.mapIndexed { i, key -> if (i == index) transform(key) else key }
    }

    //serpentine has no meaning on the first key, so it is cleared when a serpentine key is moved to the top
    private fun updateKeys(transform: (List<FieldSortKey>) -> List<FieldSortKey>) = _keys.update { current ->
        transform(current).mapIndexed { i, key -> if (i == 0 && key.serpentine) key.copy(serpentine = false) else key }
    }

    private fun buildState(keys: List<FieldSortKey>, data: FieldData): UiState {

        //keys for columns this field doesn't have (e.g. a stale sort) stay listed but can't order anything
        val usableKeys = keys.filter { it.column in data.columnIndex }
        val usableColumns = usableKeys.map { data.columnIndex.getValue(it.column) }

        val order = FieldSorter.order(data.rows.size, usableKeys) { row, key ->
            data.rows[row][usableColumns[key]]
        }

        val keyColumns = keys.map { it.column }
        val previewColumns = (listOf(data.uniqueName).filter { it.isNotEmpty() && it !in keyColumns }) + keyColumns
        val previewIndices = previewColumns.map { data.columnIndex[it] }
        val firstKeyIndex = keyColumns.firstOrNull()?.let { data.columnIndex[it] }

        fun valueOf(row: Int, column: Int?) = column?.let { data.rows[row][it] } ?: ""

        var groupIndex = 0
        val preview = order.take(PREVIEW_SIZE).mapIndexed { position, row ->
            if (position > 0 && keys.size > 1 && valueOf(row, firstKeyIndex) != valueOf(order[position - 1], firstKeyIndex)) {
                groupIndex++
            }
            PreviewRow(
                position = position + 1,
                values = previewIndices.map { valueOf(row, it) },
                groupIndex = groupIndex,
            )
        }

        return UiState(
            isLoading = false,
            attributes = data.attributes,
            previewColumns = previewColumns,
            preview = preview,
            entryCount = data.rows.size,
        )
    }
}
