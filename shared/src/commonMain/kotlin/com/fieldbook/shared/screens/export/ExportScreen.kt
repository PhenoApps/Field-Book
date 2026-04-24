package com.fieldbook.shared.screens.export

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.dialog_cancel
import com.fieldbook.shared.generated.resources.dialog_export_bundle_data_cb_text
import com.fieldbook.shared.generated.resources.dialog_save
import com.fieldbook.shared.generated.resources.export_content_columns_all
import com.fieldbook.shared.generated.resources.export_content_columns_title
import com.fieldbook.shared.generated.resources.export_content_columns_unique
import com.fieldbook.shared.generated.resources.export_content_traits_active
import com.fieldbook.shared.generated.resources.export_content_traits_all
import com.fieldbook.shared.generated.resources.export_file_name
import com.fieldbook.shared.generated.resources.export_format_database
import com.fieldbook.shared.generated.resources.export_format_table
import com.fieldbook.shared.generated.resources.export_multiple_fields_message
import com.fieldbook.shared.generated.resources.export_overwrite
import com.fieldbook.shared.generated.resources.export_progress
import com.fieldbook.shared.generated.resources.settings_export
import com.fieldbook.shared.generated.resources.settings_traits
import com.fieldbook.shared.generated.resources.traits_create_format
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.compose.resources.stringResource

@Composable
fun ExportScreen(
    fieldIds: List<Int>,
    viewModel: ExportScreenViewModel = viewModel(
        factory = exportScreenViewModelFactory()
    ),
    onBack: () -> Unit
) {
    val uiState = viewModel.uiState.collectAsState().value
    var isExporting by remember { mutableStateOf(false) }
    var dialogMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadDefaults(fieldIds)
    }

    LaunchedEffect(viewModel) {
        viewModel.events.collectLatest { event ->
            when (event) {
                is ExportEvent.ShowMessage -> dialogMessage = event.message
                ExportEvent.ShowProgress -> {
                    dialogMessage = null
                    isExporting = true
                }
                is ExportEvent.Completed -> {
                    isExporting = false
                    onBack()
                }
                is ExportEvent.Failed -> {
                    isExporting = false
                    dialogMessage = event.message
                }
            }
        }
    }

    val content: @Composable () -> Unit = {
        ExportContent(
            uiState = uiState,
            onToggleFormatDb = viewModel::onToggleFormatDb,
            onToggleFormatTable = viewModel::onToggleFormatTable,
            onSelectOnlyUnique = viewModel::onSelectOnlyUnique,
            onSelectAllColumns = viewModel::onSelectAllColumns,
            onSelectActiveTraits = viewModel::onSelectActiveTraits,
            onSelectAllTraits = viewModel::onSelectAllTraits,
            onToggleBundle = viewModel::onToggleBundle,
            onToggleOverwrite = viewModel::onToggleOverwrite,
            onFileNameChange = viewModel::onFileNameChange,
            onDismiss = onBack,
            onSave = { viewModel.onSave(fieldIds) }
        )
    }

    Dialog(
        onDismissRequest = onBack,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.88f)
                .widthIn(max = 420.dp),
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp
        ) {
            content()
        }
    }

    if (isExporting) {
        Dialog(
            onDismissRequest = {},
            properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
        ) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(stringResource(Res.string.export_progress))
                }
            }
        }
    }

    dialogMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { dialogMessage = null },
            title = { Text(stringResource(Res.string.settings_export)) },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { dialogMessage = null }) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
            }
        )
    }
}

@Composable
private fun ExportContent(
    uiState: ExportUiState,
    onToggleFormatDb: () -> Unit,
    onToggleFormatTable: () -> Unit,
    onSelectOnlyUnique: () -> Unit,
    onSelectAllColumns: () -> Unit,
    onSelectActiveTraits: () -> Unit,
    onSelectAllTraits: () -> Unit,
    onToggleBundle: () -> Unit,
    onToggleOverwrite: () -> Unit,
    onFileNameChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 20.dp)
    ) {
        Text(
            text = stringResource(Res.string.settings_export),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.height(12.dp))

        SectionTitle(stringResource(Res.string.traits_create_format))
        CheckboxOption(
            label = stringResource(Res.string.export_format_database),
            checked = uiState.formatDb,
            onCheckedChange = onToggleFormatDb
        )
        CheckboxOption(
            label = stringResource(Res.string.export_format_table),
            checked = uiState.formatTable,
            onCheckedChange = onToggleFormatTable
        )

        Spacer(modifier = Modifier.height(14.dp))

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val stackSections = maxWidth < 360.dp

            if (stackSections) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    RadioSection(
                        title = stringResource(Res.string.export_content_columns_title),
                        options = listOf(
                            RadioOption(
                                label = stringResource(Res.string.export_content_columns_unique),
                                selected = uiState.onlyUnique,
                                onClick = onSelectOnlyUnique
                            ),
                            RadioOption(
                                label = stringResource(Res.string.export_content_columns_all),
                                selected = uiState.allColumns,
                                onClick = onSelectAllColumns
                            )
                        )
                    )
                    RadioSection(
                        title = stringResource(Res.string.settings_traits),
                        options = listOf(
                            RadioOption(
                                label = stringResource(Res.string.export_content_traits_active),
                                selected = uiState.activeTraits,
                                onClick = onSelectActiveTraits
                            ),
                            RadioOption(
                                label = stringResource(Res.string.export_content_traits_all),
                                selected = uiState.allTraits,
                                onClick = onSelectAllTraits
                            )
                        )
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    RadioSection(
                        title = stringResource(Res.string.export_content_columns_title),
                        options = listOf(
                            RadioOption(
                                label = stringResource(Res.string.export_content_columns_unique),
                                selected = uiState.onlyUnique,
                                onClick = onSelectOnlyUnique
                            ),
                            RadioOption(
                                label = stringResource(Res.string.export_content_columns_all),
                                selected = uiState.allColumns,
                                onClick = onSelectAllColumns
                            )
                        ),
                        modifier = Modifier.weight(1f)
                    )
                    RadioSection(
                        title = stringResource(Res.string.settings_traits),
                        options = listOf(
                            RadioOption(
                                label = stringResource(Res.string.export_content_traits_active),
                                selected = uiState.activeTraits,
                                onClick = onSelectActiveTraits
                            ),
                            RadioOption(
                                label = stringResource(Res.string.export_content_traits_all),
                                selected = uiState.allTraits,
                                onClick = onSelectAllTraits
                            )
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        CheckboxOption(
            label = stringResource(Res.string.dialog_export_bundle_data_cb_text),
            checked = uiState.bundleMedia,
            onCheckedChange = onToggleBundle
        )

        Spacer(modifier = Modifier.height(14.dp))

        SectionTitle(stringResource(Res.string.export_file_name))
        FilenameField(
            value = uiState.fileName,
            onValueChange = onFileNameChange
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (uiState.multipleFields) {
            Text(
                text = stringResource(Res.string.export_multiple_fields_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            CheckboxOption(
                label = stringResource(Res.string.export_overwrite),
                checked = uiState.overwrite,
                onCheckedChange = onToggleOverwrite
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_cancel))
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onSave) {
                Text(stringResource(Res.string.dialog_save))
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun CheckboxOption(
    label: String,
    checked: Boolean,
    onCheckedChange: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCheckedChange)
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onCheckedChange() }
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

private data class RadioOption(
    val label: String,
    val selected: Boolean,
    val onClick: () -> Unit
)

@Composable
private fun RadioSection(
    title: String,
    options: List<RadioOption>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        SectionTitle(title)
        Spacer(modifier = Modifier.height(2.dp))
        options.forEach { option ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = option.onClick)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.Top
            ) {
                RadioButton(
                    selected = option.selected,
                    onClick = option.onClick,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Text(
                    text = option.label,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}

@Composable
private fun FilenameField(
    value: String,
    onValueChange: (String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = MaterialTheme.colorScheme.onSurface
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.primary)
    }
}
