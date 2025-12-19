package com.fieldbook.tracker.ui.screens.traits

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.brapi.BrapiTraitActivity
import com.fieldbook.tracker.activities.brapi.io.BrapiFilterCache
import com.fieldbook.tracker.activities.brapi.io.filter.filterer.BrapiTraitFilterActivity
import com.fieldbook.tracker.database.viewmodels.DialogTriggerSource
import com.fieldbook.tracker.database.viewmodels.TraitActivityDialog
import com.fieldbook.tracker.database.viewmodels.TraitEditorEvent
import com.fieldbook.tracker.database.viewmodels.TraitEditorViewModel
import com.fieldbook.tracker.ui.components.buttons.CircularBorderedFab
import com.fieldbook.tracker.ui.screens.traits.dialogs.CreateTraitsDialog
import com.fieldbook.tracker.ui.screens.traits.dialogs.DeleteAllTraitsDialog
import com.fieldbook.tracker.ui.screens.traits.dialogs.ExportCheckDialog
import com.fieldbook.tracker.ui.screens.traits.dialogs.ExportDialog
import com.fieldbook.tracker.ui.screens.traits.dialogs.SortOptionsDialog
import com.fieldbook.tracker.ui.screens.traits.dialogs.TraitImportDialog
import com.fieldbook.tracker.ui.screens.traits.lists.TraitList
import com.fieldbook.tracker.ui.screens.traits.toolbars.TraitEditorToolbar
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.Utils

@Composable
fun TraitEditorScreen(
    viewModel: TraitEditorViewModel,
    onNavigateBack: () -> Unit,
    onTraitDetail: (String) -> Unit,
    onShowCreateNewTraitDialog: () -> Unit,
    onShowLocalFilePicker: () -> Unit,
) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val prefs = PreferenceManager.getDefaultSharedPreferences(context)

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val permissionCallback = remember { mutableStateOf<(() -> Unit)?>(null) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            permissionCallback.value?.invoke()
            permissionCallback.value = null
        } else {
            Utils.makeToast(context, resources.getString(R.string.permission_rationale_storage))
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

    Scaffold(
        topBar = {
            TraitEditorToolbar(
                hasTraits = uiState.hasTraits,
                isTutorialEnabled = viewModel.isTutorialEnabled(),
                onBack = onNavigateBack,
                onToggleAllTraits = { viewModel.toggleAllTraitsVisibility() },
                onShowDialog = { dialog -> viewModel.showDialog(dialog) },
                onRequestExportPermission = { viewModel.requestExportPermission(DialogTriggerSource.TOOLBAR) },
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
    when (val dialog = uiState.activeDialog) {
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
                    viewModel.showExportDialog(DialogTriggerSource.IMPORT_WORKFLOW)
                },
                onSkipExport = {
                    viewModel.hideDialog()
                    viewModel.showDeleteDialog(DialogTriggerSource.IMPORT_WORKFLOW)
                }
            )
        }

        is TraitActivityDialog.Export -> {
            ExportDialog(
                onCancel = { viewModel.handleExportDialogAction(dialog.source) },
                onExport = { fileName ->
                    viewModel.handleExportDialogAction(dialog.source, fileName)
                }
            )
        }

        is TraitActivityDialog.DeleteAll -> {
            DeleteAllTraitsDialog(
                onCancel = { viewModel.handleDeleteDialogAction(dialog.source) },
                onDelete = { viewModel.handleDeleteDialogAction(dialog.source, true) },
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
                is TraitEditorEvent.ShowMessageWithArgs -> {
                    val message = resources.getString(event.resId, *event.args.toTypedArray())
                    Utils.makeToast(context, message)
                }

                is TraitEditorEvent.ShowToast -> {
                    val message = resources.getString(event.resId)
                    Utils.makeToast(context, message)
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

                is TraitEditorEvent.RequestStoragePermissionForExport -> {
                    if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {

                        permissionCallback.value = {
                            viewModel.onExportPermissionGranted(event.source)
                        }

                        permissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    } else {
                        viewModel.onExportPermissionGranted(event.source)
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
                        Utils.makeToast(context, resources.getString(R.string.opening_brapi_no_network_error))
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