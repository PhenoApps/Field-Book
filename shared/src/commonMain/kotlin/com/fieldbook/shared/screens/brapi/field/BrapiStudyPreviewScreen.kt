package com.fieldbook.shared.screens.brapi.field

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.brapi_base_url_default
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrapiStudyPreviewScreen(
    importViewModel: BrapiFieldImportViewModel,
    onBack: () -> Unit,
    onMissingStudy: () -> Unit,
    onImportComplete: () -> Unit,
    viewModel: BrapiStudyPreviewScreenViewModel = viewModel(
        factory = brapiStudyPreviewScreenViewModelFactory()
    ),
) {
    val selectedStudy by importViewModel.selectedStudy.collectAsState()
    val state by viewModel.uiState.collectAsState()
    val defaultBrapiBaseUrl = stringResource(Res.string.brapi_base_url_default)
    val snackbarHostState = remember { SnackbarHostState() }

    val study = selectedStudy
    if (study == null) {
        LaunchedEffect(Unit) {
            onMissingStudy()
        }
        return
    }

    LaunchedEffect(study.studyDbId, defaultBrapiBaseUrl) {
        viewModel.loadStudy(study, defaultBrapiBaseUrl)
    }

    LaunchedEffect(viewModel) {
        viewModel.importCompleted.collect {
            onImportComplete()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TopAppBar(
                    title = { Text("Study Preview") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = study.studyName ?: study.studyDbId,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Text(text = "Study ID: ${study.studyDbId}")
                    study.trialName?.takeIf { it.isNotBlank() }?.let { Text(text = "Trial: $it") }
                    study.locationName?.takeIf { it.isNotBlank() }?.let { Text(text = "Location: $it") }
                    study.studyDescription?.takeIf { it.isNotBlank() }?.let { Text(text = it) }
                    Text(text = "Observation units: ${state.observationUnits.size}")
                }

                HorizontalDivider()

                if (state.loadingUnits) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.unitError?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Traits (${state.traits.size})",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    TextButton(
                        enabled = !state.importing && !state.loadingTraits && !state.loadingUnits,
                        onClick = { viewModel.loadStudy(study, defaultBrapiBaseUrl) },
                    ) {
                        Text("Reload")
                    }
                }

                if (state.loadingTraits) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }

                state.traitError?.let { message ->
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(state.traits, key = { it.observationVariableDbId }) { trait ->
                        ListItem(
                            headlineContent = { Text(trait.observationVariableName) },
                            supportingContent = { Text(trait.format) },
                        )
                        HorizontalDivider()
                    }
                }

                Button(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    enabled = !state.importing &&
                        !state.loadingTraits &&
                        !state.loadingUnits &&
                        state.observationUnits.isNotEmpty(),
                    onClick = { viewModel.importStudy(study, defaultBrapiBaseUrl) },
                ) {
                    Text(if (state.importing) "Saving..." else "Save")
                }
            }
        }
    }
}
