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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.preferences_storage_files_base_directory_title
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.theme.MainTheme
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.compose.rememberDirectoryPickerLauncher
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageDefinerScreen(
    onBack: (() -> Unit)? = null
) {
    MainTheme {
        val preferences: Settings = Settings()
        var currentDirectory by remember {
            mutableStateOf(
                preferences.getString(
                    GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY.key,
                    ""
                )
            )
        }

        val launcher = rememberDirectoryPickerLauncher(
            title = "Directory picker",
            initialDirectory = currentDirectory.ifEmpty { null }
        ) { directory ->
            directory?.let {
                val value = it.path ?: ""
                preferences.putString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY.key, value)
                currentDirectory = value
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
                    text = "Current directory: $currentDirectory",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(16.dp)
                )
                Button(onClick = { launcher.launch() }, modifier = Modifier.padding(16.dp)) {
                    Text("Choose Directory")
                }
            }
        }
    }
}
