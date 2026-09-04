package com.fieldbook.tracker.printing

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import org.phenoapps.labelprint.service.LabelPrintManager
import org.phenoapps.labelprint.ui.ZplPreviewCanvas
import org.phenoapps.labelprint.zpl.ZplLabel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelPrintMainView(
    onPrintClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onEditClick: () -> Unit = {},
    onConnectClick: () -> Unit = {},
    isPrinterConnected: Boolean = true,
    copiesCount: Int,
    previewLabel: ZplLabel? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ZplPreviewCanvas(
            label = previewLabel,
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .padding(horizontal = 8.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onSettingsClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_configure),
                    contentDescription = "Label settings",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            IconButton(
                onClick = if (isPrinterConnected) onPrintClick else onConnectClick,
                modifier = Modifier
                    .size(76.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            ) {
                if (!isPrinterConnected) {
                    Icon(
                        painter = painterResource(id = R.drawable.connection),
                        contentDescription = stringResource(R.string.label_config_connect_printer),
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                } else if (copiesCount > 1) {
                    BadgedBox(
                        badge = { Badge { Text(text = copiesCount.toString()) } }
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_trait_labelprint),
                            contentDescription = "Print $copiesCount labels",
                            modifier = Modifier.size(28.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_trait_labelprint),
                        contentDescription = "Print label",
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            IconButton(
                onClick = onEditClick,
                modifier = Modifier
                    .size(40.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit label fields",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    }
}

@Composable
fun LabelPrintConfigDialog(
    currentCopies: String,
    copiesOptions: List<String>,
    onConfirm: (copies: String) -> Unit,
    onDismiss: () -> Unit,
    onConnectClick: () -> Unit = {},
    onCalibrate: (() -> Unit)? = null
) {
    var localCopies by remember { mutableStateOf(currentCopies) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(text = stringResource(R.string.label_config_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CopiesSelector(
                    options = copiesOptions,
                    selected = localCopies,
                    onSelect = { localCopies = it }
                )

                if (onCalibrate != null) {
                    OutlinedButton(
                        onClick = onCalibrate,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.label_config_calibrate))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(onClick = onConnectClick) {
                        Icon(
                            painter = painterResource(id = R.drawable.connection),
                            contentDescription = "Connect to printer"
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(localCopies)
            }) {
                Text(stringResource(R.string.dialog_ok))
            }
        },
        dismissButton = { }
    )
}

@Composable
fun LabelFieldAssignmentDialog(
    templateZpl: String,
    currentAssignments: Map<String, String>,
    onFieldClick: (placeholder: String) -> Unit,
    onConfirm: (assignments: Map<String, String>) -> Unit,
    onDismiss: () -> Unit
) {
    val placeholders = remember(templateZpl) {
        LabelPrintManager.extractPlaceholders(templateZpl)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.label_fields_title)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                placeholders.forEach { placeholder ->
                    LabelFieldSelector(
                        label = placeholder,
                        selected = currentAssignments[placeholder] ?: "",
                        onClick = { onFieldClick(placeholder) }
                    )
                }
                if (placeholders.isEmpty()) {
                    Text(
                        text = stringResource(R.string.label_fields_no_fields),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(currentAssignments)
            }) {
                Text(stringResource(R.string.dialog_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}

@Composable
fun LabelFieldSelector(
    label: String,
    selected: String,
    onClick: () -> Unit
) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = selected.ifEmpty { stringResource(R.string.label_fields_select) },
                maxLines = 1
            )
        }
    }
}

@Composable
fun CopiesSelector(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Column {
        Text(
            text = stringResource(R.string.label_config_copies),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Box {
            OutlinedButton(
                onClick = { expanded = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = selected.ifEmpty { "1" },
                    maxLines = 1
                )
            }
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(MaterialTheme.colorScheme.surface)
            ) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(text = option) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}
