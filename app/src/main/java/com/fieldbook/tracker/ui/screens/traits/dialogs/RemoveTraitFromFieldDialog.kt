package com.fieldbook.tracker.ui.screens.traits.dialogs

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.dialogs.builder.AppAlertDialog
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun RemoveTraitFromFieldDialog(
    traitName: String,
    isViewerMode: Boolean,
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    val title = if (isViewerMode) R.string.traits_viewer_remove_from_field_title else R.string.traits_options_delete_title
    val message = if (isViewerMode) R.string.traits_viewer_remove_from_field_message else R.string.traits_warning_delete
    val confirmText = if (isViewerMode) R.string.dialog_remove else R.string.dialog_delete

    AppAlertDialog(
        title = stringResource(title),
        content = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = if (isViewerMode) stringResource(message, traitName) else stringResource(message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        positiveButtonText = stringResource(confirmText),
        positiveTextColor = AppTheme.colors.status.error,
        onPositive = onRemove,
        negativeButtonText = stringResource(R.string.dialog_cancel),
        onNegative = onCancel,
    )
}

