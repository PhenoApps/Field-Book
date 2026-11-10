package com.fieldbook.tracker.preferences.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.BrapiModuleCalls
import com.fieldbook.tracker.utilities.CallComparison
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
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    ServerInfoCard(
                        serverName = uiState.serverName,
                        organizationName = uiState.organizationName,
                        serverDescription = uiState.serverDescription
                    )
                }

                // field book compatibility, one card per purpose (import, upload)
                // shows serverSupported / totalFBCalls as %
                items(uiState.appCompatibility) { compatibility ->
                    ModuleCard(moduleInfo = compatibility)
                }

                // section title
                if (uiState.modulesMap.values.toList().isNotEmpty()) {
                    item {
                        Text(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            text = stringResource(R.string.brapi_compatibility_all_calls),
                            style = AppTheme.typography.subheadingStyle,
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
    AppTheme {
        ServerInfoScreen(
            uiState = ServerInfoUiState(
                isLoading = false,
                serverName = "Test Server",
                organizationName = "Breeding Insight",
                serverDescription = "A description for the server",
                modulesMap = mapOf(
                    "core" to BrapiModuleCalls(
                        moduleName = "Core",
                        calls = listOf(
                            CallComparison("programs", "GET", supportedByServer = true, supportedByApp = true),
                            CallComparison("studies", "GET", supportedByServer = true, supportedByApp = true),
                            CallComparison("studies", "POST", supportedByServer = true, supportedByApp = false),
                        ),
                        supportedCount = 2,
                        totalCalls = 3
                    ),
                ),
                appCompatibility = listOf(
                    BrapiModuleCalls(
                        moduleName = "Upload from Field Book",
                        calls = listOf(
                            CallComparison("observations", "POST", supportedByServer = true, supportedByApp = true),
                            CallComparison("observations", "PUT", supportedByServer = false, supportedByApp = true),
                        ),
                        supportedCount = 1,
                        totalCalls = 2,
                        isAppCompatibility = true
                    )
                ),
                errorMessage = null,
                isBrapiV1Incompatible = false
            )
        )
    }
}
