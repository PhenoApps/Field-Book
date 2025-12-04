package com.fieldbook.tracker.ui.dialogs

import android.util.TypedValue
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import com.fieldbook.tracker.R

@Composable
fun DialogTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val theme = context.theme

    val typedValue = TypedValue()

    theme.resolveAttribute(R.attr.fb_color_background, typedValue, true)
    val backgroundColor = Color(typedValue.data)

    theme.resolveAttribute(R.attr.fb_color_text_dark, typedValue, true)
    val textColor = Color(typedValue.data)

    theme.resolveAttribute(R.attr.fb_color_accent, typedValue, true)
    val accentColor = Color(typedValue.data)

    theme.resolveAttribute(R.attr.fb_icon_tint, typedValue, true)
    val iconTint = Color(typedValue.data)

    theme.resolveAttribute(R.attr.fb_title_text_size, typedValue, true)
    val titleTextSize = with(density) {
        context.resources.getDimension(typedValue.resourceId).toSp()
    }

    theme.resolveAttribute(R.attr.fb_body_text_size, typedValue, true)
    val bodyTextSize = with(density) {
        context.resources.getDimension(typedValue.resourceId).toSp()
    }

    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            surface = backgroundColor,
            onSurface = textColor,
            primary = accentColor,
            onPrimary = textColor,
            surfaceVariant = backgroundColor,
            onSurfaceVariant = iconTint,
        ),
        typography = MaterialTheme.typography.copy(
            titleLarge = MaterialTheme.typography.titleLarge.copy(
                fontSize = titleTextSize,
                color = textColor,
                fontWeight = FontWeight.Bold
            ),
            bodyLarge = MaterialTheme.typography.bodyLarge.copy(
                fontSize = bodyTextSize,
                color = textColor
            )
        )
    ) {
        content()
    }
}