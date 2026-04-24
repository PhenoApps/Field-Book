package com.fieldbook.shared.screens.fields

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fieldbook.shared.components.AppListItem
import com.fieldbook.shared.database.models.FieldObject
import com.fieldbook.shared.database.repository.ObservationUnitAttributeRepository
import com.fieldbook.shared.database.repository.StudyRepository
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_field
import com.fieldbook.shared.generated.resources.ic_file_cloud
import com.fieldbook.shared.generated.resources.ic_file_csv
import com.fieldbook.shared.generated.resources.ic_file_generic
import com.fieldbook.shared.generated.resources.tutorial_fields_add_title
import com.fieldbook.shared.objects.ImportFormat
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.utilities.DocumentFile
import com.fieldbook.shared.utilities.FieldSwitchImpl
import com.fieldbook.shared.utilities.getFieldImportDirectory
import com.fieldbook.shared.utilities.listFiles
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.painterResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FieldEditorScreen(
    onBack: (() -> Unit)? = null,
    viewModel: FieldEditorScreenViewModel = viewModel(
        factory = fieldEditorViewModelFactory()
    )
) {
    var showAddFieldDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showLocalFilesDialog by remember { mutableStateOf(false) }
    var showFieldCreatorDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    val selectedFieldId = remember { mutableStateOf<Int?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    var localFieldFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }

    // Observe state from the ViewModel
    val fieldsState by viewModel.fields.collectAsState(initial = null)
    val errorState by viewModel.error.collectAsState(initial = null)
    val loadingState by viewModel.loading.collectAsState(initial = true)
    val pendingImport by viewModel.pendingImport.collectAsState()
    val importing by viewModel.importing.collectAsState()

    val importFilePicker = rememberFilePickerLauncher(
        type = PickerType.File(extensions = listOf("csv")),
        title = "Import field file"
    ) { file ->
        if (file != null) {
            coroutineScope.launch {
                val bytes = file.readBytes()
                saveCloudFieldImportFile(file.name, bytes)
                viewModel.prepareFieldImport(file.name, bytes)
            }
        }
        showImportDialog = false
    }

    if (selectedFieldId.value != null) {
        FieldDetailScreen(
            fieldId = selectedFieldId.value!!,
            viewModel = viewModel,
            onBack = { selectedFieldId.value = null },
            onDeleted = {
                selectedFieldId.value = null
                viewModel.clearFieldDetail()
            }
        )
        return
    }

    // load fields when the screen appears
    LaunchedEffect(Unit) {
        viewModel.loadFields()
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(showLocalFilesDialog) {
        if (showLocalFilesDialog) {
            localFieldFiles = withContext(Dispatchers.Default) { loadLocalFieldFiles() }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fields") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddFieldDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = Res.string.tutorial_fields_add_title.key
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            when {
                loadingState -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                errorState != null -> {
                    Text(
                        text = "Error: ${errorState}",
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.error
                    )
                }

                fieldsState.isNullOrEmpty() -> {
                    Text(
                        text = "No fields found.",
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(fieldsState!!) { field ->
                            FieldListItem(
                                field = field,
                                viewModel = viewModel,
                                onOpenDetails = {
                                    field.exp_id?.let { selectedFieldId.value = it }
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }

            if (showAddFieldDialog) {
                AddFieldDialog(
                    onDismiss = { showAddFieldDialog = false },
                    onCreateNew = {
                        showAddFieldDialog = false
                        showFieldCreatorDialog = true
                    },
                    onImportFromFile = {
                        showAddFieldDialog = false
                        showImportDialog = true
                    }
                )
            }

            if (showImportDialog) {
                FieldImportDialog(
                    importing = importing,
                    onDismiss = { showImportDialog = false },
                    onPickLocal = {
                        showImportDialog = false
                        if (getFieldImportDirectory() != null) {
                            showLocalFilesDialog = true
                        } else {
                            importFilePicker.launch()
                        }
                    },
                    onPickCloud = {
                        showImportDialog = false
                        importFilePicker.launch()
                    }
                )
            }

            if (showLocalFilesDialog) {
                LocalFieldFilesDialog(
                    files = localFieldFiles,
                    importing = importing,
                    onDismiss = { showLocalFilesDialog = false },
                    onImportFile = { file ->
                        showLocalFilesDialog = false
                        coroutineScope.launch {
                            viewModel.prepareFieldImport(
                                file.name().orEmpty(),
                                file.readBytes()
                            )
                        }
                    }
                )
            }

            if (showFieldCreatorDialog) {
                FieldCreatorDialogFragment(
                    onDismiss = { showFieldCreatorDialog = false },
                    onSuccess = { fieldId ->
                        showFieldCreatorDialog = false
                        coroutineScope.launch {
                            viewModel.loadFields()
                            viewModel.switchField(fieldId)
                        }
                    }
                )
            }

            if (pendingImport != null) {
                PendingFieldImportDialog(
                    pendingImport = pendingImport!!,
                    importing = importing,
                    onDismiss = { viewModel.clearPendingImport() },
                    onImport = { uniqueColumn ->
                        viewModel.importPendingField(uniqueColumn)
                    }
                )
            }
        }
    }
}

@Composable
private fun AddFieldDialog(
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit,
    onImportFromFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Field") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppListItem(
                    text = "Create new field",
                    icon = Res.drawable.ic_field,
                    rowModifier = Modifier.clickable(onClick = onCreateNew)
                )
                AppListItem(
                    text = "Import from file",
                    icon = Res.drawable.ic_file_csv,
                    rowModifier = Modifier.clickable(onClick = onImportFromFile)
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FieldImportDialog(
    importing: Boolean,
    onDismiss: () -> Unit,
    onPickLocal: () -> Unit,
    onPickCloud: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!importing) onDismiss()
        },
        title = { Text("Import from file") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (importing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Please wait...")
                    }
                } else {
                    AppListItem(
                        text = "Local storage",
                        icon = Res.drawable.ic_file_generic,
                        rowModifier = Modifier.clickable(onClick = onPickLocal)
                    )
                    AppListItem(
                        text = "Cloud storage",
                        icon = Res.drawable.ic_file_cloud,
                        rowModifier = Modifier.clickable(onClick = onPickCloud)
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !importing) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun LocalFieldFilesDialog(
    files: List<DocumentFile>,
    importing: Boolean,
    onDismiss: () -> Unit,
    onImportFile: (DocumentFile) -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!importing) onDismiss()
        },
        title = { Text("Import") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (importing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Please wait...")
                    }
                } else if (files.isEmpty()) {
                    Text("No field files found")
                } else {
                    files.forEach { file ->
                        AppListItem(
                            text = file.name().orEmpty(),
                            icon = Res.drawable.ic_file_csv,
                            rowModifier = Modifier.clickable { onImportFile(file) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !importing) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun PendingFieldImportDialog(
    pendingImport: PendingFieldImport,
    importing: Boolean,
    onDismiss: () -> Unit,
    onImport: (String) -> Unit
) {
    var selectedColumn by remember(pendingImport) {
        mutableStateOf(pendingImport.uniqueColumnOptions.firstOrNull().orEmpty())
    }

    AlertDialog(
        onDismissRequest = {
            if (!importing) onDismiss()
        },
        title = { Text("Choose unique column") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Field name: ${pendingImport.fieldName}")
                if (pendingImport.sanitizedColumns) {
                    Text("Unsupported header characters were removed.")
                }
                if (pendingImport.duplicateColumnsSkipped) {
                    Text("Duplicate column names will be skipped.")
                }
                pendingImport.uniqueColumnOptions.forEach { column ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = !importing) { selectedColumn = column },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedColumn == column,
                            onClick = { selectedColumn = column },
                            enabled = !importing
                        )
                        Text(column)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onImport(selectedColumn) },
                enabled = !importing && selectedColumn.isNotBlank()
            ) {
                Text("Import")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !importing) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun FieldListItem(
    field: FieldObject,
    viewModel: FieldEditorScreenViewModel,
    onOpenDetails: () -> Unit
) {
    val activeStudyId by viewModel.activeFieldId.collectAsState()
    val importFormat = ImportFormat.fromString(field.import_format)
    val iconRes =
        if (importFormat == ImportFormat.CSV) Res.drawable.ic_file_csv else Res.drawable.ic_field
    val isActive = field.exp_id == activeStudyId

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenDetails)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (isActive) Modifier.border(
                        width = 2.dp,
                        color = MaterialTheme.colorScheme.onSurface,
                        shape = CircleShape
                    ) else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = { viewModel.switchField(field) }
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = "Field Icon",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (field.exp_alias.isNotEmpty()) field.exp_alias else field.exp_name,
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                text = field.exp_name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun loadLocalFieldFiles(): List<DocumentFile> {
    val fieldDir = getFieldImportDirectory() ?: return emptyList()
    return listFiles(fieldDir)
        .filter { !it.isDirectory() && it.name()?.endsWith(".csv", ignoreCase = true) == true }
        .sortedBy { it.name()?.lowercase().orEmpty() }
}

private fun saveCloudFieldImportFile(fileName: String, bytes: ByteArray) {
    val fieldDir = getFieldImportDirectory() ?: return
    val targetName = uniqueFieldFileName(fieldDir, fileName)
    fieldDir.createFile("*/*", targetName)?.writeBytes(bytes)
}

private fun uniqueFieldFileName(directory: DocumentFile, originalName: String): String {
    val existingNames = listFiles(directory).mapNotNull { it.name() }.toSet()
    if (originalName !in existingNames) return originalName

    val dotIndex = originalName.lastIndexOf('.')
    val baseName = if (dotIndex >= 0) originalName.substring(0, dotIndex) else originalName
    val extension = if (dotIndex >= 0) originalName.substring(dotIndex) else ""

    var index = 1
    while (true) {
        val candidate = "${baseName}_$index$extension"
        if (candidate !in existingNames) return candidate
        index++
    }
}

class FieldEditorScreenViewModel(
    private val observationUnitAttributeRepository: ObservationUnitAttributeRepository,
    private val studyRepository: StudyRepository,
    private val fieldSwitchImpl: FieldSwitchImpl = FieldSwitchImpl(
        observationUnitAttributeRepository,
        studyRepository
    ),
    private val settings: Settings = Settings()
) : ViewModel() {
    private val _activeFieldId =
        MutableStateFlow(settings.getInt(GeneralKeys.SELECTED_FIELD_ID.key, 0))
    val activeFieldId: StateFlow<Int> = _activeFieldId.asStateFlow()

    private val _fields = MutableStateFlow<List<FieldObject>?>(null)
    val fields: StateFlow<List<FieldObject>?> = _fields.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _fieldDetail = MutableStateFlow<FieldObject?>(null)
    val fieldDetail: StateFlow<FieldObject?> = _fieldDetail.asStateFlow()

    private val _fieldDetailLoading = MutableStateFlow(false)
    val fieldDetailLoading: StateFlow<Boolean> = _fieldDetailLoading.asStateFlow()

    private val _fieldAttributes = MutableStateFlow<List<String>>(emptyList())
    val fieldAttributes: StateFlow<List<String>> = _fieldAttributes.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private val _importing = MutableStateFlow(false)
    val importing: StateFlow<Boolean> = _importing.asStateFlow()

    private val _pendingImport = MutableStateFlow<PendingFieldImport?>(null)
    val pendingImport: StateFlow<PendingFieldImport?> = _pendingImport.asStateFlow()

    private val _sortAscending = MutableStateFlow(true)
    val sortAscending: StateFlow<Boolean> = _sortAscending.asStateFlow()

    fun switchField(field: FieldObject) {
        fieldSwitchImpl.switchField(field)
        viewModelScope.launch {
            settings.putInt(GeneralKeys.SELECTED_FIELD_ID.key, field.exp_id!!)
            _activeFieldId.value = field.exp_id!!
        }
    }

    fun switchField(fieldId: Int) {
        fieldSwitchImpl.switchField(fieldId)
        viewModelScope.launch {
            settings.putInt(GeneralKeys.SELECTED_FIELD_ID.key, fieldId)
            _activeFieldId.value = fieldId
        }
    }

    fun loadFields() {
        viewModelScope.launch {
            _loading.value = true
            _error.value = null
            try {
                _fields.value = studyRepository.getAllFields()
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = e.message ?: "Unknown error"
                _fields.value = null
            } finally {
                _loading.value = false
            }
        }
    }

    fun loadFieldDetail(fieldId: Int) {
        viewModelScope.launch {
            _fieldDetailLoading.value = true
            try {
                _fieldDetail.value = studyRepository.getById(fieldId)
                _fieldAttributes.value = observationUnitAttributeRepository
                    .getAllNames(fieldId.toLong())
                    .filter { it != "geo_coordinates" }
                _sortAscending.value =
                    settings.getString("${GeneralKeys.SORT_ORDER.key}.$fieldId", "ASC") == "ASC"
            } catch (e: Exception) {
                e.printStackTrace()
                _error.value = e.message ?: "Unknown error"
            } finally {
                _fieldDetailLoading.value = false
            }
        }
    }

    fun clearFieldDetail() {
        _fieldDetail.value = null
        _fieldAttributes.value = emptyList()
    }

    suspend fun validateFieldName(newName: String, currentFieldId: Int): NameCheckResult {
        val conflict = studyRepository.getAllFields().firstOrNull { field ->
            field.exp_id != currentFieldId && (field.exp_name == newName || field.exp_alias == newName)
        }

        return if (conflict == null) {
            NameCheckResult(isUnique = true)
        } else {
            NameCheckResult(
                isUnique = false,
                conflictType = if (conflict.exp_name == newName) "name" else "alias"
            )
        }
    }

    fun renameField(fieldId: Int, newName: String) {
        viewModelScope.launch {
            studyRepository.updateStudyAlias(fieldId, newName)
            refreshFieldData(fieldId)
        }
    }

    fun updateFieldSort(fieldId: Int, sortAttributes: List<String>, ascending: Boolean) {
        viewModelScope.launch {
            studyRepository.updateStudySort(sortAttributes.joinToString(",").ifBlank { null }, fieldId)
            settings.putString("${GeneralKeys.SORT_ORDER.key}.$fieldId", if (ascending) "ASC" else "DESC")
            _sortAscending.value = ascending
            refreshFieldData(fieldId)
            _messages.emit("Sorting updated")
        }
    }

    fun getPossibleUniqueAttributes(fieldId: Int): List<String> {
        return studyRepository.getPossibleUniqueAttributes(fieldId)
    }

    fun updateSearchAttribute(fieldId: Int, attribute: String, applyToAll: Boolean) {
        viewModelScope.launch {
            if (applyToAll) {
                val updatedCount = studyRepository.updateSearchAttributeForAllFields(attribute)
                _messages.emit("Search attribute updated for $updatedCount field(s)")
            } else {
                studyRepository.updateSearchAttribute(fieldId, attribute)
                _messages.emit("Search attribute updated")
            }
            refreshFieldData(fieldId)
        }
    }

    fun deleteField(fieldId: Int) {
        viewModelScope.launch {
            studyRepository.deleteField(fieldId)
            if (_activeFieldId.value == fieldId) {
                val remainingFields = studyRepository.getAllFields()
                val nextFieldId = remainingFields.firstOrNull()?.exp_id ?: 0
                settings.putInt(GeneralKeys.SELECTED_FIELD_ID.key, nextFieldId)
                _activeFieldId.value = nextFieldId
            }
            clearFieldDetail()
            loadFields()
        }
    }

    fun prepareFieldImport(fileName: String, bytes: ByteArray) {
        viewModelScope.launch {
            _pendingImport.value = null
            try {
                _pendingImport.value = FieldImportSupport.parseCsvImport(fileName, bytes)
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Error preparing field import")
            }
        }
    }

    fun clearPendingImport() {
        _pendingImport.value = null
    }

    fun importPendingField(uniqueColumn: String) {
        val pending = _pendingImport.value ?: return
        viewModelScope.launch {
            _importing.value = true
            try {
                val result = FieldImportSupport.importPending(
                    pending = pending,
                    uniqueColumn = uniqueColumn,
                    studyRepository = studyRepository
                )

                fieldSwitchImpl.switchField(result.fieldId)
                settings.putInt(GeneralKeys.SELECTED_FIELD_ID.key, result.fieldId)
                _activeFieldId.value = result.fieldId
                _pendingImport.value = null
                _fields.value = studyRepository.getAllFields()

                if (pending.sanitizedColumns) {
                    _messages.emit("Unsupported header characters were removed")
                }
                if (pending.duplicateColumnsSkipped) {
                    _messages.emit("Duplicate columns were skipped")
                }
                _messages.emit("Imported ${result.importedRowCount} row(s)")
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Error importing field")
            } finally {
                _importing.value = false
            }
        }
    }

    private suspend fun refreshFieldData(fieldId: Int) {
        _fieldDetail.value = studyRepository.getById(fieldId)
        _fields.value = studyRepository.getAllFields()
    }

    data class NameCheckResult(val isUnique: Boolean, val conflictType: String? = null)
}

fun fieldEditorViewModelFactory() = viewModelFactory {
    initializer {
        FieldEditorScreenViewModel(
            observationUnitAttributeRepository = ObservationUnitAttributeRepository(),
            studyRepository = StudyRepository()
        )
    }
}
