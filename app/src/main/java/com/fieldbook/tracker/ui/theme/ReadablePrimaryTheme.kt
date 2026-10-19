package com.fieldbook.tracker.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

/**
 * Minimum contrast ratio between primary and surface before primary is considered unusable
 * as a content color. Low enough to leave colored themes alone, only catches near-identical
 * pairs like the high contrast theme's white primary on a white surface.
 */
private const val MIN_PRIMARY_CONTRAST = 1.5f

/**
 * Material3 components (text/outlined buttons, switches, segmented buttons) draw their content in
 * [ColorScheme.primary]. When the theme's primary matches its surface that content disappears,
 * so swap primary for the surface's content color.
 *
 * Wrap surface content such as dialogs only, so app bars keep the theme's primary colors.
 */
@Composable
fun ReadablePrimaryTheme(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val adjusted = remember(scheme) { scheme.withReadablePrimary() }
    MaterialTheme(
        colorScheme = adjusted,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}

private fun ColorScheme.withReadablePrimary(): ColorScheme {
    if (contrastRatio(primary, surface) >= MIN_PRIMARY_CONTRAST) return this
    return copy(
        primary = onSurface,
        onPrimary = surface,
        inversePrimary = surface
    )
}

private fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (maxOf(la, lb) + 0.05f) / (minOf(la, lb) + 0.05f)
}
