package com.fieldbook.shared.screens.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.database_dialog_title
import com.fieldbook.shared.generated.resources.database_export
import com.fieldbook.shared.generated.resources.database_export_invalid_filename
import com.fieldbook.shared.generated.resources.database_import
import com.fieldbook.shared.generated.resources.database_reset
import com.fieldbook.shared.generated.resources.database_reset_warning1
import com.fieldbook.shared.generated.resources.database_reset_warning2
import com.fieldbook.shared.generated.resources.dialog_cancel
import com.fieldbook.shared.generated.resources.dialog_delete
import com.fieldbook.shared.generated.resources.dialog_no
import com.fieldbook.shared.generated.resources.dialog_save
import com.fieldbook.shared.generated.resources.dialog_warning
import com.fieldbook.shared.generated.resources.dialog_yes
import com.fieldbook.shared.generated.resources.export_complete
import com.fieldbook.shared.generated.resources.export_error_general
import com.fieldbook.shared.generated.resources.ic_pref_database_delete
import com.fieldbook.shared.generated.resources.ic_pref_database_export
import com.fieldbook.shared.generated.resources.ic_pref_database_import
import com.fieldbook.shared.generated.resources.ic_pref_general_root_directory
import com.fieldbook.shared.generated.resources.import_dialog_importing
import com.fieldbook.shared.generated.resources.import_error_general
import com.fieldbook.shared.generated.resources.preferences_storage_database_title
import com.fieldbook.shared.generated.resources.preferences_storage_files_base_directory_description
import com.fieldbook.shared.generated.resources.preferences_storage_files_base_directory_title
import com.fieldbook.shared.generated.resources.preferences_storage_storage_title
import com.fieldbook.shared.generated.resources.preferences_storage_title
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.theme.AlertDialog
import com.fieldbook.shared.theme.TextButton
import com.fieldbook.shared.utilities.DatabaseImportResult
import com.fieldbook.shared.utilities.DocumentFile
import com.fieldbook.shared.utilities.availableDatabaseImportFiles
import com.fieldbook.shared.utilities.defaultDatabaseExportFileName
import com.fieldbook.shared.utilities.displayStorageDirectoryPath
import com.fieldbook.shared.utilities.exportDatabaseZip
import com.fieldbook.shared.utilities.importDatabaseFile
import com.fieldbook.shared.utilities.resetLocalDatabaseAndPreferences
import com.fieldbook.shared.utilities.shareFile
import com.russhwolf.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private data class StoragePreferenceItem(
    val icon: DrawableResource,
    val title: StringResource,
    val summary: StringResource? = null,
    val key: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoragePreferencesScreen(
    onBack: (() -> Unit)? = null,
    onNavigate: ((com.fieldbook.shared.KmpHostScreenType) -> Unit)? = null,
    onExit: (() -> Unit)? = null,
    onSnackbarMessage: (String) -> Unit,
) {
    var showImportDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showDeleteWarning1 by remember { mutableStateOf(false) }
    var showDeleteWarning2 by remember { mutableStateOf(false) }
    var isImporting by remember { mutableStateOf(false) }
    var isExporting by remember { mutableStateOf(false) }
    var exportFileName by remember { mutableStateOf("") }
    var importFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    val coroutineScope = rememberCoroutineScope()
    val settings = remember { Settings() }
    val storageDirectorySummary = displayStorageDirectoryPath(
        settings.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY.key, ""),
        settings.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_PROVIDER_TYPE.key, ""),
        settings.getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_PROVIDER_LABEL.key, "")
    )
    val deleteFailureMessage = "Failed to delete database."
    val exportCompleteMessage = stringResource(Res.string.export_complete)
    val exportErrorMessage = stringResource(Res.string.export_error_general)
    val importErrorMessage = stringResource(Res.string.import_error_general)
    val invalidExportFileNameMessage = stringResource(Res.string.database_export_invalid_filename)

    val storageItems = listOf(
        StoragePreferenceItem(
            icon = Res.drawable.ic_pref_general_root_directory,
            title = Res.string.preferences_storage_files_base_directory_title,
            summary = Res.string.preferences_storage_files_base_directory_description,
            key = "DEFAULT_STORAGE_LOCATION_PREFERENCE"
        )
    )
    val databaseItems = listOf(
        StoragePreferenceItem(
            icon = Res.drawable.ic_pref_database_import,
            title = Res.string.database_import,
            key = "pref_database_import"
        ),
        StoragePreferenceItem(
            icon = Res.drawable.ic_pref_database_export,
            title = Res.string.database_export,
            key = "pref_database_export"
        ),
        StoragePreferenceItem(
            icon = Res.drawable.ic_pref_database_delete,
            title = Res.string.database_reset,
            key = "pref_database_delete"
        )
    )
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.preferences_storage_title)) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Text(
                            text = stringResource(Res.string.preferences_storage_storage_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(storageItems) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clickable {
                                    if (item.key == "DEFAULT_STORAGE_LOCATION_PREFERENCE") {
                                        onNavigate?.invoke(com.fieldbook.shared.KmpHostScreenType.STORAGE_DEFINER)
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(item.icon),
                                contentDescription = item.key,
                                modifier = Modifier.padding(end = 16.dp).size(24.dp)
                            )
                            Column {
                                Text(
                                    text = stringResource(item.title),
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                val summaryText = when {
                                    item.key == "DEFAULT_STORAGE_LOCATION_PREFERENCE" &&
                                        storageDirectorySummary.isNotBlank() -> storageDirectorySummary
                                    item.summary != null -> stringResource(item.summary)
                                    else -> null
                                }

                                summaryText?.let { summary ->
                                    Text(
                                        text = summary,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        Divider()
                    }
                    item {
                        Text(
                            text = stringResource(Res.string.preferences_storage_database_title),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                    items(databaseItems) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .clickable {
                                    when (item.key) {
                                        "pref_database_import" -> {
                                            importFiles = availableDatabaseImportFiles()
                                            showImportDialog = true
                                        }
                                        "pref_database_export" -> {
                                            exportFileName = defaultDatabaseExportFileName()
                                            showExportDialog = true
                                        }
                                        "pref_database_delete" -> showDeleteWarning1 = true
                                    }
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                painter = painterResource(item.icon),
                                contentDescription = item.key,
                                modifier = Modifier.padding(end = 16.dp).size(24.dp)
                            )
                            Text(
                                text = stringResource(item.title),
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                        Divider()
                    }
            }
            if (showImportDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showImportDialog = false
                        isImporting = false
                    },
                    title = { Text(text = stringResource(Res.string.database_import)) },
                    text = {
                        when {
                            isImporting -> Text(stringResource(Res.string.import_dialog_importing))
                            importFiles.isEmpty() -> Text("No database backups found.")
                            else -> {
                                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                                    items(importFiles) { file ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable(enabled = !isImporting) {
                                                    coroutineScope.launch {
                                                        isImporting = true
                                                        try {
                                                            val result = withContext(Dispatchers.Default) {
                                                                importDatabaseFile(file)
                                                            }

                                                            when (result) {
                                                                DatabaseImportResult.Success -> {
                                                                    showImportDialog = false
                                                                    onSnackbarMessage("Database imported successfully.")
                                                                }

                                                                DatabaseImportResult.NoDatabaseFile,
                                                                DatabaseImportResult.UnsupportedFile -> {
                                                                    onSnackbarMessage(importErrorMessage)
                                                                }
                                                            }
                                                        } catch (_: Exception) {
                                                            onSnackbarMessage(importErrorMessage)
                                                        } finally {
                                                            isImporting = false
                                                        }
                                                    }
                                                }
                                                .padding(vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = file.name().orEmpty(),
                                                style = MaterialTheme.typography.bodyLarge
                                            )
                                        }
                                        Divider()
                                    }
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                showImportDialog = false
                                isImporting = false
                            }
                        ) {
                            Text(stringResource(Res.string.dialog_cancel))
                        }
                    }
                )
            }
            if (showDeleteWarning1) {
                AlertDialog(
                    onDismissRequest = { showDeleteWarning1 = false },
                    title = { Text(stringResource(Res.string.dialog_warning)) },
                    titleContentColor = MaterialTheme.colorScheme.error,
                    text = { Text(stringResource(Res.string.database_reset_warning1)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDeleteWarning1 = false
                                showDeleteWarning2 = true
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError
                            )
                        ) {
                            Text(stringResource(Res.string.dialog_delete))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteWarning1 = false }) {
                            Text(stringResource(Res.string.dialog_cancel))
                        }
                    }
                )
            }
            if (showDeleteWarning2) {
                AlertDialog(
                    onDismissRequest = { showDeleteWarning2 = false },
                    title = { Text(stringResource(Res.string.dialog_warning)) },
                    text = { Text(stringResource(Res.string.database_reset_warning2)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                showDeleteWarning2 = false
                                coroutineScope.launch {
                                    val resetSucceeded = runCatching {
                                        resetLocalDatabaseAndPreferences()
                                    }.getOrDefault(false)

                                    if (resetSucceeded) {
                                        onExit?.invoke() ?: onBack?.invoke()
                                    } else {
                                        onSnackbarMessage(deleteFailureMessage)
                                    }
                                }
                            }
                        ) {
                            Text(stringResource(Res.string.dialog_yes))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteWarning2 = false }) {
                            Text(stringResource(Res.string.dialog_no))
                        }
                    }
                )
            }
        }
    }

    if (showExportDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!isExporting) {
                    showExportDialog = false
                }
            },
            title = { Text(stringResource(Res.string.database_dialog_title)) },
            text = {
                OutlinedTextField(
                    value = exportFileName,
                    onValueChange = { exportFileName = it },
                    enabled = !isExporting,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val requestedFileName = exportFileName.trim()
                        if (requestedFileName.isBlank()) {
                            onSnackbarMessage(invalidExportFileNameMessage)
                            return@Button
                        }

                        coroutineScope.launch {
                            isExporting = true
                            try {
                                runCatching {
                                    withContext(Dispatchers.Default) {
                                        exportDatabaseZip(requestedFileName)
                                    }
                                }.onSuccess { exportedFile ->
                                    showExportDialog = false
                                    shareFile(exportedFile)
                                    onSnackbarMessage(exportCompleteMessage)
                                }.onFailure {
                                    onSnackbarMessage(exportErrorMessage)
                                }
                            } finally {
                                isExporting = false
                            }
                        }
                    },
                    enabled = !isExporting
                ) {
                    Text(stringResource(Res.string.dialog_save))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExportDialog = false },
                    enabled = !isExporting
                ) {
                    Text(stringResource(Res.string.dialog_cancel))
                }
            }
        )
    }
}
