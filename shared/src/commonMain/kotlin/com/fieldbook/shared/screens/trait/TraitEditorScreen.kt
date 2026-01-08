package com.fieldbook.shared.screens.trait

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fieldbook.shared.theme.MainTheme
import com.fieldbook.shared.traits.Formats
import org.jetbrains.compose.resources.painterResource
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraitEditorScreen(
    onBack: (() -> Unit)? = null,
    viewModel: TraitEditorScreenViewModel = viewModel()
) {
    MainTheme {
        val traits by viewModel.traits.collectAsState()
        val loading by viewModel.loading.collectAsState()
        val error by viewModel.error.collectAsState()

        val lazyListState = rememberLazyListState()
        val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
            viewModel.moveTrait(from.index, to.index)
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Traits") },
                    navigationIcon = {
                        if (onBack != null) {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        titleContentColor = MaterialTheme.colorScheme.onPrimary,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { /* no-op for now */ }) {
                    Icon(imageVector = Icons.Filled.Add, contentDescription = "Add")
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(MaterialTheme.colorScheme.background)
            ) {

                when {
                    loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }

                    error != null -> {
                        Text(
                            text = "Error: ${error}",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.error
                        )
                    }

                    traits.isEmpty() -> {
                        Text(text = "No traits", modifier = Modifier.align(Alignment.Center))
                    }

                    else -> {
                        androidx.compose.foundation.lazy.LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            state = lazyListState,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(traits, key = { it.id ?: it.name }) { trait ->
                                ReorderableItem(
                                    reorderState,
                                    key = trait.id ?: trait.name
                                ) { isDragging ->
                                    val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
                                    Surface(shadowElevation = elevation) {
                                        // 'this' is ReorderableCollectionItemScope here; provide draggable handle modifier
                                        val dragModifier = with(this) { Modifier.draggableHandle() }
                                        TraitListItem(
                                            trait = trait,
                                            onToggleVisible = { visible ->
                                                viewModel.toggleVisibility(
                                                    trait.id,
                                                    visible
                                                )
                                            },
                                            dragModifier = dragModifier,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 8.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TraitListItem(
    trait: com.fieldbook.shared.database.models.TraitObject,
    onToggleVisible: (Boolean) -> Unit,
    dragModifier: Modifier = Modifier,
    onStartDrag: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val iconRes = Formats.findTrait(trait.format ?: "")?.iconDrawableResource

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(8.dp)
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            modifier = dragModifier,
            onClick = {}
        ) {
            Text("≡")
        }

        Spacer(modifier = Modifier.width(8.dp))

        androidx.compose.material3.Checkbox(
            checked = trait.visible == null || trait.visible == "true",
            onCheckedChange = onToggleVisible,
            modifier = Modifier.padding(4.dp)
        )

        Spacer(modifier = Modifier.width(8.dp))

        if (iconRes != null) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = "Trait Icon",
                modifier = Modifier.size(24.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(text = trait.name, modifier = Modifier.weight(1f))
    }
}
