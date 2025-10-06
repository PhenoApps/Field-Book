package com.fieldbook.tracker.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.export.ValueProcessorFormatAdapter
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class TraitDetailViewModel(
    private val database: DataHelper,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
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
            try {
                val trait = withContext(ioDispatcher) { database.getTraitById(traitId) }

                trait?.let {
                    val observationData = loadObservationData(valueFormatter, it)
                    _uiState.value = TraitDetailUiState.Success(it.also {
                        it.loadAttributeAndValues()
                    }, observationData)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error loading trait details: ", e)
                _uiState.value = TraitDetailUiState.Error(R.string.error_loading_trait_detail)
            }
        }
    }

    private suspend fun loadObservationData(valueFormatter: ValueProcessorFormatAdapter, trait: TraitObject): ObservationData =
        withContext(ioDispatcher) {
            val observations = database.getAllObservationsOfVariable(trait.id)
            val fieldsWithObservations = observations.map { it.study_id }.distinct()
            val missingObservations = database.getMissingObservationsCount(trait.id)
            val totalObservations = observations.size + missingObservations
            val completeness = if (totalObservations > 0)
                observations.size.toFloat() / totalObservations.toFloat()
            else 0f

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

    fun updateResourceFile(traitId: String, fileUri: String) {
        viewModelScope.launch {
            try {
                val updatedTrait = withContext(ioDispatcher) {
                    val trait = database.getTraitById(traitId)
                    trait?.let {
                        it.resourceFile = fileUri
                    }
                    trait.saveAttributeValues()
                    trait
                }
                updatedTrait?.let {
                    val obsData = (_uiState.value as? TraitDetailUiState.Success)?.observationData

                    _uiState.value = TraitDetailUiState.Success(updatedTrait, obsData)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating resource file: ", e)
                _uiState.value = TraitDetailUiState.Error(R.string.error_updating_trait_resource_file)
            }
        }
    }

    fun updateTraitOptions(valueFormatter: ValueProcessorFormatAdapter, trait: TraitObject) {
        viewModelScope.launch {
            try {
                trait.saveAttributeValues()
                //reload the observations, in case they need reformatting in the graph
                val obsData = loadObservationData(valueFormatter, trait)
                _uiState.value = TraitDetailUiState.Success(trait, obsData)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating trait options: ", e)
                _uiState.value = TraitDetailUiState.Error(R.string.error_updating_trait_options)
            }
        }
    }

    fun updateTraitAlias(trait: TraitObject, newAlias: String) {
        viewModelScope.launch {
            try {
                val updatedTrait = withContext(ioDispatcher) {
                    trait.alias = newAlias

                    val currentSynonyms = trait.synonyms.toMutableList()
                    if (!currentSynonyms.any { it == newAlias }) {
                        // add to synonyms
                        currentSynonyms.add(newAlias)
                        trait.synonyms = currentSynonyms
                    }

                    database.updateTrait(trait)

                    trait
                }

                val obsData = (_uiState.value as? TraitDetailUiState.Success)?.observationData
                _uiState.value = TraitDetailUiState.Success(updatedTrait, obsData)
            } catch (e: Exception) {
                Log.e(TAG, "Error updating trait alias: ", e)
                _uiState.value = TraitDetailUiState.Error(R.string.error_updating_trait_alias)
            }
        }
    }

    fun copyTrait(trait: TraitObject, newName: String) {
        viewModelScope.launch {
            if (newName.isEmpty()) {
                _copyTraitStatus.value = CopyTraitStatus.Error(R.string.error_empty_trait_name)
                return@launch
            }

            try {
                withContext(ioDispatcher) {
                    val pos = database.getMaxPositionFromTraits() + 1

                    trait.apply {
                        name = newName
                        alias = newName
                        visible = true
                        realPosition = pos
                    }

                    database.insertTraits(trait)
                }
                _copyTraitStatus.value = CopyTraitStatus.Success(newName)
            } catch (e: Exception) {
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