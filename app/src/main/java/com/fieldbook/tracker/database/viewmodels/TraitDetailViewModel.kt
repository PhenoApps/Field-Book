package com.fieldbook.tracker.database.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.application.IoDispatcher
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@HiltViewModel
class TraitDetailViewModel @Inject constructor(
    private val repo: TraitRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    companion object {
        private const val TAG = "TraitDetailViewModel"
    }

    private val _uiState = MutableStateFlow<TraitDetailUiState>(TraitDetailUiState.Loading)
    val uiState: StateFlow<TraitDetailUiState> = _uiState

    private val _events = MutableSharedFlow<TraitDetailEvent>()
    val events = _events.asSharedFlow()

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
                    _events.emit(TraitDetailEvent.Error(R.string.error_loading_trait_detail))
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
                _events.emit(TraitDetailEvent.Error(R.string.error_empty_trait_name))
                return@launch
            }

            runCatching { repo.copyTrait(trait, newName) }
                .onSuccess { copiedTrait ->
                    val event = copiedTrait?.let {
                        TraitDetailEvent.CopySuccess(copiedTrait)
                    } ?: TraitDetailEvent.Error(R.string.error_copy_trait)

                    _events.emit(event)
                }
                .onFailure { e ->
                    Log.e(TAG, "Error copying trait: ", e)
                    _events.emit(TraitDetailEvent.Error(R.string.error_copy_trait))
                }
        }
    }
}

sealed class TraitDetailUiState {
    object Loading : TraitDetailUiState()
    data class Success(
        val trait: TraitObject,
        val observationData: ObservationData?,
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
    data class Message(val resId: Int) : TraitDetailEvent()
    object NavigateBack : TraitDetailEvent()
    data class CopySuccess(val trait: TraitObject) : TraitDetailEvent()
    data class Error(val resId: Int) : TraitDetailEvent()
}