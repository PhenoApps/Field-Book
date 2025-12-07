package com.fieldbook.tracker.ui.dialogs.traits

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
import com.fieldbook.tracker.ui.dialogs.AppAlertDialog
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.TraitDetailUtil

@Composable
fun DateFormatDialog(
    currentUseDayOfYear: Boolean,
    onSave: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedUseDayOfYear by remember { mutableStateOf(currentUseDayOfYear) }

    val todayFormattedDate = TraitDetailUtil.getTodayFormattedDate()
    val todayDayOfYear = TraitDetailUtil.getTodayDayOfYear()

    AppAlertDialog(
        title = stringResource(R.string.trait_date_format_dialog_title),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                RadioButton(
                    isSelected = !selectedUseDayOfYear,
                    onClick = { selectedUseDayOfYear = false },
                    text = stringResource(R.string.trait_date_format_display, todayFormattedDate)
                )

                RadioButton(
                    isSelected = selectedUseDayOfYear,
                    onClick = { selectedUseDayOfYear = true },
                    text = stringResource(R.string.trait_day_format_display, todayDayOfYear)
                )
            }
        },
        positiveButtonText = stringResource(R.string.dialog_save),
        onPositive = {
            onSave(selectedUseDayOfYear)
        },
        negativeButtonText = stringResource(R.string.dialog_cancel),
        onNegative = onDismiss
    )
}

@Preview
@Composable
private fun DateFormatSelectionDialogPreview() {
    AppTheme {
        DateFormatDialog(
            currentUseDayOfYear = false,
            onSave = {},
            onDismiss = {}
        )
    }
}