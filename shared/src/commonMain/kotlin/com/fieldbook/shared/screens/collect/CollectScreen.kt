package com.fieldbook.shared.screens.collect

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.ic_field
import com.fieldbook.shared.screens.collect.traits.PhotoTrait
import com.fieldbook.shared.screens.collect.traits.PhotoTraitDisplayMode
import com.fieldbook.shared.screens.datagrid.DataGridScreen
import com.fieldbook.shared.traits.Formats
import org.jetbrains.compose.resources.painterResource

/**
 * KMP version of CollectActivity main screen logic.
 * UI and business logic will be migrated here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectScreen(
    modifier: Modifier = Modifier,
    controller: CollectScreenController = remember { CollectScreenController() },
    onBack: (() -> Unit)? = null,
) {
    var isCameraFullscreen by remember { mutableStateOf(false) }
    var showDataGrid by remember { mutableStateOf(false) }
    val handleBack: () -> Unit = {
        controller.persistCurrentSelection()
        onBack?.invoke()
    }

    DisposableEffect(controller) {
        onDispose {
            controller.persistCurrentSelection()
        }
    }

    val currentTrait = controller.traits.getOrNull(controller.currentTraitIndex)
    val currentValues = currentTrait?.let { controller.traitValues[it.id] } ?: emptyList()
    val currentFormat = currentTrait?.format?.let { formatStr ->
        Formats.entries.find { it.databaseName.equals(formatStr, ignoreCase = true) }
    }
    val isCurrentTraitCamera = currentFormat?.isCamera == true

    if (isCameraFullscreen && isCurrentTraitCamera) {
        Surface(modifier = modifier.fillMaxSize()) {
            PhotoTrait(
                values = currentValues,
                onPhotoCaptured = { controller.addCurrentTraitValue(it) },
                onPhotoDeleted = { controller.deleteCurrentTraitValue(it) },
                modifier = Modifier.fillMaxSize(),
                controller = controller,
                displayMode = PhotoTraitDisplayMode.FULLSCREEN,
                onCollapseRequest = { isCameraFullscreen = false }
            )
        }
        return
    }

    if (showDataGrid) {
        DataGridScreen(
            modifier = modifier,
            activePlotIndex = controller.currentUnitIndex + 1,
            activeTraitIndex = controller.currentTraitIndex + 1,
            onBack = { showDataGrid = false },
            onSelection = { selection ->
                controller.applyDataGridSelection(selection)
                showDataGrid = false
            }
        )
        return
    }

    Surface(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = { Text(text = "Collect Data") },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = handleBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showDataGrid = true }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_field),
                            contentDescription = "Data Grid"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
            if (controller.unitLoading || controller.traitLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (controller.unitError != null || controller.traitError != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Error: ${controller.unitError ?: controller.traitError}")
                }
            } else if (controller.units.isNotEmpty() && controller.traits.isNotEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(8.dp))
                    InfoBar(controller = controller)
                    Spacer(Modifier.height(8.dp))
                    TraitBox(
                        viewModel = controller,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    RangeBox(controller = controller)
                    CollectInput(
                        controller = controller,
                        modifier = Modifier.weight(1f),
                        onExpandPhotoTrait = { isCameraFullscreen = true }
                    )
                }
            }
        }
    }
}
