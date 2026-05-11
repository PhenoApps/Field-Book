package com.fieldbook.tracker.ui.components.widgets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun RadioButton(
    isSelected: Boolean = false,
    onClick: () -> Unit,
    text: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(
                selected = isSelected,
                onClick = onClick,
                role = Role.RadioButton
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = isSelected,
            colors = RadioButtonDefaults.colors(
                selectedColor = AppTheme.colors.accent
            ),
            onClick = onClick
        )
        Text(
            text = text,
            style = AppTheme.typography.bodyStyle,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RadioButtonPreview() {
    AppTheme {
        Column {
            RadioButton(
                onClick = { },
                text = "Value 1"
            )
            RadioButton(
                isSelected = true,
                onClick = { },
                text = "Value 2"
            )
        }
    }
}