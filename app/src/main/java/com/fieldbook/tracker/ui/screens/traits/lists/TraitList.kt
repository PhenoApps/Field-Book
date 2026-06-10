package com.fieldbook.tracker.ui.screens.traits.lists

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.ui.screens.traits.listItems.TraitListItem
import com.fieldbook.tracker.ui.theme.AppTheme
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun TraitList(
    traits: List<TraitObject>,
    selectedTraitIds: Set<String> = emptySet(),
    showVisibilityControls: Boolean,
    showRemoveAction: Boolean,
    allowReorder: Boolean,
    onTraitClick: (String) -> Unit,
    onTraitLongClick: (String) -> Unit = {},
    onToggleVisibility: (TraitObject, Boolean) -> Unit,
    onRemoveTrait: (TraitObject) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onDragStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val state = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMoveItem(from.index, to.index)
    }

    // Track drag lifecycle once at list-level so commit is always triggered when dragging ends.
    LaunchedEffect(state.isAnyItemDragging) {
        onDragStateChanged(state.isAnyItemDragging)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier.animateContentSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            count = traits.size,
            key = { index -> traits[index].id }
        ) { index ->
            val trait = traits[index]
            val isSelected = trait.id in selectedTraitIds
            
            if (allowReorder) {
                ReorderableItem(state, key = trait.id) { isDragging ->
                    TraitListItem(
                        position = index + 1,
                        trait = trait,
                        isSelected = isSelected,
                        showVisibilityCheckbox = showVisibilityControls,
                        showRemoveAction = showRemoveAction,
                        showDragHandle = true,
                        onClick = { onTraitClick(trait.id) },
                        onLongClick = { onTraitLongClick(trait.id) },
                        onToggleVisibility = { isVisible -> onToggleVisibility(trait, isVisible) },
                        onRemove = { onRemoveTrait(trait) },
                        isDragging = isDragging,
                        reorderableScope = this@ReorderableItem,
                        isAnyItemDragging = state.isAnyItemDragging,
                    )
                }
            } else {
                TraitListItem(
                    position = index + 1,
                    trait = trait,
                    isSelected = isSelected,
                    showVisibilityCheckbox = showVisibilityControls,
                    showRemoveAction = showRemoveAction,
                    showDragHandle = false,
                    onClick = { onTraitClick(trait.id) },
                    onLongClick = { onTraitLongClick(trait.id) },
                    onToggleVisibility = { isVisible -> onToggleVisibility(trait, isVisible) },
                    onRemove = { onRemoveTrait(trait) },
                    reorderableScope = null,
                    isAnyItemDragging = false,
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun TraitListPreview() {
    val trait1 = TraitObject().apply {
        id = "1"
        alias = "height"
        format = "numeric"
        realPosition = 4
    }
    val trait2 = TraitObject().apply {
        id = "2"
        alias = "lodging"
        format = "percent"
        realPosition = 1
    }

    val traitList = listOf(trait1, trait2)

    AppTheme {
        Box(modifier = Modifier.fillMaxHeight()) {
            TraitList(
                traits = traitList,
                showVisibilityControls = true,
                showRemoveAction = true,
                allowReorder = true,
                onTraitClick = { },
                onToggleVisibility = { _, _ -> },
                onRemoveTrait = { },
                onMoveItem = { _, _ -> },
                onDragStateChanged = { },
                modifier = Modifier,
            )
        }
    }
}