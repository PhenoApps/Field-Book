package com.fieldbook.shared.screens.preferences

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.preferences_storage_files_base_directory_title
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.utilities.configureAndPersistStorageDirectory
import com.fieldbook.shared.utilities.displayStorageDirectoryPath
import com.fieldbook.shared.utilities.normalizeStorageDirectoryPath
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.compose.rememberDirectoryPickerLauncher
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageDefinerScreen(
    onBack: (() -> Unit)? = null
) {
    val preferences: Settings = Settings()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var currentDirectory by remember {
        mutableStateOf(
            normalizeStorageDirectoryPath(
                preferences.getString(
                    GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY.key,
                    ""
                )
            )
        )
    }
    var currentProviderType by remember {
        mutableStateOf(
            preferences.getString(
                GeneralKeys.DEFAULT_STORAGE_LOCATION_PROVIDER_TYPE.key,
                ""
            )
        )
    }
    var currentProviderLabel by remember {
        mutableStateOf(
            preferences.getString(
                GeneralKeys.DEFAULT_STORAGE_LOCATION_PROVIDER_LABEL.key,
                ""
            )
        )
    }

    val launcher = rememberDirectoryPickerLauncher(
        title = "Directory picker"
    ) { directory ->
        directory?.let {
            coroutineScope.launch {
                configureAndPersistStorageDirectory(it, preferences)
                    .onSuccess { result ->
                        currentDirectory = result.configuredDirectory
                        currentProviderType = result.providerTypeName
                        currentProviderLabel = result.providerLabel
                        if (result.sampleSeedFailed) {
                            snackbarHostState.showSnackbar("Storage configured, but sample files could not be prepared.")
                        }
                    }
                    .onFailure {
                        snackbarHostState.showSnackbar("Failed to configure the selected folder.")
                    }
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(
                            Res.string.preferences_storage_files_base_directory_title
                        )
                    )
                },
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
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
            Text(
                text = "Current directory: ${
                    displayStorageDirectoryPath(
                        currentDirectory,
                        currentProviderType,
                        currentProviderLabel
                    )
                }",
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(16.dp)
            )
            Button(onClick = { launcher.launch() }, modifier = Modifier.padding(16.dp)) {
                Text("Choose Directory")
            }
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
    }
}
