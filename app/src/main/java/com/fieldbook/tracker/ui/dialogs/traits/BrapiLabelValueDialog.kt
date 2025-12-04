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
import com.fieldbook.tracker.utilities.TraitOptionChipsUtil.parseCategoryExample

@Composable
fun BrapiLabelValueDialog(
    categories: String,
    displaysValue: Boolean,
    onSave: (Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var displaysValues by remember { mutableStateOf(displaysValue) }
    val firstCategory = parseCategoryExample(categories)

    val labelOption = stringResource(R.string.trait_brapi_label_display, firstCategory.first)
    val valueOption = stringResource(R.string.trait_brapi_value_display, firstCategory.second)

    AppAlertDialog(
        title = stringResource(R.string.trait_brapi_display_dialog_title),
        content = {
            Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                // labels
                RadioButton(
                    isSelected = !displaysValues,
                    onClick = { displaysValues = false },
                    text = labelOption
                )

                // values
                RadioButton(
                    isSelected = displaysValues,
                    onClick = { displaysValues = true },
                    text = valueOption
                )
            }
        },
        negativeButtonText = stringResource(R.string.dialog_cancel),
        onNegative = onDismiss,
        positiveButtonText = stringResource(R.string.dialog_save),
        onPositive = { onSave(displaysValues) }
    )
}

@Preview
@Composable
private fun BrapiLabelValueDialogPreview() {
    AppTheme {
        BrapiLabelValueDialog(
            categories = """[{"label": "Small", "value": "1"}, {"label": "Medium", "value": "2"}]""",
            displaysValue = false,
            onSave = { },
            onDismiss = { },
        )
    }
}