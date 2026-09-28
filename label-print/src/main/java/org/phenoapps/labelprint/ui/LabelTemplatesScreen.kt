package org.phenoapps.labelprint.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.phenoapps.labelprint.R

/**
 * A saved label template as shown in [LabelTemplatesScreen].
 *
 * @param traitCount number of traits that use this template as their own
 */
data class LabelTemplateListItem(
    val id: String,
    val name: String,
    val isDefault: Boolean,
    val traitCount: Int
)

/**
 * Lists saved label templates, where they are added, edited, duplicated, exported, deleted
 * and chosen as the default. Editing a single template happens in [ZplEditorScreen].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LabelTemplatesScreen(
    templates: List<LabelTemplateListItem>,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (id: String) -> Unit,
    onSetDefault: (id: String) -> Unit,
    onDuplicate: (id: String) -> Unit,
    onExport: (id: String) -> Unit,
    onDelete: (id: String) -> Unit
) {
    var pendingDelete by remember { mutableStateOf<LabelTemplateListItem?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.label_templates_title),
                        // matches the app's AppBar title: default top bar style, medium weight
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.zpl_editor_back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            LabelPrintContrastTheme {
                FloatingActionButton(
                    onClick = onAdd,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.label_templates_add)
                    )
                }
            }
        }
    ) { innerPadding ->
        LabelPrintContrastTheme {
            if (templates.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.label_templates_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    // leave room so the last row isn't under the add button
                    contentPadding = PaddingValues(start = 12.dp, top = 12.dp, end = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(templates, key = { it.id }) { template ->
                        LabelTemplateRow(
                            template = template,
                            onClick = { onEdit(template.id) },
                            onSetDefault = { onSetDefault(template.id) },
                            onDuplicate = { onDuplicate(template.id) },
                            onExport = { onExport(template.id) },
                            onDelete = { pendingDelete = template }
                        )
                    }
                }
            }

            pendingDelete?.let { template ->
                DeleteTemplateDialog(
                    template = template,
                    onConfirm = {
                        pendingDelete = null
                        onDelete(template.id)
                    },
                    onDismiss = { pendingDelete = null }
                )
            }
        }
    }
}

@Composable
private fun LabelTemplateRow(
    template: LabelTemplateListItem,
    onClick: () -> Unit,
    onSetDefault: () -> Unit,
    onDuplicate: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = template.name,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (template.isDefault) {
                        DefaultBadge(modifier = Modifier.padding(start = 8.dp))
                    }
                }
                Text(
                    text = pluralStringResource(
                        R.plurals.label_templates_used_by,
                        template.traitCount,
                        template.traitCount
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.label_templates_more_options),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    if (!template.isDefault) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.label_templates_set_default)) },
                            onClick = { menuExpanded = false; onSetDefault() }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.label_templates_duplicate)) },
                        onClick = { menuExpanded = false; onDuplicate() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.zpl_editor_export)) },
                        onClick = { menuExpanded = false; onExport() }
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.zpl_editor_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun DefaultBadge(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(4.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.label_templates_default_badge),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun DeleteTemplateDialog(
    template: LabelTemplateListItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(stringResource(R.string.label_templates_delete_title, template.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (template.traitCount > 0) {
                    Text(
                        pluralStringResource(
                            R.plurals.label_templates_delete_used_by,
                            template.traitCount,
                            template.traitCount
                        )
                    )
                }
                if (template.isDefault) {
                    Text(stringResource(R.string.label_templates_delete_default))
                }
                Text(stringResource(R.string.label_templates_delete_cannot_undo))
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.zpl_editor_delete),
                    color = MaterialTheme.colorScheme.error
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel))
            }
        }
    )
}
