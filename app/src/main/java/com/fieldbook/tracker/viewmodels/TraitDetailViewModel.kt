package com.fieldbook.tracker.viewmodels

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.CategoryJsonUtil
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

    fun loadTraitDetails(traitId: String) {
        viewModelScope.launch {
            _uiState.value = TraitDetailUiState.Loading
            try {
                val trait = withContext(ioDispatcher) { database.getTraitById(traitId) }

                trait?.let {
                    val observationData = loadObservationData(it)
                    _uiState.value = TraitDetailUiState.Success(trait, observationData)
                }

            } catch (e: Exception) {
                _uiState.value = TraitDetailUiState.Error(R.string.error_loading_trait_detail)
            }
        }
    }

    private suspend fun loadObservationData(trait: TraitObject): ObservationData =
        withContext(ioDispatcher) {
            val observations = database.getAllObservationsOfVariable(trait.id)
            val fieldsWithObservations = observations.map { it.study_id }.distinct()
            val missingObservations = database.getMissingObservationsCount(trait.id)
            val totalObservations = observations.size + missingObservations
            val completeness = if (totalObservations > 0)
                observations.size.toFloat() / totalObservations.toFloat()
            else 0f

            val processedObservations = observations.map { obs ->
                CategoryJsonUtil.processValue(
                    buildMap {
                        put("observation_variable_field_book_format", trait.format)
                        put("value", obs.value)
                    }
                ) ?: ""
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
                    Log.d(TAG, "updateResourceFile: ${trait?.name}")
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

    fun copyTrait(trait: TraitObject, newName: String) {
        viewModelScope.launch {
            if (newName.isEmpty()) {
                _copyTraitStatus.value = CopyTraitStatus.Error(R.string.error_empty_trait_name)
                return@launch
            }

            try {
                withContext(ioDispatcher) {
                    val pos = database.getMaxPositionFromTraits() + 1

                    trait.name = newName
                    trait.visible = true
                    trait.realPosition = pos

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