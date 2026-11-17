package com.fieldbook.tracker.ui.screens.traits.dialogs

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.ui.dialogs.builder.AppAlertDialog
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun CopyTraitDialog(
    trait: TraitObject,
    allTraits: List<TraitObject>,
    onCancel: () -> Unit,
    onCopy: (String) -> Unit
) {
    var name by remember { mutableStateOf(generateCopyName(trait.alias, allTraits)) }

    AppAlertDialog(
        title = stringResource(R.string.traits_options_copy_title),
        content = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.trait_new_trait_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
        },
        positiveButtonText = stringResource(R.string.dialog_save),
        onPositive = { onCopy(name.trim()) },
        negativeButtonText = stringResource(R.string.dialog_cancel),
        onNegative = onCancel
    )
}

private fun generateCopyName(alias: String, allTraits: List<TraitObject>): String {
    // remove existing "-Copy-(X)" if user copies a copy
    val base = alias.substringBefore("-Copy")

    var i = 1
    var newName: String

    while (true) { // run until no match against names AND aliases
        newName = "$base-Copy-($i)"
        val exists = allTraits.any { it.name == newName || it.alias == newName }
        if (!exists) break
        i++
    }

    return newName
}

@Preview
@Composable
private fun CopyTraitDialogPreview() {
    val listOfTraits = listOf(
        TraitObject().apply {
            alias = "height"
            name = "height"
        },
        TraitObject().apply {
            alias = "height2"
            name = "height2"
        },
    )
    AppTheme {
        CopyTraitDialog(
            trait = TraitObject().apply {
                alias = "dsa"
                name = "dsa"
            },
            allTraits = listOfTraits,
            onCancel = { },
            onCopy = { }
        )
    }
}