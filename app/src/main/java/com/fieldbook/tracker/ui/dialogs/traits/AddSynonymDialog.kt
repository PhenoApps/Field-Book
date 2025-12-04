package com.fieldbook.tracker.ui.dialogs.traits

import com.fieldbook.tracker.ui.dialogs.AppAlertDialog
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun AddSynonymDialog(
    title: String,
    hint: String,
    initialValue: String = "",
    onValidate: (String) -> String?, // return error message or null if valid
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var inputText by remember { mutableStateOf(initialValue) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    AppAlertDialog(
        title = title,
        content = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = {
                        inputText = it

                        // clear the previous error when user starts typing
                        if (errorMessage != null) {
                            errorMessage = null
                        }
                    },
                    label = { Text(hint) },
                    isError = errorMessage != null,
                    supportingText = errorMessage?.let {
                        {
                            Text(
                                text = it,
                                color = AppTheme.colors.status.error
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true
                )
            }
        },
        positiveButtonText = stringResource(R.string.trait_swap_name_set_alias),
        onPositive = {
            val validationError = onValidate(inputText.trim())
            if (validationError != null) {
                errorMessage = validationError
            } else {
                onConfirm(inputText.trim())
            }
        },
        negativeButtonText = stringResource(R.string.dialog_cancel),
        onNegative = onDismiss,
        neutralButtonText = stringResource(R.string.dialog_clear),
        onNeutral = {
            inputText = ""
            errorMessage = null
        },
    )
}

@Preview
@Composable
private fun AddSynonymDialogPreview() {
    AppTheme {
        AddSynonymDialog(
            title = "Add New Synonym",
            hint = "Synonym",
            onValidate = { input -> "Error" },
            onConfirm = {},
            onDismiss = {},
        )
    }
}