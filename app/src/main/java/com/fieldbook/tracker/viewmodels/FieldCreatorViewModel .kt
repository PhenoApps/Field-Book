package com.fieldbook.tracker.viewmodels

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.enums.FieldCreationStep
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.ImportFormat
import com.fieldbook.tracker.enums.FieldWalkingPattern
import com.fieldbook.tracker.enums.FieldStartCorner
import com.fieldbook.tracker.viewmodels.FieldConfig.Companion.formatted
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/**
 * Used for state management across field creator activity and fragments
 *
 * Handles all data validation, logic and db related operation
 * Uses LiveData for reactive UI updates across fragments/activity
 *
 * FieldConfig: contains the name, rows, cols, start corner, walking direction and pattern
 * ValidationErrors: used in FieldCreatorSizeFragment to validate input
 * CreationResult: denotes the status of field creation during preview/expanded preview
 * FieldCreationStep: tracks the current step used to manage stepper UI
 * ReferenceGridDimensions: maintains consistent grid sizing across fragments
 */
class FieldCreatorViewModel : ViewModel() {

    private val _fieldConfig = MutableLiveData(FieldConfig())
    val fieldConfig: LiveData<FieldConfig> = _fieldConfig

    private val _validationErrors = MutableLiveData(ValidationError())
    val validationErrors: LiveData<ValidationError> = _validationErrors

    private val _creationResult = MutableLiveData<FieldCreationResult>()
    val creationResult: LiveData<FieldCreationResult> = _creationResult

    private val _currentStep = MutableLiveData<FieldCreationStep>()
    val currentStep: LiveData<FieldCreationStep> = _currentStep

    private val _referenceGridDimensions = MutableLiveData<Pair<Int, Int>?>(null)
    val referenceGridDimensions: LiveData<Pair<Int, Int>?> = _referenceGridDimensions

    private var createFieldJob: Job? = null

    fun updateFieldName(name: String) {
        _fieldConfig.value = _fieldConfig.value?.copy(fieldName = name.trim())
        if (_validationErrors.value?.fieldNameError != null) {
            _validationErrors.value = _validationErrors.value?.copy(fieldNameError = null)
        }
    }

    /**
     * Only update the dimensions if rows/cols are <= max
     */
    fun updateDimensions(rows: Int, cols: Int, context: Context?) {
        var errors = _validationErrors.value ?: ValidationError()

        val validRows = if (rows > FieldConfig.MAX_ROWS) { // keep previous value and show error
            errors = errors.copy(
                rowsError = context?.getString(R.string.field_creator_rows_exceeded_error, FieldConfig.MAX_ROWS.formatted())
            )
            _fieldConfig.value?.rows ?: 0
        } else {
            errors = errors.copy(rowsError = null)
            rows
        }

        val validCols = if (cols > FieldConfig.MAX_COLS) { // keep previous value and show error
            errors = errors.copy(
                colsError = context?.getString(R.string.field_creator_cols_exceeded_error, FieldConfig.MAX_COLS.formatted())
            )
            _fieldConfig.value?.cols ?: 0
        } else {
            errors = errors.copy(colsError = null)
            cols
        }

        errors = errors.copy(dimensionsError = validateDimensionsError(validRows, validCols, context))

        _fieldConfig.value = _fieldConfig.value?.copy(rows = validRows, cols = validCols)
        _validationErrors.value = errors
    }

    fun updateStartCorner(corner: FieldStartCorner) {
        _fieldConfig.value = _fieldConfig.value?.copy(startCorner = corner)
    }

    fun updatePatternType(isZigzag: Boolean) {
        _fieldConfig.value = _fieldConfig.value?.copy(isZigzag = isZigzag)
    }

    fun updateDirection(isHorizontal: Boolean) {
        _fieldConfig.value = _fieldConfig.value?.copy(isHorizontal = isHorizontal)
    }

    fun updateCurrentStep(step: FieldCreationStep) {
        _currentStep.value = step
    }

    fun setReferenceGridDimensions(displayRows: Int, displayCols: Int) {
        _referenceGridDimensions.value = Pair(displayRows, displayCols)
    }

    fun validateNameAndDimensions(db: DataHelper, context: Context?): Boolean {
        val state = _fieldConfig.value ?: return false
        var hasErrors = false
        var errors = ValidationError()

        if (state.fieldName.isBlank()) {
            errors = errors.copy(fieldNameError = context?.getString(R.string.field_name_required_error))
            hasErrors = true
        } else {
            val nameExists = db.allFieldObjects.any { it.name == state.fieldName }
            if (nameExists) {
                errors = errors.copy(fieldNameError = context?.getString(R.string.field_name_exists_error))
                hasErrors = true
            }
        }

        if (state.rows < 1) {
            errors = errors.copy(rowsError = context?.getString(R.string.field_creator_positive_number_error))
            hasErrors = true
        } else if (state.rows > FieldConfig.MAX_ROWS) {
            errors = errors.copy(rowsError = context?.getString(R.string.field_creator_rows_exceeded_error, FieldConfig.MAX_ROWS.formatted()))
            hasErrors = true
        }

        if (state.cols < 1) {
            errors = errors.copy(colsError = context?.getString(R.string.field_creator_positive_number_error))
            hasErrors = true
        } else if (state.cols > FieldConfig.MAX_COLS) {
            errors = errors.copy(colsError = context?.getString(R.string.field_creator_cols_exceeded_error, FieldConfig.MAX_COLS.formatted()))
            hasErrors = true
        }

        val dimensionsError = validateDimensionsError(state.rows, state.cols, context)
        errors = errors.copy(dimensionsError = dimensionsError)
        if (dimensionsError != null) hasErrors = true

        _validationErrors.value = errors
        return !hasErrors
    }

    private fun validateDimensionsError(rows: Int, cols: Int, context: Context?): String? {
        if (rows > 0 && cols > 0 && rows <= FieldConfig.MAX_ROWS && cols <= FieldConfig.MAX_COLS) {
            val totalPlots = rows * cols
            if (totalPlots > FieldConfig.MAX_TOTAL_PLOTS) {
                return context?.getString(
                    R.string.field_creator_total_plots_exceeded_error,
                    FieldConfig.MAX_TOTAL_PLOTS.formatted(),
                    totalPlots.formatted()
                )
            }
        }
        return null
    }

    fun createField(db: DataHelper, context: Context?) {
        val state = _fieldConfig.value ?: return

        // cancel any existing job
        createFieldJob?.cancel()

        createFieldJob = viewModelScope.launch {
            _creationResult.value = FieldCreationResult.Loading

            try {
                val studyDbId = withContext(Dispatchers.IO) {
                    db.open()
                    DataHelper.db.beginTransaction()
                    try {
                        // Create the field object
                        val field = FieldObject().apply {
                            uniqueId = "plot_id"
                            primaryId = "Row"
                            secondaryId = "Column"
                            sortColumnsStringArray = "Plot"
                            name = state.fieldName
                            alias = state.fieldName
                            dataSource = context?.getString(R.string.field_book)
                            dataSourceFormat = ImportFormat.INTERNAL
                            entryCount = (state.rows * state.cols).toString()
                            startCorner = state.startCorner?.name
                            walkingDirection = if (state.isHorizontal == true) "horizontal" else "vertical"
                            walkingPattern = when (state.isZigzag) {
                                true -> "serpentine"
                                false -> "linear"
                                else -> null
                            }
                        }

                        val fieldColumns = listOf(
                            "Row",
                            "Column",
                            "Plot",
                            "plot_id",
                            "position_coordinate_x_type",
                            "position_coordinate_y_type",
                            "position_coordinate_x",
                            "position_coordinate_y"
                        )

                        val studyDbId = db.createField(field, fieldColumns, false)

                        // Insert plot data
                        insertPlotData(db, studyDbId, fieldColumns, state)

                        DataHelper.db.setTransactionSuccessful()
                        studyDbId
                    } catch (e: Exception) {
                        DataHelper.db.endTransaction()
                        throw e
                    } finally {
                        if (DataHelper.db.inTransaction()) {
                            DataHelper.db.endTransaction()
                        }
                    }
                }

                _creationResult.value = FieldCreationResult.Success(studyDbId)
            } catch (e: Exception) {
                _creationResult.value = FieldCreationResult.Error(
                    e.message ?: context?.getString(R.string.field_creation_failed_error) ?: ""
                )
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        createFieldJob?.cancel()
    }
}

data class FieldConfig(
    val fieldName: String = "",
    val rows: Int = 0,
    val cols: Int = 0,
    val startCorner: FieldStartCorner? = null,
    val isZigzag: Boolean? = null,
    val isHorizontal: Boolean? = null
) {
    val pattern: FieldWalkingPattern?
        get() = when {
            isHorizontal == null || isZigzag == null -> null
            isHorizontal && !isZigzag -> FieldWalkingPattern.HORIZONTAL_LINEAR
            isHorizontal && isZigzag -> FieldWalkingPattern.HORIZONTAL_ZIGZAG
            !isHorizontal && !isZigzag -> FieldWalkingPattern.VERTICAL_LINEAR
            else -> FieldWalkingPattern.VERTICAL_ZIGZAG
        }

    val totalPlots: Int
        get() = rows * cols

    val isLargeField: Boolean
        get() = totalPlots > LARGE_FIELD_THRESHOLD

    companion object {
        const val LARGE_FIELD_THRESHOLD = 2500
        const val MAX_ROWS = 10000
        const val MAX_COLS = 10000
        const val MAX_TOTAL_PLOTS = 100000

        fun Int.formatted(): String {
            return NumberFormat.getNumberInstance(Locale.getDefault()).format(this)
        }
    }
}

sealed class FieldCreationResult {
    data class Success(val studyDbId: Int) : FieldCreationResult()
    data class Error(val message: String) : FieldCreationResult()
    object Loading : FieldCreationResult()
}

data class ValidationError(
    val fieldNameError: String? = null,
    val rowsError: String? = null,
    val colsError: String? = null,
    val dimensionsError: String? = null
)

private fun insertPlotData(db: DataHelper, studyDbId: Int, fieldColumns: List<String>, config: FieldConfig) {
    var plotIndex = 0
    val pattern = config.pattern ?: return
    val rows = config.rows
    val cols = config.cols

    when (pattern) {
        FieldWalkingPattern.HORIZONTAL_LINEAR, FieldWalkingPattern.HORIZONTAL_ZIGZAG -> {
            var ltr = true
            for (i in 1..rows) { // outer: rows
                for (j in if (ltr) 1..cols else cols downTo 1) { // inner: cols, L→R or R→L
                    plotIndex++
                    insertSinglePlot(db, studyDbId, fieldColumns, i, j, plotIndex)
                }
                // flip the direction before iterating over columns again
                if (pattern == FieldWalkingPattern.HORIZONTAL_ZIGZAG) {
                    ltr = !ltr
                }
            }
        }
        FieldWalkingPattern.VERTICAL_LINEAR, FieldWalkingPattern.VERTICAL_ZIGZAG -> {
            var topToBottom = true
            for (j in 1..cols) { // outer: cols
                for (i in if (topToBottom) 1..rows else rows downTo 1) { // inner: rows, T→B or B→T
                    plotIndex++
                    insertSinglePlot(db, studyDbId, fieldColumns, i, j, plotIndex)
                }
                // flip the direction before iterating over columns again
                if (pattern == FieldWalkingPattern.VERTICAL_ZIGZAG) {
                    topToBottom = !topToBottom
                }
            }
        }
    }
}

private fun insertSinglePlot(db: DataHelper, studyDbId: Int, fieldColumns: List<String>,
    row: Int, col: Int, plotIndex: Int
) {
    val uuid = java.util.UUID.randomUUID().toString()
    val (posX, posY) = row to col

    val values = listOf(row.toString(), col.toString(), plotIndex.toString(),
        uuid, "x_coordinate", "y_coordinate", posX.toString(), posY.toString())

    db.createFieldData(studyDbId, fieldColumns, values)
}