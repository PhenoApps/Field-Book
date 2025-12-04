package com.fieldbook.tracker.ui.dialogs.traits

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.dialogs.components.DialogOption
import com.fieldbook.tracker.ui.dialogs.components.OptionsDialog
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun SortOptionsDialog(
    currentSortOrder: String,
    onCancel: () -> Unit,
    onSortSelected: (String) -> Unit,
) {
    val sortOptions = listOf(
        "position" to stringResource(R.string.traits_sort_default),
        "observation_variable_name" to stringResource(R.string.traits_sort_name),
        "observation_variable_field_book_format" to stringResource(R.string.traits_sort_format),
        "internal_id_observation_variable" to stringResource(R.string.traits_sort_import_order),
        "visible" to stringResource(R.string.traits_sort_visibility)
    )

    var selectedOption by remember(currentSortOrder) { mutableStateOf(currentSortOrder) }

    val options = sortOptions.map { (key, label) ->
        DialogOption(
            icon = null,
            title = label,
            onClick = { selectedOption = key },
            isOptionActive = (selectedOption == key)
        )
    }

    OptionsDialog(
        title = stringResource(R.string.traits_toolbar_sort),
        options = options,
        positiveButtonText = stringResource(R.string.dialog_ok),
        onPositive = { onSortSelected(selectedOption) },
        negativeButtonText = stringResource(R.string.dialog_cancel),
        onNegative = onCancel
    )
}

@Preview
@Composable
private fun SortOptionsDialogPreview() {
    AppTheme {
        SortOptionsDialog(
            currentSortOrder = "position",
            onCancel = { },
            onSortSelected = { },
        )
    }
}