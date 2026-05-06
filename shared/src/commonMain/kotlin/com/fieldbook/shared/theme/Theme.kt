package com.fieldbook.shared.theme

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.AlertDialog as MaterialAlertDialog
import androidx.compose.ui.window.Dialog as ComposeDialog

private val MainLightColors = lightColorScheme(
    primary = Color(0xFF8BC34A),
    onPrimary = Color(0xFF000000),
    primaryContainer = Color(0xFF689F38),
    onPrimaryContainer = Color(0xFF000000),
    secondary = Color(0xFF795548),
    onSecondary = Color(0xFF000000),
    background = Color(0xFFFFFFFF),
    onBackground = Color(0xFF000000),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF000000),
    error = Color(0xFFD50000),
    onError = Color(0xFFFFFFFF)
)

private val MainShapes = Shapes(
    small = RoundedCornerShape(4.dp), // Use for buttons
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(0.dp)
)

val MainFloatingActionButtonShape = RoundedCornerShape(50)

fun Modifier.numericButtonDefaults() =
    this.padding(4.dp).fillMaxHeight()

@Composable
fun MainTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MainLightColors,
        typography = MaterialTheme.typography,
        shapes = MainShapes,
        content = content
    )
}

fun argbIntToColor(argb: Int): Color = Color(argb.toLong())

enum class AppColors(val argb: Int) {
    fb_color_text_dark(0xFF000000.toInt()),
    fb_value_saved_color(0xFFD50000.toInt());

    val color: Color get() = argbIntToColor(argb)
}

val TraitButtonDefaultColor = Color(0xFFD9D9D9)

@Composable
fun traitButtonColors(selected: Boolean, selectedColor: Color = MaterialTheme.colorScheme.primary): androidx.compose.material3.ButtonColors =
    ButtonDefaults.buttonColors(
        containerColor = if (selected) selectedColor else TraitButtonDefaultColor,
        contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else Color.Black
    )

@Composable
fun Button(
    onClick: () -> Unit,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    shape: androidx.compose.foundation.shape.CornerBasedShape = MaterialTheme.shapes.small,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    content: @Composable RowScope.() -> Unit
) {
    val borderModifier = if (!selected) Modifier.border(1.dp, Color.Black, shape) else Modifier
    Button(
        onClick = onClick,
        colors = traitButtonColors(selected, selectedColor),
        modifier = modifier.then(borderModifier),
        shape = shape,
        content = content
    )
}

@Composable
fun FilledIconButton(
    onClick: () -> Unit,
    selected: Boolean = false,
    modifier: Modifier = Modifier,
    shape: androidx.compose.foundation.shape.CornerBasedShape = MaterialTheme.shapes.small,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val borderModifier = if (!selected) Modifier.border(1.dp, Color.Black, shape) else Modifier
    androidx.compose.material3.FilledIconButton(
        onClick = onClick,
        modifier = modifier.then(borderModifier),
        enabled = enabled,
        shape = shape,
        colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
            containerColor = if (selected) selectedColor else TraitButtonDefaultColor,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else Color.Black
        ),
        content = content
    )
}

@Composable
private fun DialogButtonColorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MaterialTheme.colorScheme.copy(
            primary = Color.Black,
            onPrimary = Color.White
        ),
        typography = MaterialTheme.typography,
        shapes = MaterialTheme.shapes,
        content = content
    )
}

@Composable
fun Dialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    ComposeDialog(
        onDismissRequest = onDismissRequest,
        properties = properties
    ) {
        DialogButtonColorTheme(content = content)
    }
}

@Composable
fun AlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = AlertDialogDefaults.shape,
    containerColor: Color = AlertDialogDefaults.containerColor,
    iconContentColor: Color = AlertDialogDefaults.iconContentColor,
    titleContentColor: Color = AlertDialogDefaults.titleContentColor,
    textContentColor: Color = AlertDialogDefaults.textContentColor,
    tonalElevation: Dp = AlertDialogDefaults.TonalElevation,
    properties: DialogProperties = DialogProperties()
) {
    DialogButtonColorTheme {
        MaterialAlertDialog(
            onDismissRequest = onDismissRequest,
            confirmButton = confirmButton,
            modifier = modifier,
            dismissButton = dismissButton,
            icon = icon,
            title = title,
            text = text,
            shape = shape,
            containerColor = containerColor,
            iconContentColor = iconContentColor,
            titleContentColor = titleContentColor,
            textContentColor = textContentColor,
            tonalElevation = tonalElevation,
            properties = properties
        )
    }
}
