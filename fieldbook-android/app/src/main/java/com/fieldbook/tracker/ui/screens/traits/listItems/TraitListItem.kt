package com.fieldbook.tracker.ui.screens.traits.listItems

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.ui.components.widgets.AppIcon
import com.fieldbook.tracker.ui.theme.AppTheme
import sh.calvin.reorderable.ReorderableCollectionItemScope

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TraitListItem(
    trait: TraitObject,
    onClick: () -> Unit,
    onToggleVisibility: (Boolean) -> Unit,
    isAnyItemDragging: Boolean = false, // used disable interactions when something is being dragged
    reorderableScope: ReorderableCollectionItemScope? = null,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }

    Box (
        modifier = modifier
            .fillMaxWidth()
            .background(
                color = AppTheme.colors.background,
                shape = RoundedCornerShape(5.dp)
            )
            .border(
                width = 1.dp,
                color = AppTheme.colors.surface.border,
                shape = RoundedCornerShape(5.dp)
            )
            .then(
                if (reorderableScope != null) {
                    with(reorderableScope) {
                        Modifier.longPressDraggableHandle( // reorder only on long press
                            onDragStarted = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragStopped = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            },
                            interactionSource = interactionSource
                        )
                    }
                } else Modifier
            )
            .clickable(
                onClick = onClick,
                interactionSource = interactionSource,
                enabled = !isAnyItemDragging
            )
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            
            // visibility checkbox
            Checkbox(
                checked = trait.visible,
                onCheckedChange = if (isAnyItemDragging) { _ -> } else onToggleVisibility,
                enabled = !isAnyItemDragging,
                colors = CheckboxDefaults.colors(
                    checkedColor = AppTheme.colors.accent,
                    uncheckedColor = AppTheme.colors.accent,
                    checkmarkColor = AppTheme.colors.background,
                )
            )

            Spacer(modifier = Modifier.width(5.dp))

            // format icon
            val formatEnum = Formats.entries.find { it.getDatabaseName() == trait.format }
            AppIcon(
                icon = painterResource(
                    formatEnum?.getIcon() ?: R.drawable.ic_trait_categorical
                ),
            )

            Spacer(modifier = Modifier.width(15.dp))

            // trait alias
            Text(
                text = trait.alias,
                style = AppTheme.typography.bodyStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // drag button
            IconButton(
                onClick = { },
                enabled = !isAnyItemDragging
            ) {
                Icon(
                    Icons.Rounded.DragHandle,
                    contentDescription = null,
                    tint = AppTheme.colors.surface.iconTint
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TraitListItemPreview() {
    val traitObject = TraitObject()
    traitObject.apply {
        alias = "Percent trait"
        format = "percent"
        visible = false
    }

    AppTheme {
        TraitListItem(
            trait = traitObject,
            onClick = { },
            onToggleVisibility = { },
        )
    }
}