package com.fieldbook.tracker.dialogs.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun AppAlertDialog(
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable (() -> Unit)? = null,
    positiveButtonText: String? = null,
    onPositive: (() -> Unit)? = null,
    negativeButtonText: String? = null,
    onNegative: (() -> Unit)? = null,
    neutralButtonText: String? = null,
    onNeutral: (() -> Unit)? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            // dialog title
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 16.dp)
            )

            // dialog content
            content?.let {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 24.dp)
                ) {
                    it()
                }
            }

            // dialog buttons
            DialogButtonsRow(
                positiveButtonText = positiveButtonText,
                onPositive = onPositive,
                negativeButtonText = negativeButtonText,
                onNegative = onNegative,
                neutralButtonText = neutralButtonText,
                onNeutral = onNeutral
            )
        }
    }
}