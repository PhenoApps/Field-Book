package com.fieldbook.tracker.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun CardView(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = AppTheme.colors.background
        ),
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        border = null,
        shape = RectangleShape,
    ) {
        content()
    }
}

@Preview(showBackground = true)
@Composable
private fun CardPreview() {
    AppTheme {
        CardView {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Sample Title",
                    style = AppTheme.typography.titleStyle
                )
                Text(
                    text = "Sample content goes here",
                    style = AppTheme.typography.subheadingStyle,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}