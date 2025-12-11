package com.fieldbook.tracker.ui.dialogs.builder

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun AppAlertDialog(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: @Composable (() -> Unit)? = null,
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
    val onDismissRequest = onNegative ?: { }
    Dialog(onDismissRequest = onDismissRequest) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp, bottom = 8.dp)
            ) {
                // dialog title
                title?.let {
                    Text(
                        text = title,
                        style = AppTheme.typography.titleStyle,
                        modifier = Modifier
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 16.dp)
                    )
                }

                // dialog content
                content?.let {
                    Box(
                        modifier= Modifier
                            .heightIn(max = 600.dp)
                            .padding(horizontal = 24.dp)
                            .wrapContentHeight()
                    )  {
                        it()
                    }
                }

                // dialog buttons
                DialogButtonsRow(
                    positiveButtonText = positiveButtonText,
                    positiveTextColor = positiveTextColor,
                    onPositive = onPositive,
                    negativeButtonText = negativeButtonText,
                    negativeTextColor = negativeTextColor,
                    onNegative = onNegative,
                    neutralButtonText = neutralButtonText,
                    neutralTextColor = neutralTextColor,
                    onNeutral = onNeutral,
                )
            }
        }
    }
}

@Preview
@Composable
private fun AppAlertDialogPreview() {
    AppTheme {
        AppAlertDialog(
            title = "DialogTitle",
            content = {
                Box(modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)) {
                    Text("Content")
                }
            },
            positiveButtonText = "Positive",
            onPositive = {},
            negativeButtonText = "Negative",
            onNegative = {},
            neutralButtonText = "Neutral",
            onNeutral = {}
        )
    }
}