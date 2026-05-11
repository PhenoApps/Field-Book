package com.fieldbook.tracker.ui.screens.traits.dialogs

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.dialogs.builder.AppAlertDialog

@Composable
fun ExportCheckDialog(
    onConfirmExport: () -> Unit,
    onSkipExport: () -> Unit
) {
    AppAlertDialog(
        content = {
            Text(stringResource(R.string.traits_export_check))
        },
        positiveButtonText = stringResource(R.string.dialog_yes),
        onPositive = onConfirmExport,
        negativeButtonText = stringResource(R.string.dialog_no),
        onNegative = onSkipExport,
    )
}
