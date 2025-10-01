package com.fieldbook.shared.screens.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.database.utils.DATABASE_NAME
import com.fieldbook.shared.database.utils.importDatabaseFromBundled
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.database_export
import com.fieldbook.shared.generated.resources.database_import
import com.fieldbook.shared.generated.resources.database_reset
import com.fieldbook.shared.generated.resources.ic_pref_database_delete
import com.fieldbook.shared.generated.resources.ic_pref_database_export
import com.fieldbook.shared.generated.resources.ic_pref_database_import
import com.fieldbook.shared.generated.resources.ic_pref_general_root_directory
import com.fieldbook.shared.generated.resources.preferences_storage_database_title
import com.fieldbook.shared.generated.resources.preferences_storage_files_base_directory_description
import com.fieldbook.shared.generated.resources.preferences_storage_files_base_directory_title
import com.fieldbook.shared.generated.resources.preferences_storage_storage_title
import com.fieldbook.shared.generated.resources.preferences_storage_title
import com.fieldbook.shared.sqldelight.DriverFactory
import com.fieldbook.shared.theme.MainTheme
import com.fieldbook.shared.utilities.selectFirstField
import kotlinx.coroutines.launch
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
    driverFactory: DriverFactory,
    onBack: (() -> Unit)? = null
) {
    MainTheme {
        var showImportDialog by remember { mutableStateOf(false) }
        var isImporting by remember { mutableStateOf(false) }
        var importResult by remember { mutableStateOf<String?>(null) }
        var showSuccessSnackbar by remember { mutableStateOf(false) }
        val coroutineScope = rememberCoroutineScope()
        val snackbarHostState = remember { SnackbarHostState() }

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
                                .padding(16.dp),
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
                                item.summary?.let { summaryRes ->
                                    Text(
                                        text = stringResource(summaryRes),
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
                        val isImport = item.key == "pref_database_import"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                                .let { mod ->
                                    if (isImport) mod.clickable { showImportDialog = true } else mod
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
                            importResult = null
                        },
                        title = { Text(text = stringResource(Res.string.database_import)) },
                        text = {
                            Column {
                                if (!isImporting && importResult == null) {
                                    TextButton(
                                        onClick = {
                                            coroutineScope.launch {
                                                isImporting = true
                                                importResult = null
                                                try {
                                                    importDatabaseFromBundled(
                                                        driverFactory,
                                                        DATABASE_NAME
                                                    )
                                                    selectFirstField(driverFactory)
                                                    showImportDialog = false
                                                    showSuccessSnackbar = true
                                                } catch (e: Exception) {
                                                    importResult =
                                                        "Failed to import sample database: ${'$'}{e.message}"
                                                } finally {
                                                    isImporting = false
                                                }
                                            }
                                        },
                                        enabled = !isImporting
                                    ) {
                                        Text("sample_db")
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    showImportDialog = false
                                    isImporting = false
                                    importResult = null
                                }
                            ) {
                                Text("Cancel")
                            }
                        }
                    )
                }
                if (showSuccessSnackbar) {
                    LaunchedEffect(showSuccessSnackbar) {
                        snackbarHostState.showSnackbar("Sample database imported successfully.")
                        showSuccessSnackbar = false
                    }
                }
                SnackbarHost(hostState = snackbarHostState)
            }
        }
    }
}
