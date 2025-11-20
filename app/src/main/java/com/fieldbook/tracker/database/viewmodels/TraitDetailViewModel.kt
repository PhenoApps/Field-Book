package com.fieldbook.tracker.database.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.application.IoDispatcher
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.export.ValueProcessorFormatAdapter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
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

    private val _uiState = MutableLiveData<TraitDetailUiState>()
    val uiState: LiveData<TraitDetailUiState> = _uiState

    private val _copyTraitStatus = MutableLiveData<CopyTraitStatus>()
    val copyTraitStatus: LiveData<CopyTraitStatus> = _copyTraitStatus

    data class ObservationData(
        val fieldCount: Int,
        val observationCount: Int,
        val completeness: Float,
        val processedObservations: List<String>,
    )

    fun loadTraitDetails(valueFormatter: ValueProcessorFormatAdapter, traitId: String) {
        viewModelScope.launch {
            _uiState.value = TraitDetailUiState.Loading

            runCatching { repo.getTraitById(traitId) }
                .onSuccess { trait ->
                    trait?.let {
                        val observationData = loadObservationData(valueFormatter, it)
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

    private suspend fun loadObservationData(valueFormatter: ValueProcessorFormatAdapter, trait: TraitObject): ObservationData =
        withContext(ioDispatcher) {
            val observations = repo.getTraitObservations(trait.id)
            val fieldsWithObservations = observations.map { it.study_id }.distinct()

            val missingObs = repo.getMissingObservationCount(trait.id)
            val totalObs = observations.size + missingObs

            val completeness =
                if (totalObs > 0) observations.size.toFloat() / totalObs.toFloat() else 0f

            val processedObservations = observations.map { obs ->

                valueFormatter.processValue(obs.value, trait) ?: obs.value

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
                    trait.visible = newVisibility

                    val obsData = (_uiState.value as? TraitDetailUiState.Success)?.observationData
                    _uiState.value = TraitDetailUiState.Success(trait, obsData)
                }
                .onFailure { e ->
                    Log.e(TAG, "Error updating trait visibility: ", e)
                    _uiState.value = TraitDetailUiState.Error(R.string.error_updating_trait_visibility)
                }
        }
    }

    fun updateResourceFile(traitId: String, fileUri: String) {
        viewModelScope.launch {
            runCatching { repo.updateResourceFile(traitId, fileUri) }
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

    fun updateTraitAttributes(valueFormatter: ValueProcessorFormatAdapter, trait: TraitObject) {
        viewModelScope.launch {
            runCatching {
                repo.updateTraitAndAttributes(trait)

                //reload the observations, in case they need reformatting in the graph
                loadObservationData(valueFormatter, trait)
            }.onSuccess { obsData ->
                _uiState.value = TraitDetailUiState.Success(trait, obsData)
            }.onFailure { e ->
                Log.e(TAG, "Error updating trait options: ", e)
                _uiState.value = TraitDetailUiState.Error(R.string.error_updating_trait_options)
            }
        }
    }

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
        if (newName.isEmpty()) {
            _copyTraitStatus.value = CopyTraitStatus.Error(R.string.error_empty_trait_name)
            return
        }

        viewModelScope.launch {
            runCatching { repo.copyTrait(trait, newName) }
                .onSuccess { success ->
                    _copyTraitStatus.value =
                        if (success) CopyTraitStatus.Success(newName) else CopyTraitStatus.Error(R.string.error_copy_trait)
                }
                .onFailure { e ->
                    Log.e(TAG, "Error copying trait: ", e)
                    _copyTraitStatus.value = CopyTraitStatus.Error(R.string.error_copy_trait)
                }
        }
    }
}

sealed class TraitDetailUiState {
    object Loading : TraitDetailUiState()
    data class Success(
        val trait: TraitObject,
        val observationData: TraitDetailViewModel.ObservationData?
    ) : TraitDetailUiState()
    data class Error(val messageRes: Int) : TraitDetailUiState()
}

sealed class CopyTraitStatus {
    data class Success(val newName: String): CopyTraitStatus()
    data class Error(val messageRes: Int): CopyTraitStatus()
}