package com.fieldbook.shared.screens.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.dialog_cancel
import com.fieldbook.shared.generated.resources.dialog_delete
import com.fieldbook.shared.generated.resources.dialog_save
import com.fieldbook.shared.generated.resources.field_detail_title
import com.fieldbook.shared.generated.resources.field_edit_display_name
import com.fieldbook.shared.generated.resources.field_sort_entries
import com.fieldbook.shared.generated.resources.fields_delete
import com.fieldbook.shared.generated.resources.fields_delete_confirmation
import com.fieldbook.shared.generated.resources.fields_rename_study
import com.fieldbook.shared.generated.resources.ic_dots_grid
import com.fieldbook.shared.generated.resources.ic_field
import com.fieldbook.shared.generated.resources.ic_file_csv
import com.fieldbook.shared.generated.resources.ic_information_outline
import com.fieldbook.shared.generated.resources.ic_rename
import com.fieldbook.shared.generated.resources.ic_sort
import com.fieldbook.shared.generated.resources.ic_tb_barcode
import com.fieldbook.shared.generated.resources.ic_tb_delete
import com.fieldbook.shared.generated.resources.name_cannot_be_empty
import com.fieldbook.shared.generated.resources.name_conflict_display_name
import com.fieldbook.shared.generated.resources.name_conflict_import_name
import com.fieldbook.shared.generated.resources.search_attribute_apply_to_all
import com.fieldbook.shared.generated.resources.search_attribute_dialog_title
import com.fieldbook.shared.objects.ImportFormat
import com.fieldbook.shared.utilities.checkForIllegalCharacters
import com.fieldbook.shared.utilities.relativeTimeText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FieldDetailScreen(
    fieldId: Int,
    viewModel: FieldEditorScreenViewModel,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val field by viewModel.fieldDetail.collectAsState()
    val loading by viewModel.fieldDetailLoading.collectAsState()
    val attributes by viewModel.fieldAttributes.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showRenameDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showSearchDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var searchAttributeOptions by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(fieldId) {
        viewModel.loadFieldDetail(fieldId)
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(showSearchDialog) {
        if (showSearchDialog) {
            searchAttributeOptions = withContext(Dispatchers.Default) {
                viewModel.getPossibleUniqueAttributes(fieldId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.field_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showDeleteDialog = true }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_tb_delete),
                            contentDescription = stringResource(Res.string.fields_delete)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        if (loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator()
            }
        } else if (field == null || field?.exp_id == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text("Field not found.")
            }
        } else {
            val currentField = field!!
            val displayName = currentField.exp_alias.ifBlank { currentField.exp_name }
            val searchId = currentField.search_attribute ?: currentField.unique_id
            val importFormat = ImportFormat.fromString(currentField.import_format)
            val headerIcon =
                if (importFormat == ImportFormat.CSV) Res.drawable.ic_file_csv else Res.drawable.ic_field

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFFF6F5F2))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Icon(
                                    painter = painterResource(headerIcon),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .padding(top = 2.dp, end = 10.dp)
                                        .size(28.dp),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = displayName,
                                        style = MaterialTheme.typography.headlineSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    relativeTimeText(currentField.date_import)?.let { relativeTime ->
                                        Text(
                                            text = relativeTime,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                DetailChip(
                                    icon = headerIcon,
                                    text = currentField.exp_name.takeIf { it.isNotBlank() }
                                        ?: currentField.exp_source.orEmpty()
                                )
                                DetailChip(
                                    icon = Res.drawable.ic_dots_grid,
                                    text = currentField.count.orEmpty()
                                )
                                DetailChip(
                                    icon = Res.drawable.ic_information_outline,
                                    text = currentField.attribute_count.orEmpty()
                                )
                            }

                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                ActionChip(
                                    label = stringResource(Res.string.fields_rename_study),
                                    icon = Res.drawable.ic_rename,
                                    onClick = { showRenameDialog = true }
                                )
                                ActionChip(
                                    label = stringResource(Res.string.field_sort_entries),
                                    icon = Res.drawable.ic_sort,
                                    onClick = { showSortDialog = true }
                                )
                                ActionChip(
                                    label = searchId,
                                    icon = Res.drawable.ic_tb_barcode,
                                    onClick = { showSearchDialog = true }
                                )
                            }
                        }
                    }
                }
            }

            if (showRenameDialog) {
                RenameFieldDialog(
                    initialName = displayName,
                    currentFieldId = currentField.exp_id!!,
                    viewModel = viewModel,
                    onDismiss = { showRenameDialog = false }
                )
            }

            if (showSortDialog) {
                SortFieldDialog(
                    currentSort = currentField.exp_sort,
                    availableAttributes = attributes,
                    onDismiss = { showSortDialog = false },
                    onSave = { newSort ->
                        viewModel.updateFieldSort(currentField.exp_id!!, newSort)
                        showSortDialog = false
                    }
                )
            }

            if (showSearchDialog) {
                SearchAttributeDialog(
                    currentSearchAttribute = searchId,
                    options = searchAttributeOptions,
                    onDismiss = { showSearchDialog = false },
                    onSave = { attribute, applyToAll ->
                        viewModel.updateSearchAttribute(currentField.exp_id!!, attribute, applyToAll)
                        showSearchDialog = false
                    }
                )
            }

            if (showDeleteDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteDialog = false },
                    title = { Text(stringResource(Res.string.fields_delete)) },
                    text = {
                        Text(
                            pluralStringResource(
                                Res.plurals.fields_delete_confirmation,
                                1,
                                displayName
                            )
                        )
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                showDeleteDialog = false
                                viewModel.deleteField(currentField.exp_id!!)
                                onDeleted()
                            }
                        ) {
                            Text(stringResource(Res.string.dialog_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteDialog = false }) {
                            Text(stringResource(Res.string.dialog_cancel))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RenameFieldDialog(
    initialName: String,
    currentFieldId: Int,
    viewModel: FieldEditorScreenViewModel,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember(initialName) { mutableStateOf(initialName) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val emptyNameMessage = stringResource(Res.string.name_cannot_be_empty)
    val importNameConflict = stringResource(Res.string.name_conflict_import_name)
    val displayNameConflict = stringResource(Res.string.name_conflict_display_name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.field_edit_display_name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorMessage = null
                    },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = errorMessage != null
                )
                errorMessage?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val trimmedName = name.trim()
                    val illegalCharacters = checkForIllegalCharacters(trimmedName)
                    when {
                        trimmedName.isEmpty() -> {
                            errorMessage = emptyNameMessage
                        }

                        illegalCharacters.isNotEmpty() -> {
                            errorMessage = "Illegal characters found: $illegalCharacters"
                        }

                        else -> {
                            scope.launch {
                                val result = viewModel.validateFieldName(trimmedName, currentFieldId)
                                if (result.isUnique) {
                                    viewModel.renameField(currentFieldId, trimmedName)
                                    onDismiss()
                                } else {
                                    val conflictType =
                                        if (result.conflictType == "name") importNameConflict else displayNameConflict
                                    errorMessage =
                                        "\"$trimmedName\" is already used as a $conflictType for another field."
                                }
                            }
                        }
                    }
                }
            ) {
                Text(stringResource(Res.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun SortFieldDialog(
    currentSort: String?,
    availableAttributes: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit
) {
    var sortItems by remember(currentSort) {
        mutableStateOf(
            currentSort
                ?.split(',')
                ?.map { it.trim() }
                ?.filter { it.isNotEmpty() }
                .orEmpty()
        )
    }
    var addExpanded by remember { mutableStateOf(false) }
    val remainingAttributes = availableAttributes.filterNot { it in sortItems }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.field_sort_entries)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (sortItems.isEmpty()) {
                    Text(
                        text = "No sort attributes selected.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                } else {
                    sortItems.forEachIndexed { index, attribute ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${index + 1}. $attribute",
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = {
                                    if (index > 0) {
                                        val updated = sortItems.toMutableList()
                                        val temp = updated[index - 1]
                                        updated[index - 1] = updated[index]
                                        updated[index] = temp
                                        sortItems = updated
                                    }
                                }
                            ) {
                                Text("Up")
                            }
                            TextButton(
                                onClick = {
                                    if (index < sortItems.lastIndex) {
                                        val updated = sortItems.toMutableList()
                                        val temp = updated[index + 1]
                                        updated[index + 1] = updated[index]
                                        updated[index] = temp
                                        sortItems = updated
                                    }
                                }
                            ) {
                                Text("Down")
                            }
                            TextButton(onClick = { sortItems = sortItems - attribute }) {
                                Text(stringResource(Res.string.fields_delete))
                            }
                        }
                    }
                }

                if (remainingAttributes.isNotEmpty()) {
                    Column {
                        OutlinedButton(onClick = { addExpanded = true }) {
                            Text("Add attribute")
                        }
                        DropdownMenu(
                            expanded = addExpanded,
                            onDismissRequest = { addExpanded = false }
                        ) {
                            remainingAttributes.forEach { attribute ->
                                DropdownMenuItem(
                                    text = { Text(attribute) },
                                    onClick = {
                                        sortItems = sortItems + attribute
                                        addExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(sortItems) }) {
                Text(stringResource(Res.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun SearchAttributeDialog(
    currentSearchAttribute: String,
    options: List<String>,
    onDismiss: () -> Unit,
    onSave: (String, Boolean) -> Unit
) {
    var selected by remember(currentSearchAttribute, options) {
        mutableStateOf(
            options.firstOrNull { it == currentSearchAttribute } ?: options.firstOrNull().orEmpty()
        )
    }
    var applyToAll by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.search_attribute_dialog_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (options.isEmpty()) {
                    Text("No unique attributes available.")
                } else {
                    LazyColumn(modifier = Modifier.height(220.dp)) {
                        items(options) { option ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selected == option,
                                    onClick = { selected = option }
                                )
                                Text(
                                    text = option,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = applyToAll,
                            onCheckedChange = { applyToAll = it }
                        )
                        Text(
                            text = stringResource(Res.string.search_attribute_apply_to_all),
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = selected.isNotBlank(),
                onClick = { onSave(selected, applyToAll) }
            ) {
                Text(stringResource(Res.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value.ifBlank { "-" },
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun DetailChip(
    icon: org.jetbrains.compose.resources.DrawableResource,
    text: String
) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = {
            Text(
                text = text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = Color(0xFFE2F0C9),
            disabledLabelColor = MaterialTheme.colorScheme.onSurface,
            disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = null
    )
}

@Composable
private fun ActionChip(
    label: String,
    icon: org.jetbrains.compose.resources.DrawableResource,
    onClick: () -> Unit
) {
    AssistChip(
        onClick = onClick,
        label = {
            Text(
                text = label,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = Color.White,
            labelColor = MaterialTheme.colorScheme.onSurface,
            leadingIconContentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = AssistChipDefaults.assistChipBorder(
            enabled = true,
            borderColor = MaterialTheme.colorScheme.primary
        )
    )
}
