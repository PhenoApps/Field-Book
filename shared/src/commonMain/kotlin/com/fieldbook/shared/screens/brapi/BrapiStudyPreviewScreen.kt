package com.fieldbook.shared.screens.brapi

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
import androidx.compose.material3.Surface
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
import com.fieldbook.shared.brapi.BrAPIService
import com.fieldbook.shared.brapi.BrapiResult
import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationUnitDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiTraitDetails
import com.fieldbook.shared.preferences.PreferenceKeys
import com.russhwolf.settings.Settings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrapiStudyPreviewScreen(
    study: BrapiStudyDetails,
    service: BrAPIService,
    onBack: () -> Unit,
    onSave: (BrapiStudyDetails, List<BrapiObservationUnitDetails>, List<BrapiTraitDetails>) -> Unit,
) {
    val preferences = remember { Settings() }
    val pageSize = remember {
        preferences.getString(PreferenceKeys.BRAPI_PAGE_SIZE, "50").toIntOrNull() ?: 50
    }
    val coroutineScope = rememberCoroutineScope()

    var traits by remember(study.studyDbId) { mutableStateOf<List<BrapiTraitDetails>>(emptyList()) }
    var observationUnits by remember(study.studyDbId) {
        mutableStateOf<List<BrapiObservationUnitDetails>>(emptyList())
    }
    var loadingTraits by remember(study.studyDbId) { mutableStateOf(false) }
    var loadingUnits by remember(study.studyDbId) { mutableStateOf(false) }
    var traitError by remember(study.studyDbId) { mutableStateOf<String?>(null) }
    var unitError by remember(study.studyDbId) { mutableStateOf<String?>(null) }

    fun loadTraits() {
        coroutineScope.launch {
            loadingTraits = true
            traitError = null

            when (val result = service.getStudyTraits(study.studyDbId, pageSize)) {
                is BrapiResult.Success -> traits = result.value
                is BrapiResult.Failure -> {
                    traitError = result.message ?: result.statusCode?.let { "HTTP $it" }
                        ?: "Unable to load traits."
                }
            }

            loadingTraits = false
        }
    }

    fun loadObservationUnits() {
        coroutineScope.launch {
            loadingUnits = true
            unitError = null

            when (val result = service.getStudyObservationUnits(study.studyDbId, pageSize)) {
                is BrapiResult.Success -> observationUnits = result.value
                is BrapiResult.Failure -> {
                    unitError = result.message ?: result.statusCode?.let { "HTTP $it" }
                        ?: "Unable to load observation units."
                }
            }

            loadingUnits = false
        }
    }

    LaunchedEffect(study.studyDbId) {
        loadTraits()
        loadObservationUnits()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
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
                Text(text = "Observation units: ${observationUnits.size}")
            }

            HorizontalDivider()

            if (loadingUnits) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            unitError?.let { message ->
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
                    text = "Traits (${traits.size})",
                    style = MaterialTheme.typography.titleMedium,
                )
                TextButton(
                    enabled = !loadingTraits && !loadingUnits,
                    onClick = {
                        loadTraits()
                        loadObservationUnits()
                    },
                ) {
                    Text("Reload")
                }
            }

            if (loadingTraits) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            traitError?.let { message ->
                Text(
                    text = message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(traits, key = { it.observationVariableDbId }) { trait ->
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
                enabled = !loadingTraits && !loadingUnits && observationUnits.isNotEmpty(),
                onClick = { onSave(study, observationUnits, traits) },
            ) {
                Text("Save")
            }
        }
    }
}
