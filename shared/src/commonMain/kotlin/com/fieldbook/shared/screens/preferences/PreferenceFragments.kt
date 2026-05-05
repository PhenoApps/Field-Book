package com.fieldbook.shared.screens.preferences

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

enum class PreferenceDialogType {
    TEXT,
    OPTIONS,
    INFO
}

data class PreferenceItem(
    val icon: DrawableResource,
    val title: StringResource,
    val summary: StringResource? = null,
    val value: String? = null,
    val dialogTitle: StringResource = title,
    val dialogType: PreferenceDialogType,
    val options: List<String> = emptyList(),
    val isDestructive: Boolean = false
)

data class PreferenceSection(
    val title: StringResource,
    val items: List<PreferenceItem>
)

data class PreferenceDialogState(
    val title: String,
    val summary: String? = null,
    val type: PreferenceDialogType,
    val options: List<String> = emptyList(),
    val value: String = "",
    val onSave: (String) -> Unit = {},
    val onOptionSelected: (String) -> Unit = {},
    val extraButtonText: String? = null,
    val onExtraButtonClick: (String) -> Unit = {}
)

@Composable
fun PreferenceToggleRow(
    title: String,
    summary: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}

@Composable
fun PreferenceRow(
    item: PreferenceItem,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(item.icon),
            contentDescription = stringResource(item.title),
            modifier = Modifier
                .padding(end = 16.dp)
                .size(24.dp),
            tint = if (item.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(item.title),
                style = MaterialTheme.typography.bodyLarge,
                color = if (item.isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            item.value?.takeIf { it.isNotEmpty() }?.let { value ->
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } ?: item.summary?.let { summary ->
                Text(
                    text = stringResource(summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun PreferenceTextDialog(
    state: PreferenceDialogState,
    onDismiss: () -> Unit
) {
    var text by remember(state.value) { mutableStateOf(state.value) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = state.title) },
        text = {
            Column {
                state.summary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    state.onSave(text)
                    onDismiss()
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            Row {
                state.extraButtonText?.let { buttonText ->
                    TextButton(
                        onClick = {
                            state.onSave(text)
                            onDismiss()
                            state.onExtraButtonClick(text)
                        }
                    ) {
                        Text(buttonText)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}

@Composable
fun PreferenceOptionsDialog(
    state: PreferenceDialogState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = state.title) },
        text = {
            Column {
                state.summary?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }
                state.options.forEach { option ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                state.onOptionSelected(option)
                                onDismiss()
                            }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == state.value,
                            onClick = {
                                state.onOptionSelected(option)
                                onDismiss()
                            }
                        )
                        Text(
                            text = option,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 8.dp)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun PreferenceInfoDialog(
    state: PreferenceDialogState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = state.title) },
        text = {
            Text(
                text = state.summary ?: "",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
