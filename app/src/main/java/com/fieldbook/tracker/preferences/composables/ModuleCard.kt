package com.fieldbook.tracker.preferences.composables

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.components.widgets.CollapsibleSection
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.BrapiModuleCalls
import com.fieldbook.tracker.utilities.CallComparison

@Composable
fun ModuleCard(moduleInfo: BrapiModuleCalls) {
    CollapsibleSection(
        title = moduleInfo.moduleName,
        initiallyExpanded = false,
        headerContent = {
            Text(
                text = stringResource(
                    // Field Book's own cards count what the server supports, module cards count
                    // what Field Book makes of the server's calls
                    if (moduleInfo.isAppCompatibility) {
                        R.string.brapi_server_calls_supported
                    } else {
                        R.string.brapi_server_calls_implemented
                    },
                    moduleInfo.supportedCount,
                    moduleInfo.totalCalls,
                    moduleInfo.implementationPercentage
                ),
                style = AppTheme.typography.bodyStyle,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    ) {
        ServerCallsTable(calls = moduleInfo.calls)
    }
}

@Preview(showBackground = true)
@Composable
private fun ModuleCardWithTablePreview() {
    AppTheme {
        ModuleCard(
            moduleInfo = BrapiModuleCalls(
                moduleName = "Module",
                calls = listOf(
                    CallComparison("observations", "GET", supportedByServer = true, supportedByApp = true),
                    CallComparison("observations", "POST", supportedByServer = true, supportedByApp = true),
                    CallComparison("studies", "POST", supportedByServer = true, supportedByApp = false),
                ),
                supportedCount = 2,
                totalCalls = 3
            )
        )
    }
}
