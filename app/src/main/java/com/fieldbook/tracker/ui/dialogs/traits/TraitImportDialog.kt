package com.fieldbook.tracker.ui.dialogs.traits

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.dialogs.components.DialogOption
import com.fieldbook.tracker.ui.dialogs.components.OptionsDialog
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun TraitImportDialog(
    onDialogClose: () -> Unit,
    onLocalFile: () -> Unit,
    onCloudFile: () -> Unit
) {
    val options = buildList {
        add(
            DialogOption(
                icon = R.drawable.ic_file_generic,
                title = stringResource(R.string.import_source_local),
                onClick = onLocalFile
            )
        )
        add(
            DialogOption(
                icon = R.drawable.ic_file_cloud,
                title = stringResource(R.string.import_source_cloud),
                onClick = onCloudFile
            )
        )
    }

    OptionsDialog(
        title = stringResource(R.string.traits_dialog_import_from_file),
        options = options,
        onNegative = onDialogClose,
        negativeButtonText = stringResource(R.string.dialog_cancel),
    )
}

@Preview
@Composable
private fun TraitImportDialogPreview() {
    AppTheme {
        TraitImportDialog(
            onDialogClose = { },
            onLocalFile = { },
            onCloudFile = { }
        )
    }
}