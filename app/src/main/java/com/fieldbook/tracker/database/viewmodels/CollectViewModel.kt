package com.fieldbook.tracker.database.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Basic start to a collect view model
 */
@HiltViewModel
class CollectViewModel @Inject constructor(
    private val repo: TraitRepository
) : ViewModel() {

    companion object {
        private const val TAG = "CollectViewModel"
    }

    private val _uiState = MutableStateFlow<CollectUiState>(CollectUiState.Loading)
    val uiState: StateFlow<CollectUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<TraitDetailEvent>()
    val events = _events.asSharedFlow()

    fun updateAttributes(trait: TraitObject) {
        viewModelScope.launch {
            runCatching {
                repo.updateAttributes(trait)
            }.onSuccess {
                _uiState.value = CollectUiState.Success
            }.onFailure { e ->
                Log.e(TAG, "Error updating trait options: ", e)
                _uiState.value = CollectUiState.Error(R.string.error_updating_trait_options)
            }
        }
    }
}

sealed class CollectUiState {
    object Loading : CollectUiState()
    object Success : CollectUiState()
    class Error(val messageRes: Int) : CollectUiState()
}