package com.fieldbook.shared.screens.fields

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fieldbook.shared.database.models.FieldObject
import com.fieldbook.shared.database.repository.StudyRepository
import com.fieldbook.shared.database.utils.internalTimeFormatter
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.dialog_field_creator_ask_pattern
import com.fieldbook.shared.generated.resources.dialog_field_creator_ask_size
import com.fieldbook.shared.generated.resources.dialog_field_creator_ask_start_point
import com.fieldbook.shared.generated.resources.dialog_field_creator_bottom_left
import com.fieldbook.shared.generated.resources.dialog_field_creator_bottom_right
import com.fieldbook.shared.generated.resources.dialog_field_creator_insert_field
import com.fieldbook.shared.generated.resources.dialog_field_creator_review_title
import com.fieldbook.shared.generated.resources.dialog_field_creator_top_left
import com.fieldbook.shared.generated.resources.dialog_field_creator_top_right
import com.fieldbook.shared.generated.resources.field_book
import com.fieldbook.shared.generated.resources.ic_plot_pattern_linear
import com.fieldbook.shared.generated.resources.ic_plot_pattern_zigzag
import com.fieldbook.shared.objects.ImportFormat
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.sqldelight.createDatabase
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.format
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private enum class FieldCreatorStep {
    SizeGroup,
    StartPoint,
    PatternGroup,
    ReviewGroup
}

@Composable
fun FieldCreatorDialogFragment(
    viewModel: FieldCreatorDialogFragmentViewModel = viewModel {
        FieldCreatorDialogFragmentViewModel()
    },
    onDismiss: () -> Unit
) {
    var currentStep by remember { mutableStateOf(FieldCreatorStep.SizeGroup) }
    var name by remember { mutableStateOf("") }
    var rows by remember { mutableStateOf("") }
    var columns by remember { mutableStateOf("") }
    var selectedPattern by remember { mutableStateOf<FieldPattern?>(null) }
    var selectedStartPoint by remember { mutableStateOf<String?>(null) }

    when (currentStep) {
        FieldCreatorStep.SizeGroup -> {
            SizeGroupDialog(
                name = name,
                onNameChange = { name = it },
                rows = rows,
                onRowsChange = { rows = it },
                columns = columns,
                onColumnsChange = { columns = it },
                onDismiss = onDismiss,
                onNext = {
                    currentStep = FieldCreatorStep.StartPoint
                }
            )
        }

        FieldCreatorStep.StartPoint -> {
            StartPointDialog(
                selected = selectedStartPoint,
                onSelected = { selectedStartPoint = it },
                onDismiss = onDismiss,
                onBack = { currentStep = FieldCreatorStep.SizeGroup },
                onNext = {
                    if (selectedStartPoint != null) currentStep = FieldCreatorStep.PatternGroup
                }
            )
        }

        FieldCreatorStep.PatternGroup -> {
            PatternGroupDialog(
                onDismiss = onDismiss,
                onBack = { currentStep = FieldCreatorStep.StartPoint },
                selectedPattern = selectedPattern,
                onPatternSelected = { pattern ->
                    selectedPattern = pattern
                    currentStep = FieldCreatorStep.ReviewGroup
                },
                startPoint = selectedStartPoint
            )
        }

        FieldCreatorStep.ReviewGroup -> {
            ReviewGroupDialog(
                onDismiss = onDismiss,
                onBack = { currentStep = FieldCreatorStep.PatternGroup },
                name = name,
                rows = rows,
                columns = columns,
                pattern = selectedPattern,
                startPoint = selectedStartPoint,
                viewModel
            )
        }
    }
}

@Composable
private fun SizeGroupDialog(
    name: String,
    onNameChange: (String) -> Unit,
    rows: String,
    onRowsChange: (String) -> Unit,
    columns: String,
    onColumnsChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onNext: () -> Unit
) {
    val rowsInt = rows.toIntOrNull()
    val columnsInt = columns.toIntOrNull()
    val isRowsError = rows.isNotBlank() && (rowsInt == null || rowsInt <= 0)
    val isColumnsError = columns.isNotBlank() && (columnsInt == null || columnsInt <= 0)
    val isNextEnabled = name.isNotBlank() && !isRowsError && !isColumnsError

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(Res.string.dialog_field_creator_ask_size)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = rows,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() } || newValue.isEmpty()) {
                            onRowsChange(newValue)
                        }
                    },
                    label = { Text("Rows") },
                    isError = isRowsError,
                    supportingText = {
                        if (isRowsError) Text("Enter a valid positive number")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = columns,
                    onValueChange = { newValue ->
                        if (newValue.all { it.isDigit() } || newValue.isEmpty()) {
                            onColumnsChange(newValue)
                        }
                    },
                    label = { Text("Columns") },
                    isError = isColumnsError,
                    supportingText = {
                        if (isColumnsError) Text("Enter a valid positive number")
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onNext,
                enabled = isNextEnabled
            ) {
                Text("Next")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun StartPointDialog(
    selected: String?,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit
) {
    val options = listOf(
        Res.string.dialog_field_creator_top_left,
        Res.string.dialog_field_creator_top_right,
        Res.string.dialog_field_creator_bottom_left,
        Res.string.dialog_field_creator_bottom_right
    )
    val optionKeys = listOf(
        "top_left", "top_right", "bottom_left", "bottom_right"
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_field_creator_ask_start_point)) },
        text = {
            Column {
                for (row in 0..1) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        for (col in 0..1) {
                            val idx = row * 2 + col
                            val key = optionKeys[idx]
                            val label = stringResource(options[idx])
                            Row(
                                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        onSelected(key)
                                        onNext()
                                    }
                            ) {
                                RadioButton(
                                    selected = selected == key,
                                    onClick = { onSelected(key) }
                                )
                                Text(
                                    label,
                                    modifier = Modifier
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            Row {
                TextButton(onClick = onBack) {
                    Text("Back")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PatternGroupDialog(
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    selectedPattern: FieldPattern?,
    onPatternSelected: (FieldPattern) -> Unit,
    startPoint: String?
) {
    val patterns = listOf(
        FieldPattern.LINEAR to Res.drawable.ic_plot_pattern_linear,
        FieldPattern.ZIGZAG to Res.drawable.ic_plot_pattern_zigzag,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_field_creator_ask_pattern)) },
        text = {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                patterns.forEach { (pattern, drawableRes) ->
                    Button(
                        onClick = {
                            onPatternSelected(pattern)
                        },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                            contentColor = MaterialTheme.colorScheme.primary
                        ),
                        shape = MaterialTheme.shapes.small
                    ) {
                        val (scaleX, scaleY) = getPatternScales(pattern, startPoint)
                        Image(
                            painter = painterResource(drawableRes),
                            contentDescription = pattern.name,
                            modifier = Modifier.size(32.dp).graphicsLayer(
                                scaleX = scaleX,
                                scaleY = scaleY
                            )
                        )
                    }
                }
            }
        },
        confirmButton = { },
        dismissButton = {
            Row {
                TextButton(onClick = onBack) {
                    Text("Back")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
private fun ReviewGroupDialog(
    onDismiss: () -> Unit,
    onBack: () -> Unit,
    name: String,
    rows: String,
    columns: String,
    pattern: FieldPattern?,
    startPoint: String?,
    viewModel: FieldCreatorDialogFragmentViewModel,
) {
    val patternIcon = when (pattern) {
        FieldPattern.LINEAR -> Res.drawable.ic_plot_pattern_linear
        FieldPattern.ZIGZAG -> Res.drawable.ic_plot_pattern_zigzag
        else -> null
    }
    val db = createDatabase()
    val coroutineScope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.dialog_field_creator_review_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (patternIcon != null) {
                    val (scaleX, scaleY) = getPatternScales(pattern, startPoint)
                    Image(
                        painter = painterResource(patternIcon),
                        contentDescription = pattern?.name,
                        modifier = Modifier.size(48.dp).graphicsLayer(
                            scaleX = scaleX,
                            scaleY = scaleY
                        )
                    )
                }
                Row {
                    Text(
                        stringResource(
                            Res.string.dialog_field_creator_insert_field,
                            rows,
                            columns
                        )
                    )
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                coroutineScope.launch {
                    viewModel.insertBasicField(
                        name,
                        rows.toIntOrNull() ?: 0,
                        columns.toIntOrNull() ?: 0,
                        pattern,
                        onDismiss
                    )
                }
            }) {
                Text("Ok")
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onBack) {
                    Text("Back")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

class FieldCreatorDialogFragmentViewModel : ViewModel() {
    val db: FieldbookDatabase = createDatabase()
    val studyRepository = StudyRepository(db)

    suspend fun insertBasicField(
        name: String,
        rows: Int,
        cols: Int,
        pattern: FieldPattern?,
        onDismiss: () -> Unit
    ) {
        val source = getString(Res.string.field_book)

        db.transaction {
            val field = FieldObject().apply {
                unique_id = "plot_id"
                primary_id = "Row"
                secondary_id = "Column"
                exp_sort = "Plot"
                exp_name = name
                exp_alias = name
                exp_source = source
                import_format = ImportFormat.INTERNAL.format
                count = (rows * cols).toString()
            }
            val fieldColumns = listOf("Row", "Column", "Plot", "plot_id")

            val timestamp = Clock.System.now().format(internalTimeFormatter)
            val studyId: Int = studyRepository.createField(field, timestamp)

            // TODO
            // updateFieldInsertText(rows.toString(), cols.toString())

            insertPlotData(
                studyId.toLong(),
                fieldColumns,
                rows,
                cols,
                linear = pattern == FieldPattern.LINEAR,
            )
            onDismiss()
        }
    }

    private fun insertPlotData(
        studyId: Long,
        fieldColumns: List<String>, rows: Int, cols: Int,
        linear: Boolean = true, ttb: Boolean = true, ltr: Boolean = true
    ) {

        var direction = ltr
        var plotIndex = 0
        for (i in if (ttb) 1 until rows + 1 else rows downTo 1) {

            for (j in if (direction) 1 until cols + 1 else cols downTo 1) {

                plotIndex += 1

                insertPlotData(studyId, fieldColumns, i, j, plotIndex)

                // TODO
                // if (mCancelJobFlag) throw ...

            }

            // flip the direction before iterating over columns again
            if (!linear) direction = !direction
        }
    }

    /**
     * insert a unique id plot and notify the ui
     */
    @OptIn(ExperimentalUuidApi::class)
    private fun insertPlotData(
        studyId: Long,
        fieldColumns: List<String>, i: Int, j: Int, k: Int,
        uuid: String = Uuid.random().toString()
    ) {
        val row = i.toString()
        val col = j.toString()
        val index = k.toString()

        studyRepository.createFieldData(studyId, fieldColumns, listOf(row, col, index, uuid))

        // updatePlotInsertText(row, col, index)
    }
}


private fun getPatternScales(pattern: FieldPattern?, startPoint: String?): Pair<Float, Float> {
    return when (pattern) {
        FieldPattern.LINEAR -> when (startPoint) {
            "top_right", "bottom_right" -> -1f to -1f
            else -> 1f to 1f
        }

        FieldPattern.ZIGZAG -> when (startPoint) {
            "top_right", "bottom_right" -> {
                if (startPoint == "bottom_right") -1f to -1f else -1f to 1f
            }

            "bottom_left" -> 1f to -1f
            else -> 1f to 1f
        }

        else -> 1f to 1f
    }
}
