package com.fieldbook.tracker.dialogs.composables

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

@Composable
fun DialogButtonsRow(
    positiveButtonText: String? = null,
    onPositive: (() -> Unit)? = null,
    negativeButtonText: String? = null,
    onNegative: (() -> Unit)? = null,
    neutralButtonText: String? = null,
    onNeutral: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        neutralButtonText?.let { text ->
            onNeutral?.let { DialogButton(text, it) }
        }

        // to space such that negative and positive are towards the right
        Spacer(modifier = Modifier.weight(1f))

        negativeButtonText?.let { text ->
            onNegative?.let { DialogButton(text, it) }
        }

        positiveButtonText?.let { text ->
            onPositive?.let { DialogButton(text, it) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DialogButtonRowPreview() {
    DialogButtonsRow(
        positiveButtonText = "Positive",
        onPositive = {},
        negativeButtonText = "Negative",
        onNegative = {},
        neutralButtonText = "Neutral",
        onNeutral = {}
    )
}