package com.fieldbook.tracker.ui.screens.traits.dialogs

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.dialogs.DialogOption
import com.fieldbook.tracker.ui.dialogs.OptionsDialog
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun CreateTraitsDialog(
    onDialogClose: () -> Unit,
    onCreateNew: () -> Unit,
    onImportFromFile: () -> Unit,
    onImportFromBrapi: () -> Unit,
    isBrapiEnabled: Boolean,
    brapiDisplayName: String
) {
    val options = buildList {
        add(
            DialogOption(
                icon = R.drawable.ic_ruler,
                title = stringResource(R.string.traits_dialog_create),
                onClick = onCreateNew
            )
        )
        add(
            DialogOption(
                icon = R.drawable.ic_file_generic,
                title = stringResource(R.string.traits_dialog_import_from_file),
                onClick = onImportFromFile
            )
        )
        if (isBrapiEnabled) {
            add(
                DialogOption(
                    icon = R.drawable.ic_adv_brapi,
                    title = brapiDisplayName,
                    onClick = onImportFromBrapi
                )
            )
        }
    }

    OptionsDialog(
        title = stringResource(R.string.traits_new_dialog_title),
        options = options,
        negativeButtonText = stringResource(R.string.dialog_cancel),
        onNegative = onDialogClose,
    )
}

@Preview
@Composable
private fun NewTraitsDialogPreview() {
    AppTheme {
        CreateTraitsDialog(
            onDialogClose = { },
            onCreateNew = { },
            onImportFromFile = { },
            onImportFromBrapi = { },
            isBrapiEnabled = true,
            brapiDisplayName = "BrAPI Server",
        )
    }
}