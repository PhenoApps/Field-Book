package com.fieldbook.tracker.traits.composables

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.ui.theme.AppTheme

/** Matches `button_unselected.xml` / `button_selected.xml` (5dp corners, trait tint). */
private val TraitButtonShape = RoundedCornerShape(5.dp)

@Composable
fun TreeActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    emphasized: Boolean = false,
    selected: Boolean = false,
) {
    val colors = AppTheme.colors
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val filled = selected || emphasized || pressed
    val background = when {
        !enabled -> colors.button.traitBackground.copy(alpha = 0.5f)
        filled -> colors.button.categoricalSelected
        else -> colors.button.traitBackground
    }
    val contentColor = when {
        !enabled -> colors.text.secondary
        filled -> colors.text.highContrast
        else -> colors.text.primary
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = TraitButtonShape,
        color = background,
        contentColor = contentColor,
        border = BorderStroke(1.dp, colors.surface.border),
        interactionSource = interactionSource,
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
    ) {
        Text(
            text = text,
            style = AppTheme.typography.bodyStyle,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
fun TreeTextLink(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        shape = RectangleShape,
        colors = ButtonDefaults.textButtonColors(
            contentColor = AppTheme.colors.text.button,
            disabledContentColor = AppTheme.colors.text.secondary,
        ),
        modifier = modifier.defaultMinSize(minHeight = 56.dp),
    ) {
        Text(
            text = text,
            style = AppTheme.typography.bodyStyle,
        )
    }
}
