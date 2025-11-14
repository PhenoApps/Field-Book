package com.fieldbook.tracker.dialogs.composables

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun DialogButton(
    text: String,
    textColor: Color,
    onClick: () -> Unit,
) {
    AppTheme {
        TextButton(
            onClick = { onClick.invoke() },
            shape = RectangleShape,
            colors = ButtonDefaults.textButtonColors(
                contentColor = textColor
            ),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun DialogButtonPreview() {
    DialogButton("Text", AppTheme.colors.text.button) { }
}