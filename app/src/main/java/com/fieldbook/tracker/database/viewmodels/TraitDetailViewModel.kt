package com.fieldbook.tracker.database.viewmodels

import android.content.SharedPreferences
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.application.IoDispatcher
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TraitDetailViewModel @Inject constructor(
    private val repo: TraitRepository,
    private val prefs: SharedPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    companion object {
        private const val TAG = "TraitDetailViewModel"
    }

    private val _uiState = MutableStateFlow<TraitDetailUiState>(TraitDetailUiState.Loading)
    val uiState: StateFlow<TraitDetailUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TraitDetailEvent>()
    val events = _events.asSharedFlow()

    fun isOverviewExpanded(): Boolean {
        return !prefs.getBoolean(GeneralKeys.TRAIT_DETAIL_OVERVIEW_COLLAPSED, false)
    }

    fun isOptionsExpanded(): Boolean {
        return !prefs.getBoolean(GeneralKeys.TRAIT_DETAIL_OPTIONS_COLLAPSED, false)
    }

    fun isDataExpanded(): Boolean {
        return !prefs.getBoolean(GeneralKeys.TRAIT_DETAIL_DATA_COLLAPSED, false)
    }

    fun loadTraitDetails(traitId: String) {
        viewModelScope.launch {
            _uiState.value = TraitDetailUiState.Loading

            runCatching { repo.getTraitById(traitId) }
                .onSuccess { trait ->
                    trait?.let {
                        val observationData = loadObservationData(it)
                        _uiState.value = TraitDetailUiState.Success(it.also {
                            it.loadAttributeAndValues()
                        }, observationData)
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "Error loading trait details: ", e)
                    _uiState.value = TraitDetailUiState.Error(R.string.error_loading_trait_detail)
                }
        }
    }

    fun deleteTrait(traitId: String) {
        viewModelScope.launch {
            runCatching { repo.deleteTrait(traitId) }
                .onFailure { e ->
                    _events.emit(TraitDetailEvent.ShowToast(R.string.error_loading_trait_detail))
                    Log.e(TAG, "Error loading trait details: ", e)
                    _uiState.value = TraitDetailUiState.Error(R.string.error_loading_trait_detail)
                }
        }
    }

    private suspend fun loadObservationData(trait: TraitObject): ObservationData =
        withContext(ioDispatcher) {
            val observations = repo.getTraitObservations(trait.id)
            val fieldsWithObservations = observations.map { it.study_id }.distinct()

            val missingObs = repo.getMissingObservationCount(trait.id)
            val totalObs = observations.size + missingObs

            val completeness =
                if (totalObs > 0) observations.size.toFloat() / totalObs.toFloat() else 0f

            val processedObservations = observations.map { obs ->

                repo.valueFormatter.processValue(obs.value, trait) ?: obs.value

            }

            ObservationData(
                fieldCount = fieldsWithObservations.size,
                observationCount = observations.size,
                completeness = completeness,
                processedObservations = processedObservations
            )
        }

    fun updateTraitVisibility(trait: TraitObject, newVisibility: Boolean) {
        viewModelScope.launch {
            runCatching { repo.updateVisibility(trait.id, newVisibility) }
                .onSuccess {
                    val updated = trait.clone().apply { visible = newVisibility }

                    val obsData = (_uiState.value as? TraitDetailUiState.Success)?.observationData
                    _uiState.value = TraitDetailUiState.Success(updated, obsData)
                }
                .onFailure { e ->
                    Log.e(TAG, "Error updating trait visibility: ", e)
                    _uiState.value = TraitDetailUiState.Error(R.string.error_updating_trait_visibility)
                }
        }
    }

    fun updateResourceFile(trait: TraitObject, fileUri: String) {
        viewModelScope.launch {
            runCatching { repo.updateResourceFile(trait, fileUri) }
                .onSuccess { updatedTrait ->
                    updatedTrait?.let {
                        val obsData = (_uiState.value as? TraitDetailUiState.Success)?.observationData

                        _uiState.value = TraitDetailUiState.Success(updatedTrait, obsData)
                    }
                }
                .onFailure { e ->
                    Log.e(TAG, "Error updating resource file: ", e)
                    _uiState.value = TraitDetailUiState.Error(R.string.error_updating_trait_resource_file)
                }
        }
    }

    fun updateAttributes(trait: TraitObject) {
        viewModelScope.launch {
            runCatching {
                repo.updateAttributes(trait)

                //reload the observations, in case they need reformatting in the graph
                loadObservationData(trait)
            }.onSuccess { obsData ->
                _uiState.value = TraitDetailUiState.Success(trait, obsData)
            }.onFailure { e ->
                Log.e(TAG, "Error updating trait options: ", e)
                _uiState.value = TraitDetailUiState.Error(R.string.error_updating_trait_options)
            }
        }
    }

    /**
     * Sets alias to newAlias, and add it to the synonyms list if it doesn't exist
     */
    fun updateTraitAlias(trait: TraitObject, newAlias: String) {
        viewModelScope.launch {
            runCatching { repo.updateTraitAlias(trait, newAlias) }
                .onSuccess { updatedTrait ->
                    val obsData = (_uiState.value as? TraitDetailUiState.Success)?.observationData
                    _uiState.value = TraitDetailUiState.Success(updatedTrait, obsData)
                }
                .onFailure { e ->
                    Log.e(TAG, "Error updating trait alias: ", e)
                    _uiState.value = TraitDetailUiState.Error(R.string.error_updating_trait_alias)
                }
        }
    }

    fun copyTrait(trait: TraitObject, newName: String) {
        viewModelScope.launch {
            if (newName.isEmpty()) {
                _events.emit(TraitDetailEvent.ShowToast(R.string.error_empty_trait_name))
                return@launch
            }

            runCatching { repo.copyTrait(trait, newName) }
                .onSuccess { copiedTrait ->
                    val event = copiedTrait?.let {
                        TraitDetailEvent.CopySuccess(copiedTrait)
                    } ?: TraitDetailEvent.ShowToast(R.string.error_copy_trait)

                    _events.emit(event)
                }
                .onFailure { e ->
                    Log.e(TAG, "Error copying trait: ", e)
                    _events.emit(TraitDetailEvent.ShowToast(R.string.error_copy_trait))
                }
        }
    }

    fun changeTraitFormat(trait: TraitObject) = repo.changeTraitFormat(trait)

    // DIALOG STATES
    fun showDialog(nextDialog: TraitDetailDialog) {
        _uiState.update { state ->
            if (state is TraitDetailUiState.Success) {
                state.copy(activeDialog = nextDialog)
            } else state
        }
    }

    fun hideDialog() {
        _uiState.update { state ->
            if (state is TraitDetailUiState.Success) {
                state.copy(activeDialog = TraitDetailDialog.None)
            } else state
        }
    }
}

sealed class TraitDetailUiState {
    object Loading : TraitDetailUiState()
    data class Success(
        val trait: TraitObject,
        val observationData: ObservationData?,
        val activeDialog: TraitDetailDialog = TraitDetailDialog.None,
    ) : TraitDetailUiState()
    data class Error(val messageRes: Int) : TraitDetailUiState()
}

data class ObservationData(
    val fieldCount: Int,
    val observationCount: Int,
    val completeness: Float,
    val processedObservations: List<String>,
)

sealed class TraitDetailEvent {
    data class ShowToast(val resId: Int) : TraitDetailEvent()
    object NavigateBack : TraitDetailEvent()
    data class CopySuccess(val trait: TraitObject) : TraitDetailEvent()
}

sealed class TraitDetailDialog {
    object None : TraitDetailDialog()
    object Delete : TraitDetailDialog()
    object Copy : TraitDetailDialog()
}