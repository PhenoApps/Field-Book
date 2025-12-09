package com.fieldbook.tracker.ui.screens.traits

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldbook.tracker.database.viewmodels.TraitDetailEvent
import com.fieldbook.tracker.database.viewmodels.TraitDetailUiState
import com.fieldbook.tracker.database.viewmodels.TraitDetailViewModel
import com.fieldbook.tracker.database.viewmodels.TraitEditorViewModel
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.ui.toolbars.TraitDetailToolbar
import com.fieldbook.tracker.ui.dialogs.traits.CopyTraitDialog
import com.fieldbook.tracker.ui.dialogs.traits.DeleteTraitDialog
import com.fieldbook.tracker.utilities.TraitNameValidator
import com.fieldbook.tracker.utilities.Utils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TraitDetailScreen(
    traitId: String,
    detailViewModel: TraitDetailViewModel,
    editorViewModel: TraitEditorViewModel,
    onBack: () -> Unit,
    onShowConfigureTraitDialog: (TraitObject?) -> Unit,
    onResourceFilePickerDialog: (onFileSelected: (String) -> Unit) -> Unit,
    onShowParameterEditDialog: (BaseFormatParameter, TraitObject, (TraitObject) -> Unit) -> Unit,
) {
    val editorUiState by editorViewModel.uiState.collectAsState()

    val context = LocalContext.current

    val uiState by detailViewModel.uiState.collectAsStateWithLifecycle()

    val successState = uiState as? TraitDetailUiState.Success
    val currentTrait = successState?.trait

    // prefs
    val isOverviewExpanded = detailViewModel.isOverviewExpanded()
    val isOptionsExpanded = detailViewModel.isOptionsExpanded()
    val isDataExpanded = detailViewModel.isDataExpanded()

    var showDeleteDialog by remember { mutableStateOf(false) }
    var showCopyDialog by remember { mutableStateOf(false) }

    // load trait details initially
    LaunchedEffect(traitId) {
        detailViewModel.loadTraitDetails(traitId)
    }

    Scaffold(
        topBar = {
            TraitDetailToolbar(
                trait = (uiState as? TraitDetailUiState.Success)?.trait,
                onBack = onBack,
                onCopyTrait = {
                    showCopyDialog = true
                },
                onConfigureTrait = {
                    currentTrait?.let { trait ->
                        onShowConfigureTraitDialog(trait)
                    }
                },
                onDeleteTrait = {
                    showDeleteDialog = true
                }
            )
        }
    ) { padding ->
        when (uiState) {
            is TraitDetailUiState.Loading -> {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }

            is TraitDetailUiState.Error -> {
                Text(
                    modifier = Modifier.padding(20.dp),
                    text = stringResource((uiState as TraitDetailUiState.Error).messageRes)
                )
            }

            is TraitDetailUiState.Success -> {
                val trait = (uiState as TraitDetailUiState.Success).trait
                val observation = (uiState as TraitDetailUiState.Success).observationData


                TraitDetailContent(
                    trait = trait,
                    observationData = observation,
                    onUpdateAttributes = { updatedTrait ->
                        detailViewModel.updateAttributes(updatedTrait)
                    },
                    onToggleVisibility = { newVis ->
                        val updatedTrait = trait.clone().apply { visible = newVis }
                        detailViewModel.updateTraitVisibility(trait, newVis)
                        editorViewModel.updateTraitInList(updatedTrait)
                    },
                    onResourceFilePickerDialog = {
                        onResourceFilePickerDialog { filePath ->
                            detailViewModel.updateResourceFile(trait, filePath)
                        }
                    },
                    onUpdateAliasAndAddSynonym = { newAlias ->
                        val updatedTrait = trait.clone().apply { alias = newAlias }
                        detailViewModel.updateTraitAlias(trait, newAlias)
                        editorViewModel.updateTraitInList(updatedTrait)
                    },
                    onValidateSynonym = { synonym ->
                        val validationError = TraitNameValidator.validateTraitAlias(
                            synonym,
                            editorUiState.traits,
                            trait
                        )
                        validationError?.let { errorRes ->
                            context.getString(errorRes)
                        }
                    },
                    onShowParameterEditDialog = { param, traitObj, onUpdated ->
                        onShowParameterEditDialog(param, traitObj) { updatedTrait ->
                            detailViewModel.updateAttributes(updatedTrait)

                            onUpdated(updatedTrait)
                        }
                    },
                    isOverviewExpanded = isOverviewExpanded,
                    isOptionsExpanded = isOptionsExpanded,
                    isDataExpanded = isDataExpanded,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    if (showDeleteDialog) {
        DeleteTraitDialog(
            onCancel = { showDeleteDialog = false },
            onDelete = {
                currentTrait?.let { trait ->
                    detailViewModel.deleteTrait(trait.id)
                    editorViewModel.removeTraitObject(trait.id)
                }
                showDeleteDialog = false
                onBack() // navigate to trait editor
            }
        )
    }
    if (showCopyDialog && currentTrait != null) {
        CopyTraitDialog(
            trait = currentTrait,
            allTraits = editorUiState.traits,
            onCancel = { showCopyDialog = false },
            onCopy = { newName ->
                showCopyDialog = false
                detailViewModel.copyTrait(currentTrait, newName)
            }
        )
    }

    LaunchedEffect(Unit) {
        detailViewModel.events.collect { event ->
            when (event) {
                is TraitDetailEvent.CopySuccess -> {
                    editorViewModel.addTraitObject(event.trait)
                }

                is TraitDetailEvent.Error -> {
                    Utils.makeToast(context, context.getString(event.resId))
                }

                is TraitDetailEvent.Message -> {
                    Utils.makeToast(context, context.getString(event.resId))
                }

                TraitDetailEvent.NavigateBack -> {
                    onBack()
                }
            }
        }
    }
}