package com.fieldbook.tracker.preferences.composables

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.utilities.BrapiModuleCalls
import com.fieldbook.tracker.utilities.CallImplementedBy
import com.fieldbook.tracker.utilities.ServiceComparison

@Composable
fun ModuleCard(
    moduleInfo: BrapiModuleCalls,
    initiallyExpanded: Boolean = false,
) {
    var isExpanded by remember { mutableStateOf(initiallyExpanded) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RectangleShape
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = moduleInfo.moduleName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            R.string.brapi_server_calls_implemented,
                            moduleInfo.fbImplementedCount,
                            moduleInfo.totalCalls,
                            moduleInfo.implementationPercentage
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = stringResource(
                        if (isExpanded) R.string.brapi_server_collapse_content_description
                        else R.string.brapi_server_expand_content_description
                    ),
                    modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(16.dp))
                ServerCallsTable(calls = moduleInfo.calls)
            }
        }
    }
}

@Preview
@Composable
private fun ModuleCardWithTablePreview() {
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
        ),
        initiallyExpanded = true
    )
}