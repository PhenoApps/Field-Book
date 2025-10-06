package com.fieldbook.tracker.dialogs.composables

import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.material.textfield.TextInputEditText

@Composable
fun TextInputDialog(
    title: String,
    hint: String,
    initialValue: String = "",
    positiveButtonText: String,
    onPositive: (String) -> String?, // returns error message
    negativeButtonText: String,
    onNegative: () -> Unit,
    neutralButtonText: String? = null,
    onNeutral: (() -> Unit)? = null,
) {
    var text by remember { mutableStateOf(initialValue) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AppAlertDialog(
        title = title,
        content = {
            Column {
                AndroidView(
                    factory = { context ->
                        TextInputEditText(context).apply {

                            setText(initialValue)
                            this.hint = hint
                            isSingleLine = true
                            inputType = InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                            filters = arrayOf(InputFilter.LengthFilter(200))

                            addTextChangedListener(object : TextWatcher {

                                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                                    text = s?.toString() ?: ""
                                    errorMessage = null
                                }

                                override fun afterTextChanged(s: Editable?) {}

                            })
                        }
                    },
                    update = { editText -> // sync the view with the state when text changes
                        if (editText.text?.toString() != text) {
                            editText.setText(text)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                errorMessage?.let { error ->
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        positiveButtonText = positiveButtonText,
        onPositive = { errorMessage = onPositive(text.trim()) },
        negativeButtonText = negativeButtonText,
        onNegative = onNegative,
        neutralButtonText = neutralButtonText,
        onNeutral = onNeutral?.let { {
            text = ""
            errorMessage = null
            it.invoke()
        }}
    )
}

@Preview
@Composable
private fun TextInputDialogPreview() {
    TextInputDialog(
        title = "Title",
        hint = "Label",
        initialValue = "",
        positiveButtonText = "Positive",
        onPositive = { _ -> null },
        negativeButtonText = "Negative",
        onNegative = {},
        neutralButtonText = "Neutral",
        onNeutral = {})
}