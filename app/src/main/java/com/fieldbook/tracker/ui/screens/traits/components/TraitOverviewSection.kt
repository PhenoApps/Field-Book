package com.fieldbook.tracker.ui.screens.traits.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.ui.components.widgets.Chip
import com.fieldbook.tracker.ui.screens.traits.dialogs.AddSynonymDialog
import com.fieldbook.tracker.ui.screens.traits.dialogs.SynonymSelectionDialog
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun TraitOverviewSection(
    trait: TraitObject,
    onToggleVisibility: ((Boolean) -> Unit)? = null,
    onAddSynonym: ((String) -> Unit)? = null,
    onValidateSynonym: ((String) -> String?)? = null,
    onEditFormat: (() -> Unit)? = null,
) {
    var showSwapDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {

            // source
            Chip(
                text = trait.traitDataSource,
                icon = R.drawable.ic_table_arrow_right,
            )

            // format
            val formatEnum = Formats.entries.find { it.getDatabaseName() == trait.format }
            val formatIcon = formatEnum?.getIcon() ?: R.drawable.ic_trait_categorical
            Chip(
                text = trait.format,
                icon = formatIcon,
                onClick = onEditFormat,
            )

            // rename
            Chip(
                text = stringResource(R.string.trait_detail_chip_rename),
                icon = R.drawable.ic_rename,
                onClick = {
                    if (trait.synonyms.isNotEmpty()) {
                        showSwapDialog = true
                    } else {
                        showAddDialog = true
                    }
                }
            )

            // visibility
            val visibilityIcon =
                if (trait.visible) R.drawable.ic_eye
                else R.drawable.ic_eye_off

            Chip(
                text = stringResource(if (trait.visible) R.string.trait_visible else R.string.trait_hidden),
                icon = visibilityIcon,
                onClick = { onToggleVisibility?.invoke(!trait.visible) },
            )
        }
    }

    if (showSwapDialog && trait.synonyms.isNotEmpty()) {
        SynonymSelectionDialog(
            title = stringResource(R.string.trait_swap_synonym_dialog_title),
            synonyms = trait.synonyms,
            currentSelection = trait.alias,
            onSynonymSelected = { selectedSynonym ->
                onAddSynonym?.invoke(selectedSynonym)
                showSwapDialog = false
            },
            onAddNewSynonym = {
                showSwapDialog = false
                showAddDialog = true
            },
            onDismiss = { showSwapDialog = false }
        )
    }

    if (showAddDialog) {
        AddSynonymDialog(
            title = stringResource(R.string.trait_add_synonym_new_dialog),
            hint = stringResource(R.string.trait_synonym),
            onValidate = onValidateSynonym ?: { null },
            onConfirm = { newSynonym ->
                onAddSynonym?.invoke(newSynonym)
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun TraitOverviewSectionPreview() {
    AppTheme {
        TraitOverviewSection(
            trait = TraitObject().apply {
                traitDataSource = "brapi"
                format = "percent"
            }
        )
    }
}
