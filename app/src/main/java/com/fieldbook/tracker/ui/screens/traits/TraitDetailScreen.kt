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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fieldbook.tracker.database.viewmodels.TraitDetailDialog
import com.fieldbook.tracker.database.viewmodels.TraitDetailEvent
import com.fieldbook.tracker.database.viewmodels.TraitDetailUiState
import com.fieldbook.tracker.database.viewmodels.TraitDetailViewModel
import com.fieldbook.tracker.database.viewmodels.TraitEditorViewModel
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.parameters.BaseFormatParameter
import com.fieldbook.tracker.ui.screens.traits.toolbars.TraitDetailToolbar
import com.fieldbook.tracker.ui.screens.traits.dialogs.CopyTraitDialog
import com.fieldbook.tracker.ui.screens.traits.dialogs.DeleteTraitDialog
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
    val resources = LocalResources.current

    val uiState by detailViewModel.uiState.collectAsStateWithLifecycle()

    val successState = uiState as? TraitDetailUiState.Success
    val currentTrait = successState?.trait

    // prefs
    val isOverviewExpanded = detailViewModel.isOverviewExpanded()
    val isOptionsExpanded = detailViewModel.isOptionsExpanded()
    val isDataExpanded = detailViewModel.isDataExpanded()

    // load trait details initially
    LaunchedEffect(traitId) {
        detailViewModel.loadTraitDetails(traitId)
    }

    Scaffold(
        topBar = {
            TraitDetailToolbar(
                trait = (uiState as? TraitDetailUiState.Success)?.trait,
                onBack = onBack,
                onCopyTrait = { detailViewModel.showDialog(TraitDetailDialog.Copy) },
                onConfigureTrait = {
                    currentTrait?.let { trait ->
                        onShowConfigureTraitDialog(trait)
                    }
                },
                onDeleteTrait = { detailViewModel.showDialog(TraitDetailDialog.Delete) }
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
                            resources.getString(errorRes)
                        }
                    },
                    onShowParameterEditDialog = { param, traitObj, onUpdated ->
                        onShowParameterEditDialog(param, traitObj) { updatedTrait ->
                            detailViewModel.updateAttributes(updatedTrait)

                            onUpdated(updatedTrait)
                        }
                    },
                    onEditFormat = if ((observation?.observationCount ?: 0) == 0) {
                        {
                            val newTrait = detailViewModel.changeTraitFormat(trait)
                            onShowConfigureTraitDialog(newTrait)
                        }
                    } else null,
                    isOverviewExpanded = isOverviewExpanded,
                    isOptionsExpanded = isOptionsExpanded,
                    isDataExpanded = isDataExpanded,
                    modifier = Modifier.padding(padding),
                )
            }
        }
    }

    // observe for active dialog
    when (successState?.activeDialog) {
        TraitDetailDialog.Delete -> {
            DeleteTraitDialog(
                onCancel = { detailViewModel.hideDialog() },
                onDelete = {
                    currentTrait?.let { trait ->
                        detailViewModel.deleteTrait(trait.id)
                        editorViewModel.removeTraitObject(trait.id)
                    }
                    detailViewModel.hideDialog()
                    onBack()
                }
            )
        }

        TraitDetailDialog.Copy -> {
            currentTrait?.let { trait ->
                CopyTraitDialog(
                    trait = trait,
                    allTraits = editorUiState.traits,
                    onCancel = { detailViewModel.hideDialog() },
                    onCopy = { newName ->
                        detailViewModel.hideDialog()
                        detailViewModel.copyTrait(trait, newName)
                    }
                )
            }
        }

        else -> Unit
    }

    LaunchedEffect(Unit) {
        detailViewModel.events.collect { event ->
            when (event) {
                is TraitDetailEvent.CopySuccess -> {
                    editorViewModel.addTraitObject(event.trait)
                }

                is TraitDetailEvent.ShowToast -> {
                    Utils.makeToast(context, resources.getString(event.resId))
                }

                TraitDetailEvent.NavigateBack -> { onBack() }
            }
        }
    }
}