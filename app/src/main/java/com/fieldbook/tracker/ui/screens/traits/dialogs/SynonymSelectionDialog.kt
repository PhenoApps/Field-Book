package com.fieldbook.tracker.ui.screens.traits.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.components.widgets.RadioButton
import com.fieldbook.tracker.ui.dialogs.builder.AppAlertDialog
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun SynonymSelectionDialog(
    title: String,
    synonyms: List<String>,
    currentSelection: String?,
    onSynonymSelected: (String) -> Unit,
    onAddNewSynonym: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedSynonym by remember { mutableStateOf(currentSelection) }

    AppAlertDialog(
        title = title,
        content = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                synonyms.forEach { synonym ->
                    RadioButton(
                        isSelected = synonym == selectedSynonym,
                        onClick = { selectedSynonym = synonym },
                        text = synonym
                    )
                }
            }
        },
        positiveButtonText = stringResource(R.string.trait_swap_name_set_alias),
        onPositive = {
            selectedSynonym?.let { onSynonymSelected(it) }
        },
        negativeButtonText = stringResource(R.string.dialog_cancel),
        onNegative = onDismiss,
        neutralButtonText = stringResource(R.string.trait_add_synonym_new),
        onNeutral = onAddNewSynonym
    )
}

@Preview
@Composable
private fun SynonymSelectionDialogPreview() {
    AppTheme {
        SynonymSelectionDialog(
            title = "Select Synonym",
            synonyms = listOf("Height", "Plant Height", "Stem Length"),
            currentSelection = "Height",
            onSynonymSelected = {},
            onAddNewSynonym = {},
            onDismiss = {}
        )
    }
}