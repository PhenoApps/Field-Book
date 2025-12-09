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
import com.fieldbook.tracker.utilities.CallImplementedBy
import com.fieldbook.tracker.utilities.ServiceComparison

@Composable
fun ModuleCard(moduleInfo: BrapiModuleCalls) {
    CollapsibleSection(
        title = moduleInfo.moduleName,
        initiallyExpanded = true,
        headerContent = {
            Text(
                text = stringResource(
                    R.string.brapi_server_calls_implemented,
                    moduleInfo.fbImplementedCount,
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
                    ServiceComparison(
                        service = "studies",
                        methods = listOf("GET", "POST"),
                        isFbImplemented = true,
                        implementedMethods = listOf("GET"),
                        source = CallImplementedBy.SERVER_AND_FIELD_BOOK
                    ),
                    ServiceComparison(
                        service = "observations",
                        methods = listOf("GET", "POST", "PUT"),
                        isFbImplemented = true,
                        implementedMethods = listOf("GET", "POST", "PUT"),
                        source = CallImplementedBy.SERVER_AND_FIELD_BOOK
                    ),
                ),
                fbImplementedCount = 1,
                totalCalls = 2
            )
        )
    }
}