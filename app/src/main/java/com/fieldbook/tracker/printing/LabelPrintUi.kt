package com.fieldbook.tracker.printing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import org.phenoapps.labelprint.ui.LabelPrintContrastTheme
import org.phenoapps.labelprint.ui.ZplPreviewCanvas
import org.phenoapps.labelprint.zpl.ZplLabel

/**
 * @param onPreviewClick opens the label field assignments, null when the template has no fields
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelPrintMainView(
    onPrintClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onConnectClick: () -> Unit = {},
    onPreviewClick: (() -> Unit)? = null,
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
                .clickable(
                    enabled = onPreviewClick != null,
                    onClickLabel = stringResource(R.string.label_fields_title),
                    onClick = { onPreviewClick?.invoke() }
                )
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

            // Balanced spacer to keep the print button centered (matches settings button + spacer)
            Spacer(modifier = Modifier.width(16.dp + 40.dp))
        }
    }
}

/**
 * @param onFieldsClick opens the label field assignments, null when the template has no fields
 */
@Composable
fun LabelPrintConfigDialog(
    currentCopies: Int,
    maxCopies: Int,
    onConfirm: (copies: Int) -> Unit,
    onDismiss: () -> Unit,
    isPrinterConnected: Boolean = false,
    onConnectClick: () -> Unit = {},
    onDisconnectClick: (() -> Unit)? = null,
    onCalibrate: (() -> Unit)? = null,
    onFieldsClick: (() -> Unit)? = null
) {
    var localCopies by remember { mutableIntStateOf(currentCopies.coerceIn(1, maxCopies)) }

    LabelPrintContrastTheme {
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
                    CopiesStepper(
                        copies = localCopies,
                        maxCopies = maxCopies,
                        onCopiesChange = { localCopies = it }
                    )

                    if (onFieldsClick != null) {
                        OutlinedButton(
                            onClick = onFieldsClick,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.label_fields_title))
                        }
                    }

                    if (onCalibrate != null) {
                        OutlinedButton(
                            onClick = onCalibrate,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.label_config_calibrate))
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            if (isPrinterConnected) {
                                onDisconnectClick?.invoke()
                            } else {
                                onConnectClick()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (isPrinterConnected) {
                                stringResource(R.string.disconnect)
                            } else {
                                stringResource(R.string.label_config_connect_printer)
                            }
                        )
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
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}

/**
 * Assigns a field to each of the template's placeholders.
 */
@Composable
fun LabelFieldsDialog(
    placeholders: List<String>,
    assignments: Map<String, String>,
    onFieldClick: (placeholder: String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    LabelPrintContrastTheme {
        AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(text = stringResource(R.string.label_fields_title)) },
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
                            selected = assignments[placeholder] ?: "",
                            onClick = { onFieldClick(placeholder) }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirm) {
                    Text(stringResource(R.string.dialog_ok))
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
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

/**
 * The copies count between round - and + buttons.
 */
@Composable
fun CopiesStepper(
    copies: Int,
    maxCopies: Int,
    onCopiesChange: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        FilledTonalIconButton(
            onClick = { onCopiesChange(copies - 1) },
            enabled = copies > 1
        ) {
            Icon(
                Icons.Default.Remove,
                contentDescription = stringResource(R.string.label_config_copies_decrease)
            )
        }

        // outlined like the buttons below it
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .border(ButtonDefaults.outlinedButtonBorder(enabled = true), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = pluralStringResource(R.plurals.label_config_copies_count, copies, copies),
                style = MaterialTheme.typography.labelLarge
            )
        }

        FilledTonalIconButton(
            onClick = { onCopiesChange(copies + 1) },
            enabled = copies < maxCopies
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = stringResource(R.string.label_config_copies_increase)
            )
        }
    }
}
