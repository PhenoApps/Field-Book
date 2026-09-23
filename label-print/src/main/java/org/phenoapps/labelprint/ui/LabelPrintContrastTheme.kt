package org.phenoapps.labelprint.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * Minimum contrast ratio between primary and surface before primary is considered unusable
 * as a content color. Low enough to leave colored themes alone, only catches near-identical
 * pairs like a high contrast theme's white primary on a white surface.
 */
private const val MIN_PRIMARY_CONTRAST = 1.5f

/**
 * Material3 components (text/outlined buttons, sliders, checkboxes, focused text fields) draw
 * their content in [ColorScheme.primary]. When the host theme's primary matches its surface,
 * that content disappears, so swap primary for the surface's content color.
 *
 * Wrap surface content (screen bodies, dialogs) only, not toolbars, so app bars keep the
 * host theme's primary colors.
 */
@Composable
fun LabelPrintContrastTheme(content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val adjusted = remember(scheme) { scheme.withReadablePrimary() }
    MaterialTheme(
        colorScheme = adjusted,
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}

/**
 * Primary if it is readable on surface, otherwise the surface's content color.
 */
@Composable
fun readablePrimary(): Color = MaterialTheme.colorScheme.withReadablePrimary().primary

/**
 * Border for a surface-colored control placed on a primary-colored bar, needed when the two
 * colors blend together. Null when primary and surface already contrast.
 */
@Composable
fun lowContrastOutline(): BorderStroke? {
    val scheme = MaterialTheme.colorScheme
    return if (scheme.hasReadablePrimary()) null else BorderStroke(1.dp, scheme.onSurface)
}

private fun ColorScheme.hasReadablePrimary(): Boolean =
    contrastRatio(primary, surface) >= MIN_PRIMARY_CONTRAST

private fun ColorScheme.withReadablePrimary(): ColorScheme {
    if (hasReadablePrimary()) return this
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
