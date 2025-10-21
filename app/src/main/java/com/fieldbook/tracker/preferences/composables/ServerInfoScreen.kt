package com.fieldbook.tracker.preferences.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.utilities.BrapiModuleCalls
import com.fieldbook.tracker.utilities.CallImplementedBy
import com.fieldbook.tracker.utilities.ServiceComparison
import com.fieldbook.tracker.viewmodels.BrapiServerInfoViewModel.ServerInfoUiState

@Composable
fun ServerInfoScreen(uiState: ServerInfoUiState) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (uiState.isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center)
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                item {
                    ServerInfoCard(
                        serverName = uiState.serverName,
                        organizationName = uiState.organizationName,
                        serverDescription = uiState.serverDescription
                    )
                }

                // section title
                if (uiState.modulesMap.values.toList().isNotEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.brapi_supported_calls_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // module cards
                items(uiState.modulesMap.values.toList()) { module ->
                    ModuleCard(moduleInfo = module)
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ServerInfoScreenPreview() {
    MaterialTheme {
        ServerInfoScreen(
            uiState = ServerInfoUiState(
                isLoading = false,
                serverName = "Test Server",
                organizationName = "Breeding Insight",
                serverDescription = "A description for the server",
                modulesMap = mapOf<String, BrapiModuleCalls>(
                    "core" to BrapiModuleCalls(
                        moduleName = "Core",
                        calls = listOf(
                            ServiceComparison(
                                service = "studies",
                                methods = listOf("GET", "POST"),
                                isFbImplemented = true,
                                implementedMethods = listOf("GET"),
                                source = CallImplementedBy.SERVER_AND_FIELD_BOOK
                            ),
                            ServiceComparison(
                                service = "programs",
                                methods = listOf("GET"),
                                isFbImplemented = true,
                                implementedMethods = listOf("GET"),
                                source = CallImplementedBy.SERVER_AND_FIELD_BOOK
                            ),
                            ServiceComparison(
                                service = "trials",
                                methods = listOf("GET", "POST"),
                                isFbImplemented = false,
                                implementedMethods = emptyList(),
                                source = CallImplementedBy.SERVER
                            )
                        ),
                        fbImplementedCount = 2,
                        totalCalls = 3
                    ),
                ),
                errorMessage = null,
                isBrapiV1Incompatible = false
            )
        )
    }
}
