package com.fieldbook.tracker.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.BrapiTraitActivity
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterCache
import com.fieldbook.tracker.activities.brapi.io.filter.filterer.BrapiTraitFilterActivity
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.ui.dialogs.traits.DeleteAllTraitsDialog
import com.fieldbook.tracker.ui.dialogs.traits.ExportDialog
import com.fieldbook.tracker.ui.dialogs.traits.SortOptionsDialog
import com.fieldbook.tracker.ui.dialogs.traits.CreateTraitsDialog
import com.fieldbook.tracker.ui.dialogs.traits.ExportCheckDialog
import com.fieldbook.tracker.ui.dialogs.traits.TraitImportDialog
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.ui.components.appBar.ActionDisplayMode
import com.fieldbook.tracker.ui.components.appBar.AppBar
import com.fieldbook.tracker.ui.components.appBar.TopAppBarAction
import com.fieldbook.tracker.ui.components.buttons.CircularBorderedFab
import com.fieldbook.tracker.ui.lists.TraitList
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.database.viewmodels.TraitActivityDialog
import com.fieldbook.tracker.database.viewmodels.DeleteTriggerSource
import com.fieldbook.tracker.database.viewmodels.ExportTriggerSource
import com.fieldbook.tracker.database.viewmodels.TraitEditorEvent
import com.fieldbook.tracker.database.viewmodels.TraitEditorViewModel
import kotlinx.coroutines.Dispatchers

@Composable
fun TraitEditorScreen(
    viewModel: TraitEditorViewModel,
    onNavigateBack: () -> Unit,
    onTraitDetail: (String) -> Unit,
    onShowCreateNewTraitDialog: () -> Unit,
    onShowLocalFilePicker: () -> Unit,
) {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val exportTriggerSource by viewModel.exportTriggerSource.collectAsStateWithLifecycle()
    val deleteTriggerSource by viewModel.deleteTriggeredSource.collectAsStateWithLifecycle()

    val permissionCallback = remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionCallback.value?.invoke()
            permissionCallback.value = null
        } else {
            Utils.makeToast(context, "Storage permission denied")
        }
    }

    val cloudFileImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val uri = result.data?.data
        if (result.resultCode == Activity.RESULT_OK && uri != null) {
            viewModel.importTraits(uri)
        }
    }

    val brapiLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        viewModel.loadTraits()
    }

    val appBarActions = buildList {
        // if (viewModel.isTutorialEnabled()) {
        //     add(
        //         TopAppBarAction(
        //             title = stringResource(R.string.tutorial_dialog_title),
        //             contentDescription = "Help",
        //             icon = Icons.AutoMirrored.Filled.Help,
        //             displayMode = ActionDisplayMode.ALWAYS,
        //             onClick = {
        //                 // TODO add tutorial
        //             }
        //         )
        //     )
        // }

        if (uiState.hasTraits) {
            addAll(
                listOf(
                    TopAppBarAction(
                        title = stringResource(R.string.traits_sort_visibility),
                        contentDescription = stringResource(R.string.traits_sort_visibility),
                        icon = R.drawable.ic_tb_toggle_all,
                        displayMode = ActionDisplayMode.ALWAYS,
                        onClick = {
                            viewModel.toggleAllTraitsVisibility()
                        }
                    ),
                    TopAppBarAction(
                        title = stringResource(R.string.traits_toolbar_sort),
                        contentDescription = stringResource(R.string.traits_toolbar_sort),
                        icon = R.drawable.ic_sort,
                        displayMode = ActionDisplayMode.ALWAYS,
                        onClick = {
                            viewModel.showDialog(TraitActivityDialog.SortTraits)
                        }
                    ),
                    TopAppBarAction(
                        title = stringResource(R.string.traits_toolbar_delete_all),
                        contentDescription = stringResource(R.string.traits_toolbar_delete_all),
                        icon = Icons.Filled.Delete,
                        displayMode = ActionDisplayMode.IF_ROOM,
                        onClick = {
                            viewModel.showDeleteDialog(DeleteTriggerSource.TOOLBAR)
                        }
                    ),
                    TopAppBarAction(
                        title = stringResource(R.string.traits_dialog_export),
                        contentDescription = stringResource(R.string.traits_dialog_export),
                        icon = Icons.Filled.FileDownload,
                        displayMode = ActionDisplayMode.IF_ROOM,
                        onClick = {
                            viewModel.requestExportPermission()
                        }
                    ),
                )
            )
        }
    }

    Scaffold(
        topBar = {
            AppBar(
                title = stringResource(R.string.settings_traits),
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_left),
                            contentDescription = stringResource(R.string.appbar_back)
                        )
                    }
                },
                actions = appBarActions,
            )
        },
        floatingActionButton = {
            CircularBorderedFab(
                onClick = { viewModel.showDialog(TraitActivityDialog.NewTrait) },
                icon = Icons.Filled.Add,
                contentDescription = stringResource(R.string.traits_new_dialog_title)
            )
        }
    ) { paddingValues ->

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .align(Alignment.Center)
                    )
                }

                else -> {
                    TraitList(
                        traits = uiState.traits,
                        onTraitClick = onTraitDetail,
                        onToggleVisibility = { trait, isVisible ->
                            viewModel.updateTraitVisibility(trait.id, isVisible)
                        },
                        onMoveItem = { from, to ->
                            viewModel.moveTraitItem(from, to)
                        },
                        onDragStateChanged = { dragging ->
                            viewModel.onDragStateChanged(dragging)
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }

    // observe for active dialog
    when (uiState.activeDialog) {
        TraitActivityDialog.NewTrait -> {
            val defaultBrapiName = stringResource(R.string.brapi_edit_display_name_default)

            CreateTraitsDialog(
                onDialogClose = { viewModel.hideDialog() },
                onCreateNew = {
                    viewModel.hideDialog()
                    onShowCreateNewTraitDialog()
                },
                onImportFromFile = {
                    viewModel.hideDialog()
                    viewModel.requestImportPermission()
                },
                onImportFromBrapi = {
                    viewModel.hideDialog()
                    viewModel.openBrapiActivity()
                },
                isBrapiEnabled = viewModel.isBrapiEnabled(),
                brapiDisplayName = viewModel.getBrapiDisplayName(defaultBrapiName)
            )
        }

        TraitActivityDialog.ImportChoice -> {
            TraitImportDialog(
                onDialogClose = { viewModel.hideDialog() },
                onLocalFile = {
                    viewModel.hideDialog()
                    viewModel.openLocalFilePicker()
                },
                onCloudFile = {
                    viewModel.hideDialog()
                    viewModel.openCloudPicker()
                }
            )
        }

        TraitActivityDialog.ExportCheck -> {
            // triggered during IMPORT_WORKFLOW

            // if user chooses export, show ExportDialog -> DeleteAllDialog -> Import Local/Cloud file
            // if user skips export, show DeleteAllDialog -> Import Local/Cloud file
            ExportCheckDialog(
                onConfirmExport = {
                    viewModel.hideDialog()
                    viewModel.showExportDialog(ExportTriggerSource.IMPORT_WORKFLOW)
                },
                onSkipExport = {
                    viewModel.hideDialog()
                    viewModel.showDeleteDialog(DeleteTriggerSource.IMPORT_WORKFLOW)
                }
            )
        }

        TraitActivityDialog.Export -> {
            // can be triggered during IMPORT_WORKFLOW OR via TOOLBAR

            // if triggered via import workflow, show DeleteAllDialog -> Import Local/Cloud file
            ExportDialog(
                onCancel = {
                    viewModel.hideDialog()

                    if (exportTriggerSource == ExportTriggerSource.IMPORT_WORKFLOW) {
                        viewModel.showDeleteDialog(DeleteTriggerSource.IMPORT_WORKFLOW)
                    }

                    viewModel.clearExportTrigger()
                },
                onExport = { fileName ->
                    viewModel.hideDialog()
                    viewModel.exportTraits(fileName)

                    if (exportTriggerSource == ExportTriggerSource.IMPORT_WORKFLOW) {
                        viewModel.showDeleteDialog(DeleteTriggerSource.IMPORT_WORKFLOW)
                    }

                    viewModel.clearExportTrigger()
                }
            )
        }

        TraitActivityDialog.DeleteAll -> {
            // can be triggered during IMPORT_WORKFLOW OR via TOOLBAR

            // if triggered via IMPORT_WORKFLOW, show Import Local/Cloud file
            DeleteAllTraitsDialog(
                onDelete = {
                    viewModel.deleteAllTraits()
                    viewModel.hideDialog()

                    if (deleteTriggerSource == DeleteTriggerSource.IMPORT_WORKFLOW) {
                        viewModel.showDialog(TraitActivityDialog.ImportChoice)
                    }

                    viewModel.clearDeleteTrigger()
                },
                onCancel = {
                    viewModel.hideDialog()

                    if (deleteTriggerSource == DeleteTriggerSource.IMPORT_WORKFLOW) {
                        viewModel.showDialog(TraitActivityDialog.ImportChoice)
                    }

                    viewModel.clearDeleteTrigger()
                }
            )
        }


        TraitActivityDialog.SortTraits -> {
            SortOptionsDialog(
                currentSortOrder = uiState.sortOrder,
                onCancel = { viewModel.hideDialog() },
                onSortSelected = { sortOrder ->
                    viewModel.updateSortOrder(sortOrder)
                    viewModel.hideDialog()
                }
            )
        }

        else -> Unit
    }

    // observe for errors, toasts, navigation
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TraitEditorEvent.ShowMessage -> {
                    Utils.makeToast(context, event.message)
                }

                is TraitEditorEvent.ShowError -> {
                    Utils.makeToast(context, event.message)
                }

                TraitEditorEvent.RequestStoragePermissionForImport -> {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {

                        permissionCallback.value = {
                            viewModel.onImportPermissionGranted()
                        }

                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    } else {
                        viewModel.onImportPermissionGranted()
                    }
                }

                TraitEditorEvent.RequestStoragePermissionForExport -> {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {

                        permissionCallback.value = {
                            viewModel.onExportPermissionGranted()
                        }

                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    } else {
                        viewModel.onExportPermissionGranted()
                    }
                }

                TraitEditorEvent.OpenCloudFilePicker -> {
                    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "*/*"
                    }
                    cloudFileImportLauncher.launch(intent)
                }

                TraitEditorEvent.NavigateToBrapi -> {
                    if (!Utils.isConnected(context)) {
                        Utils.makeToast(context, context.getString(R.string.opening_brapi_no_network_error))
                        return@collect
                    }

                    val intent = if (viewModel.isBrapiNewUi()) {
                        Intent(context, BrapiTraitFilterActivity::class.java).apply {
                            BrapiFilterCache.checkClearCache(context)
                        }
                    } else {
                        Intent(context, BrapiTraitActivity::class.java)
                    }

                    brapiLauncher.launch(intent)
                }

                TraitEditorEvent.OpenFileExplorer -> {
                    onShowLocalFilePicker()
                }

                is TraitEditorEvent.ShareFile -> {
                    val doc = DocumentFile.fromSingleUri(context, event.fileUri)
                    FileUtil.shareFile(context, prefs, doc)
                }
            }
        }
    }
}

@SuppressLint("ViewModelConstructorInComposable")
@Preview
@Composable
private fun TraitEditorScreenPreview() {
    val context = LocalContext.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    val db = DataHelper(context)

    val viewModel = TraitEditorViewModel(
        repo = TraitRepository(
            context = context,
            database = db,
            prefs = prefs,
            ioDispatcher = Dispatchers.IO
        ),
        prefs = prefs,
    )

    AppTheme {
        TraitEditorScreen(
            viewModel = viewModel,
            onNavigateBack = { },
            onTraitDetail = { },
            onShowCreateNewTraitDialog = { },
            onShowLocalFilePicker = { },
        )
    }
}