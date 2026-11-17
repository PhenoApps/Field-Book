package com.fieldbook.tracker.ui.dialogs.builder

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun DialogButtonsRow(
    positiveButtonText: String? = null,
    positiveTextColor: Color = AppTheme.colors.text.button,
    onPositive: (() -> Unit)? = null,
    negativeButtonText: String? = null,
    negativeTextColor: Color = AppTheme.colors.text.button,
    onNegative: (() -> Unit)? = null,
    neutralButtonText: String? = null,
    neutralTextColor: Color = AppTheme.colors.text.button,
    onNeutral: (() -> Unit)? = null,
) {

    Column {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {

            neutralButtonText?.let { text ->
                onNeutral?.let {
                    DialogButton(
                        text = text,
                        textColor = neutralTextColor,
                        onClick = it,
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            negativeButtonText?.let { text ->
                onNegative?.let {
                    DialogButton(
                        text = text,
                        textColor = negativeTextColor,
                        onClick = it,
                    )
                }
            }

            positiveButtonText?.let { text ->
                onPositive?.let {
                    DialogButton(
                        text = text,
                        textColor = positiveTextColor,
                        onClick = it,
                    )
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DialogButtonRowPreview() {
    DialogButtonsRow(
        positiveButtonText = "Positive",
        onPositive = { },
        negativeButtonText = "Negative",
        onNegative = { },
        neutralButtonText = "Neutral",
        onNeutral = { },
    )
}