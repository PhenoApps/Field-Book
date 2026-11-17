package org.phenoapps.brapi.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.phenoapps.brapi.BrapiAccountConstants
import org.phenoapps.brapi.R

/**
 * One choice in a [RadioPickerField]: [value] is what gets persisted, [label] is what the user
 * sees. Keeping them separate matters for any option whose label is translated — persisting the
 * label would make the stored value change with the app language.
 */
data class PickerOption(val value: String, val label: String)

/**
 * OIDC flow choices, pairing each stable identifier with its localized label. The order matches
 * `pheno_brapi_oidc_flow_options`.
 */
@Composable
fun rememberOidcFlowOptions(): List<PickerOption> {
    val labels = stringArrayResource(R.array.pheno_brapi_oidc_flow_options)
    return remember(labels) {
        listOf(
            PickerOption(BrapiAccountConstants.OIDC_FLOW_OAUTH_CODE, labels[0]),
            PickerOption(BrapiAccountConstants.OIDC_FLOW_OAUTH_IMPLICIT, labels[1]),
        )
    }
}

/** BrAPI version choices. These labels are not translated, so value and label coincide. */
@Composable
fun rememberBrapiVersionOptions(): List<PickerOption> {
    val labels = stringArrayResource(R.array.pheno_brapi_version_options)
    return remember(labels) { labels.map { PickerOption(it, it) } }
}

@Composable
fun RadioPickerField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    options: List<PickerOption>,
    modifier: Modifier = Modifier,
) {
    var showDialog by remember { mutableStateOf(false) }

    val selectedLabel = options.firstOrNull { it.value == value }?.label ?: value

    OutlinedTextField(
        value = selectedLabel,
        onValueChange = {},
        readOnly = true,
        label = { Text(label) },
        trailingIcon = {
            Icon(
                painter = painterResource(R.drawable.pheno_brapi_ic_chevron_down),
                contentDescription = null,
            )
        },
        modifier = modifier.clickable { showDialog = true },
        enabled = false,
        colors = OutlinedTextFieldDefaults.colors(
            disabledTextColor = MaterialTheme.colorScheme.onSurface,
            disabledBorderColor = MaterialTheme.colorScheme.outline,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    )

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(label) },
            text = {
                Column {
                    options.forEach { option ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onValueChange(option.value)
                                    showDialog = false
                                }
                                .padding(vertical = 4.dp),
                        ) {
                            RadioButton(
                                selected = option.value == value,
                                onClick = {
                                    onValueChange(option.value)
                                    showDialog = false
                                },
                            )
                            Text(
                                text = option.label,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.pheno_brapi_dialog_cancel))
                }
            },
        )
    }
}
