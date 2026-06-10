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
    onCancel: () -> Unit,
    onRemove: () -> Unit,
) {
    AppAlertDialog(
        title = stringResource(R.string.traits_viewer_remove_from_field_title),
        content = {
            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.traits_viewer_remove_from_field_message, traitName),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        positiveButtonText = stringResource(R.string.dialog_remove),
        positiveTextColor = AppTheme.colors.status.error,
        onPositive = onRemove,
        negativeButtonText = stringResource(R.string.dialog_cancel),
        onNegative = onCancel,
    )
}

