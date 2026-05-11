package com.fieldbook.shared.screens.brapi

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.brapi.BrAPIService
import com.fieldbook.shared.brapi.BrAPIServiceFactory
import com.fieldbook.shared.brapi.BrapiPaginationManager
import com.fieldbook.shared.brapi.BrapiResult
import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.brapi_base_url_default
import com.fieldbook.shared.generated.resources.brapi_load_data_button
import com.fieldbook.shared.generated.resources.brapi_studies_error
import com.fieldbook.shared.generated.resources.brapi_studies_filter_title
import com.fieldbook.shared.generated.resources.fragment_summary_next_button_text
import com.fieldbook.shared.generated.resources.fragment_summary_prev_button_text
import com.fieldbook.shared.preferences.PreferenceKeys
import com.russhwolf.settings.Settings
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrapiStudyScreen(
    onBack: (() -> Unit)? = null,
    onStudySelected: ((BrapiStudyDetails) -> Unit)? = null,
) {
    val preferences = remember { Settings() }
    val defaultBaseUrl = stringResource(Res.string.brapi_base_url_default)
    val brapiVersion = remember {
        preferences.getString(PreferenceKeys.BRAPI_VERSION, BrAPIServiceFactory.VERSION_V2)
    }
    val paginationManager = remember { BrapiPaginationManager.fromSettings(preferences) }
    val service = remember(defaultBaseUrl, brapiVersion) {
        buildBrapiService(
            baseUrl = preferences.getString(PreferenceKeys.BRAPI_BASE_URL, defaultBaseUrl),
            bearerToken = preferences.getStringOrNull(PreferenceKeys.BRAPI_TOKEN),
            brapiVersion = brapiVersion,
        )
    }
    val coroutineScope = rememberCoroutineScope()

    var studies by remember { mutableStateOf<List<BrapiStudyDetails>>(emptyList()) }
    var selectedStudyDbId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var pageVersion by remember { mutableIntStateOf(0) }
    val pageLabel = remember(pageVersion) { paginationManager.pageLabel }
    val canMovePrevious = remember(pageVersion) { paginationManager.canMovePrevious }
    val canMoveNext = remember(pageVersion) { paginationManager.canMoveNext }

    fun loadStudies() {
        coroutineScope.launch {
            isLoading = true
            errorMessage = null

            when (val result = service.getStudies(paginationManager = paginationManager)) {
                is BrapiResult.Success -> {
                    studies = result.value
                    selectedStudyDbId = null
                    pageVersion++
                }

                is BrapiResult.Failure -> {
                    errorMessage = result.message ?: result.statusCode?.let { "HTTP $it" }
                        ?: "Unable to load studies."
                }
            }

            isLoading = false
        }
    }

    LaunchedEffect(service) {
        loadStudies()
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(text = stringResource(Res.string.brapi_studies_filter_title)) },
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

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    enabled = !isLoading,
                    onClick = {
                        paginationManager.reset()
                        pageVersion++
                        loadStudies()
                    }
                ) {
                    Text(text = stringResource(Res.string.brapi_load_data_button))
                }

                Text(
                    text = pageLabel,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            errorMessage?.let { message ->
                Text(
                    text = "${stringResource(Res.string.brapi_studies_error)} $message",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(studies, key = { it.studyDbId }) { study ->
                    ListItem(
                        modifier = Modifier.clickable { selectedStudyDbId = study.studyDbId },
                        headlineContent = { Text(text = study.studyName ?: study.studyDbId) },
                        supportingContent = {
                            val detail = listOfNotNull(study.locationName, study.trialName)
                                .joinToString(" - ")
                            if (detail.isNotBlank()) {
                                Text(text = detail)
                            }
                        },
                        trailingContent = {
                            if (selectedStudyDbId == study.studyDbId) {
                                Text(
                                    text = "Selected",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.labelMedium,
                                )
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (onStudySelected != null) {
                    Button(
                        enabled = !isLoading && selectedStudyDbId != null,
                        onClick = {
                            studies.firstOrNull { it.studyDbId == selectedStudyDbId }
                                ?.let(onStudySelected)
                        },
                    ) {
                        Text("Import")
                    }
                }

                OutlinedButton(
                    enabled = !isLoading && canMovePrevious,
                    onClick = {
                        paginationManager.previousPage()
                        pageVersion++
                        loadStudies()
                    },
                ) {
                    Text(text = stringResource(Res.string.fragment_summary_prev_button_text))
                }

                OutlinedButton(
                    enabled = !isLoading && canMoveNext,
                    onClick = {
                        paginationManager.nextPage()
                        pageVersion++
                        loadStudies()
                    },
                ) {
                    Text(text = stringResource(Res.string.fragment_summary_next_button_text))
                }
            }
        }
    }
}

private fun buildBrapiService(
    baseUrl: String,
    bearerToken: String?,
    brapiVersion: String?,
): BrAPIService {
    return BrAPIServiceFactory.create(
        baseUrl = baseUrl,
        bearerToken = bearerToken,
        version = brapiVersion,
    )
}
