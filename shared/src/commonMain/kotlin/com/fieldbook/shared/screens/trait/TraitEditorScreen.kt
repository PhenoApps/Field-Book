package com.fieldbook.shared.screens.trait

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.fieldbook.shared.components.AppListItem
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.dialog_cancel
import com.fieldbook.shared.generated.resources.dialog_delete_traits_message
import com.fieldbook.shared.generated.resources.dialog_save
import com.fieldbook.shared.generated.resources.ic_file_cloud
import com.fieldbook.shared.generated.resources.ic_file_csv
import com.fieldbook.shared.generated.resources.ic_file_generic
import com.fieldbook.shared.generated.resources.ic_more_vert
import com.fieldbook.shared.generated.resources.ic_reorder
import com.fieldbook.shared.generated.resources.ic_ruler
import com.fieldbook.shared.generated.resources.ic_sort
import com.fieldbook.shared.generated.resources.ic_tb_toggle_all
import com.fieldbook.shared.generated.resources.traits_dialog_export
import com.fieldbook.shared.generated.resources.traits_sort_default
import com.fieldbook.shared.generated.resources.traits_sort_format
import com.fieldbook.shared.generated.resources.traits_sort_import_order
import com.fieldbook.shared.generated.resources.traits_sort_name
import com.fieldbook.shared.generated.resources.traits_sort_visibility
import com.fieldbook.shared.generated.resources.traits_toolbar_delete_all
import com.fieldbook.shared.traits.Formats
import com.fieldbook.shared.utilities.DocumentFile
import com.fieldbook.shared.utilities.getTraitDirectory
import com.fieldbook.shared.utilities.listFiles
import io.github.vinceglb.filekit.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.core.PickerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
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
    val importing by viewModel.importing.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()

    var traitToDelete by remember { mutableStateOf<TraitObject?>(null) }
    var showAddTraitDialog by remember { mutableStateOf(false) }
    var showImportDialog by remember { mutableStateOf(false) }
    var showLocalFilesDialog by remember { mutableStateOf(false) }
    var showCreator by remember { mutableStateOf(false) }
    var traitToEdit by remember { mutableStateOf<TraitObject?>(null) }
    var localTraitFiles by remember { mutableStateOf<List<DocumentFile>>(emptyList()) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var showExportDialog by remember { mutableStateOf(false) }
    var topBarMenuExpanded by remember { mutableStateOf(false) }
    var exportFileName by remember { mutableStateOf(defaultTraitExportName()) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val importFilePicker = rememberFilePickerLauncher(
        type = PickerType.File(extensions = listOf("trt")),
        title = "Import trait file"
    ) { file ->
        if (file != null) {
            scope.launch {
                val bytes = file.readBytes()
                val traitDir = getTraitDirectory()
                if (traitDir != null) {
                    val targetName = uniqueTraitFileName(traitDir, file.name)
                    traitDir.createFile("*/*", targetName)?.writeBytes(bytes)
                }
                viewModel.importTraitsFromBytes(bytes)
            }
        }
        showImportDialog = false
    }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(showLocalFilesDialog) {
        if (showLocalFilesDialog) {
            localTraitFiles = withContext(Dispatchers.Default) { loadLocalTraitFiles() }
        }
    }

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
                actions = {
                    IconButton(onClick = { viewModel.toggleAllVisibility() }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_tb_toggle_all),
                            contentDescription = stringResource(Res.string.traits_sort_visibility)
                        )
                    }
                    IconButton(onClick = { showSortDialog = true }) {
                        Icon(
                            painter = painterResource(Res.drawable.ic_sort),
                            contentDescription = stringResource(Res.string.traits_sort_default)
                        )
                    }
                    Box {
                        IconButton(onClick = { topBarMenuExpanded = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = "More actions"
                            )
                        }
                        DropdownMenu(
                            expanded = topBarMenuExpanded,
                            onDismissRequest = { topBarMenuExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.traits_toolbar_delete_all)) },
                                onClick = {
                                    topBarMenuExpanded = false
                                    showDeleteAllDialog = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(Res.string.traits_dialog_export)) },
                                onClick = {
                                    topBarMenuExpanded = false
                                    exportFileName = defaultTraitExportName()
                                    showExportDialog = true
                                }
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddTraitDialog = true },
                shape = CircleShape
            ) {
                Icon(imageVector = Icons.Filled.Add, contentDescription = "Add")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
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

            if (showAddTraitDialog) {
                AddTraitDialog(
                    onDismiss = { showAddTraitDialog = false },
                    onCreateNew = {
                        showAddTraitDialog = false
                        showCreator = true
                    },
                    onImportFromFile = {
                        showAddTraitDialog = false
                        showImportDialog = true
                    }
                )
            }

            if (showImportDialog) {
                TraitImportDialog(
                    importing = importing,
                    onDismiss = { showImportDialog = false },
                    onPickLocal = {
                        showImportDialog = false
                        showLocalFilesDialog = true
                    },
                    onPickCloud = { importFilePicker.launch() }
                )
            }

            if (showLocalFilesDialog) {
                LocalTraitFilesDialog(
                    files = localTraitFiles,
                    importing = importing,
                    onDismiss = { showLocalFilesDialog = false },
                    onImportFile = { file ->
                        showLocalFilesDialog = false
                        viewModel.importTraitsFromDocumentFile(file)
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

            if (showSortDialog) {
                TraitSortDialog(
                    selectedOption = sortOption,
                    onDismiss = { showSortDialog = false },
                    onSelect = {
                        viewModel.setSortOption(it)
                        showSortDialog = false
                    }
                )
            }

            if (showDeleteAllDialog) {
                AlertDialog(
                    onDismissRequest = { showDeleteAllDialog = false },
                    title = { Text(stringResource(Res.string.traits_toolbar_delete_all)) },
                    text = { Text(stringResource(Res.string.dialog_delete_traits_message)) },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.deleteAllTraits()
                            showDeleteAllDialog = false
                        }) {
                            Text(stringResource(Res.string.traits_toolbar_delete_all))
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDeleteAllDialog = false }) {
                            Text(stringResource(Res.string.dialog_cancel))
                        }
                    }
                )
            }

            if (showExportDialog) {
                TraitExportDialog(
                    defaultFileName = exportFileName,
                    onDismiss = { showExportDialog = false },
                    onExport = { fileName ->
                        viewModel.exportTraits(fileName)
                        showExportDialog = false
                    }
                )
            }
        }
    }
}

@Composable
private fun TraitSortDialog(
    selectedOption: TraitEditorScreenViewModel.TraitSortOption,
    onDismiss: () -> Unit,
    onSelect: (TraitEditorScreenViewModel.TraitSortOption) -> Unit
) {
    val options = listOf(
        TraitEditorScreenViewModel.TraitSortOption.DEFAULT to stringResource(Res.string.traits_sort_default),
        TraitEditorScreenViewModel.TraitSortOption.NAME to stringResource(Res.string.traits_sort_name),
        TraitEditorScreenViewModel.TraitSortOption.FORMAT to stringResource(Res.string.traits_sort_format),
        TraitEditorScreenViewModel.TraitSortOption.IMPORT_ORDER to stringResource(Res.string.traits_sort_import_order),
        TraitEditorScreenViewModel.TraitSortOption.VISIBILITY to stringResource(Res.string.traits_sort_visibility)
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort by") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (option, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(option) },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = option == selectedOption,
                            onClick = { onSelect(option) }
                        )
                        Text(label)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun TraitExportDialog(
    defaultFileName: String,
    onDismiss: () -> Unit,
    onExport: (String) -> Unit
) {
    var fileName by remember(defaultFileName) { mutableStateOf(defaultFileName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.traits_dialog_export)) },
        text = {
            OutlinedTextField(
                value = fileName,
                onValueChange = { fileName = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("File name") }
            )
        },
        confirmButton = {
            TextButton(onClick = { onExport(fileName) }) {
                Text(stringResource(Res.string.dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(Res.string.dialog_cancel))
            }
        }
    )
}

@Composable
private fun AddTraitDialog(
    onDismiss: () -> Unit,
    onCreateNew: () -> Unit,
    onImportFromFile: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Trait(s)") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AppListItem(
                    text = "Create new trait",
                    icon = Res.drawable.ic_ruler,
                    rowModifier = Modifier.clickable(onClick = onCreateNew)
                )
                AppListItem(
                    text = "Import from file",
                    icon = Res.drawable.ic_file_csv,
                    rowModifier = Modifier.clickable(onClick = onImportFromFile)
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun TraitImportDialog(
    importing: Boolean,
    onDismiss: () -> Unit,
    onPickLocal: () -> Unit,
    onPickCloud: () -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!importing) onDismiss()
        },
        title = { Text("Import from file") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (importing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Please wait...")
                    }
                } else {
                    AppListItem(
                        text = "Local storage",
                        icon = Res.drawable.ic_file_generic,
                        rowModifier = Modifier.clickable(onClick = onPickLocal)
                    )
                    AppListItem(
                        text = "Cloud storage",
                        icon = Res.drawable.ic_file_cloud,
                        rowModifier = Modifier.clickable(onClick = onPickCloud),
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !importing) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun LocalTraitFilesDialog(
    files: List<DocumentFile>,
    importing: Boolean,
    onDismiss: () -> Unit,
    onImportFile: (DocumentFile) -> Unit
) {
    AlertDialog(
        onDismissRequest = {
            if (!importing) onDismiss()
        },
        title = { Text("Import") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (importing) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text("Please wait...")
                    }
                } else if (files.isEmpty()) {
                    Text("No trait files found")
                } else {
                    files.forEach { file ->
                        AppListItem(
                            text = file.name().orEmpty(),
                            icon = Res.drawable.ic_file_csv,
                            rowModifier = Modifier.clickable { onImportFile(file) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !importing) {
                Text("Cancel")
            }
        }
    )
}

private fun loadLocalTraitFiles(): List<DocumentFile> {
    val traitDir = getTraitDirectory() ?: return emptyList()
    return listFiles(traitDir)
        .filter { !it.isDirectory() && it.name()?.endsWith(".trt", ignoreCase = true) == true }
        .sortedBy { it.name()?.lowercase().orEmpty() }
}

private fun uniqueTraitFileName(directory: DocumentFile, originalName: String): String {
    val existingNames = listFiles(directory).mapNotNull { it.name() }.toSet()
    if (originalName !in existingNames) return originalName

    val dotIndex = originalName.lastIndexOf('.')
    val baseName = if (dotIndex >= 0) originalName.substring(0, dotIndex) else originalName
    val extension = if (dotIndex >= 0) originalName.substring(dotIndex) else ""

    var index = 1
    while (true) {
        val candidate = "${baseName}_$index$extension"
        if (candidate !in existingNames) return candidate
        index++
    }
}

private fun defaultTraitExportName(): String {
    val local = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    return "trait_export_%04d-%02d-%02d-%02d-%02d-%02d.trt".format(
        local.year,
        local.monthNumber,
        local.dayOfMonth,
        local.hour,
        local.minute,
        local.second
    )
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
