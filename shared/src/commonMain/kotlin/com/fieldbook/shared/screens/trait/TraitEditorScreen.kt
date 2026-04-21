package com.fieldbook.shared.screens.trait

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_more_vert
import com.fieldbook.shared.generated.resources.ic_reorder
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
    val traits by viewModel.traits.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val error by viewModel.error.collectAsState()

    var traitToDelete by remember { mutableStateOf<TraitObject?>(null) }
    var showCreator by remember { mutableStateOf(false) }
    var traitToEdit by remember { mutableStateOf<TraitObject?>(null) }

    val lazyListState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(lazyListState) { from, to ->
        viewModel.moveTrait(from.index, to.index)
    }

    androidx.compose.material3.Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Traits") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
            FloatingActionButton(
                onClick = { showCreator = true },
                shape = CircleShape
            ) {
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
                    LazyColumn(
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
                                androidx.compose.material3.Surface(shadowElevation = elevation) {
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
                                            .height(40.dp),
                                        onEditClick = {
                                            traitToEdit = viewModel.getTraitForEdit(it.id)
                                        },
                                        onCopyClick = { viewModel.copyTrait(it) },
                                        onDeleteClick = { traitToDelete = it }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Delete confirmation dialog
            if (traitToDelete != null) {
                val trait = traitToDelete!!
                AlertDialog(
                    onDismissRequest = { traitToDelete = null },
                    title = { Text("Delete trait") },
                    text = { Text("Are you sure you want to delete '${trait.name}'?") },
                    confirmButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            viewModel.deleteTrait(trait.id)
                            traitToDelete = null
                        }) {
                            Text("Delete", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    dismissButton = {
                        androidx.compose.material3.TextButton(onClick = {
                            traitToDelete = null
                        }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            if (showCreator) {
                TraitCreatorDialog(
                    onDismiss = { showCreator = false },
                    onSuccess = { showCreator = false }
                )
            }

            if (traitToEdit != null) {
                TraitCreatorDialog(
                    initialTrait = traitToEdit,
                    onDismiss = { traitToEdit = null },
                    onSuccess = { traitToEdit = null }
                )
            }
        }
    }
}

@Composable
fun TraitListItem(
    trait: TraitObject,
    onToggleVisible: (Boolean) -> Unit,
    dragModifier: Modifier = Modifier,
    onEditClick: (TraitObject) -> Unit = {},
    onCopyClick: (TraitObject) -> Unit = {},
    onDeleteClick: (TraitObject) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val iconRes = Formats.findTrait(trait.format ?: "")?.iconDrawableResource

    var menuOpen by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSurface,
                shape = RoundedCornerShape(8.dp)
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = dragModifier
                .size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_reorder),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
        }

        Box(
            modifier = Modifier
                .size(40.dp)
                .clickable { menuOpen = true },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(Res.drawable.ic_more_vert),
                contentDescription = "More",
                modifier = Modifier.size(24.dp)
            )

            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = {
                    menuOpen = false
                    onEditClick(trait)
                })
                DropdownMenuItem(text = { Text("Copy") }, onClick = {
                    menuOpen = false
                    onCopyClick(trait)
                })
                DropdownMenuItem(text = { Text("Delete") }, onClick = {
                    menuOpen = false
                    onDeleteClick(trait)
                })
            }
        }

        Checkbox(
            checked = trait.visible == null || trait.visible == "true",
            onCheckedChange = onToggleVisible,
        )

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
