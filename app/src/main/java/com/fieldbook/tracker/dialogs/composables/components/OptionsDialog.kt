package com.fieldbook.tracker.dialogs.composables.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.dialogs.composables.AppAlertDialog
import com.fieldbook.tracker.ui.components.widgets.HorizontalDivide
import com.fieldbook.tracker.ui.theme.AppTheme

data class DialogOption(
    val icon: Any? = null,
    val title: String,
    val onClick: () -> Unit,
    val isOptionActive: Boolean = false,
)

@Composable
fun OptionsDialog(
    title: String,
    options: List<DialogOption>,
    positiveButtonText: String? = null,
    onPositive: (() -> Unit)? = null,
    negativeButtonText: String? = null,
    onNegative: (() -> Unit)? = null,
    neutralButtonText: String? = null,
    onNeutral: (() -> Unit)? = null,
) {
    AppAlertDialog(
        title = title,
        content = {
            Column {
                options.forEachIndexed { index, option ->
                    OptionsDialogListItem(
                        icon = option.icon,
                        title = option.title,
                        onClick = option.onClick,
                        isItemActive = option.isOptionActive
                    )

                    // add divider between items (except last)
                    if (index < options.size - 1) {
                        HorizontalDivide(modifier = Modifier.padding(horizontal = 10.dp))
                    }
                }
            }
        },
        positiveButtonText = positiveButtonText,
        onPositive = onPositive,
        negativeButtonText = negativeButtonText,
        onNegative = onNegative,
        neutralButtonText = neutralButtonText,
        onNeutral = onNeutral
    )
}

@Preview
@Composable
private fun OptionsDialogPreview() {
    val options = listOf(
        DialogOption(
            icon = R.drawable.ic_ruler,
            title = "Create New Trait",
            onClick = { }
        ),
        DialogOption(
            icon = R.drawable.ic_file_generic,
            title = "Import from File",
            onClick = { }
        ),
        DialogOption(
            icon = R.drawable.ic_adv_brapi,
            title = "BrAPI Server",
            onClick = { }
        ),
        DialogOption(
            icon = null,
            title = "Option without Icon",
            onClick = { }
        )
    )
    AppTheme {
        OptionsDialog(
            title = "Select from options",
            options = options,
            negativeButtonText = "Cancel",
            onNegative = { },
        )
    }
}