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
fun DeleteAllTraitsDialog(
    onCancel: () -> Unit,
    onDelete: () -> Unit
) {
    AppAlertDialog(
        title = stringResource(R.string.traits_toolbar_delete_all),
        content = {
            Column {
                Row(
                    verticalAlignment = Alignment.Companion.CenterVertically,
                    modifier = Modifier.Companion.padding(bottom = 16.dp)
                ) {
                    Text(
                        text = stringResource(R.string.dialog_delete_traits_message),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        },
        positiveButtonText = stringResource(R.string.dialog_delete),
        positiveTextColor = AppTheme.colors.status.error,
        onPositive = onDelete,
        negativeButtonText = stringResource(R.string.dialog_no),
        onNegative = onCancel,
    )
}