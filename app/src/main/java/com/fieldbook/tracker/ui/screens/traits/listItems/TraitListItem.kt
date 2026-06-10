package com.fieldbook.tracker.ui.screens.traits.listItems

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
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
    position: Int,
    trait: TraitObject,
    isSelected: Boolean = false,
    showVisibilityCheckbox: Boolean,
    showRemoveAction: Boolean,
    showDragHandle: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit = {},
    onToggleVisibility: (Boolean) -> Unit,
    onRemove: () -> Unit,
    isDragging: Boolean = false,
    isAnyItemDragging: Boolean = false, // used disable interactions when something is being dragged
    reorderableScope: ReorderableCollectionItemScope? = null,
    modifier: Modifier = Modifier,
) {
    val hapticFeedback = LocalHapticFeedback.current

    val interactionSource = remember { MutableInteractionSource() }

    val elevation by animateDpAsState(if (isDragging) 6.dp else 0.dp, label = "elevation")
    val scale by animateFloatAsState(if (isDragging) 1.02f else 1f, label = "scale")

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (isDragging) 0.95f else 1f
                shadowElevation = elevation.toPx()
                shape = RoundedCornerShape(12.dp)
                clip = true
            }
            .then(
                if (reorderableScope != null && !showDragHandle) {
                    with(reorderableScope) {
                        Modifier.longPressDraggableHandle( // reorder only on long press if no handle
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
            .combinedClickable(
                onClick = onClick,
                onLongClick = {
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.LongPress)
                    onLongClick()
                },
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                enabled = !isAnyItemDragging
            ),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) AppTheme.colors.interactive.selectedItemBackground else AppTheme.colors.background,
        border = androidx.compose.foundation.BorderStroke(
            if (isSelected) 1.dp else 0.dp,
            if (isSelected) AppTheme.colors.primary else AppTheme.colors.surface.border
        ),
        shadowElevation = 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Text(
                text = "$position",
                modifier = Modifier.width(16.dp),
                style = AppTheme.typography.subheadingStyle,
                color = if (isSelected) AppTheme.colors.primary else AppTheme.colors.text.secondary,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.width(4.dp))

            if (showVisibilityCheckbox) {
                // visibility checkbox
                Checkbox(
                    checked = trait.visible,
                    onCheckedChange = { onToggleVisibility(it) },
                    enabled = !isAnyItemDragging,
                    colors = CheckboxDefaults.colors(
                        checkedColor = AppTheme.colors.accent,
                        uncheckedColor = AppTheme.colors.accent,
                        checkmarkColor = AppTheme.colors.background,
                    )
                )

                Spacer(modifier = Modifier.width(2.dp))
            }

            // format icon
            val formatEnum = Formats.entries.find { it.getDatabaseName() == trait.format }
            AppIcon(
                icon = painterResource(
                    formatEnum?.getIcon() ?: R.drawable.ic_trait_categorical
                ),
            )

            Spacer(modifier = Modifier.width(15.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = trait.alias,
                    style = AppTheme.typography.bodyStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = trait.format,
                    style = AppTheme.typography.subheadingStyle,
                    color = AppTheme.colors.text.secondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (showDragHandle) {
                // drag button
                IconButton(
                    onClick = { },
                    enabled = !isAnyItemDragging,
                    modifier = if (reorderableScope != null) {
                        with(reorderableScope) {
                            Modifier.longPressDraggableHandle(
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
                ) {
                    Icon(
                        Icons.Rounded.DragHandle,
                        contentDescription = null,
                        tint = if (isDragging) AppTheme.colors.primary else AppTheme.colors.surface.iconTint
                    )
                }
            }

//            if (showRemoveAction) {
//                IconButton(
//                    onClick = onRemove,
//                    enabled = !isAnyItemDragging
//                ) {
//                    Icon(
//                        imageVector = Icons.Filled.Close,
//                        contentDescription = null,
//                        tint = AppTheme.colors.surface.iconTint
//                    )
//                }
//            }
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
            position = 1,
            trait = traitObject,
            showVisibilityCheckbox = true,
            showRemoveAction = true,
            showDragHandle = true,
            onClick = { },
            onToggleVisibility = { },
            onRemove = { },
        )
    }
}