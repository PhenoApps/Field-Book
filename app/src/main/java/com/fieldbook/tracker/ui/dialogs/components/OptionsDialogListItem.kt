package com.fieldbook.tracker.ui.dialogs.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.ui.components.widgets.AppIcon
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun OptionsDialogListItem(
    icon: Any? = null,
    title: String,
    onClick: () -> Unit,
    isItemActive: Boolean = false,
) {
    val backgroundColor = if (isItemActive) {
        AppTheme.colors.button.traitBackground
    } else {
        AppTheme.colors.background
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        ),
        shape = RectangleShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 24.dp),
        ) {

            icon?.let {
                AppIcon(
                    icon = it,
                    contentDescription = null,
                )
                Spacer(modifier = Modifier.width(16.dp))
            }

            Text(
                text = title,
                style = AppTheme.typography.titleStyle,
            )
        }
    }
}

@Preview()
@Composable
private fun OptionsDialogListItemPreview() {
    AppTheme {
        OptionsDialogListItem(
            icon = R.drawable.ic_file_generic,
            title = "Add",
            onClick = { },
        )
    }
}