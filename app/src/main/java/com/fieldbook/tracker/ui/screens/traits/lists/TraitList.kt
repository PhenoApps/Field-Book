package com.fieldbook.tracker.ui.screens.traits.lists

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
    onTraitClick: (String) -> Unit,
    onToggleVisibility: (TraitObject, Boolean) -> Unit,
    onMoveItem: (Int, Int) -> Unit,
    onDragStateChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val lazyListState = rememberLazyListState()
    val state = rememberReorderableLazyListState(lazyListState) { from, to ->
        onMoveItem(from.index, to.index)
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(
            count = traits.size,
            key = { index -> traits[index].id }
        ) { index ->
            val trait = traits[index]
            ReorderableItem(state, key = trait.id) { isDragging ->
                LaunchedEffect(isDragging) {
                    onDragStateChanged(isDragging)
                }
                TraitListItem(
                    trait = trait,
                    onClick = { onTraitClick(trait.id) },
                    onToggleVisibility = { isVisible -> onToggleVisibility(trait, isVisible) },
                    reorderableScope = this@ReorderableItem,
                    isAnyItemDragging = state.isAnyItemDragging,
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
                onTraitClick = { },
                onToggleVisibility = { _, _ -> },
                onMoveItem = { _, _ -> },
                onDragStateChanged = { },
                modifier = Modifier,
            )
        }
    }
}