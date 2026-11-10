package com.fieldbook.tracker.database.viewmodels

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.repository.FieldInsightsRepository
import com.fieldbook.tracker.insights.FieldInsightsData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FieldInsightsViewModel @Inject constructor(
    private val repo: FieldInsightsRepository,
) : ViewModel() {

    companion object {
        private const val TAG = "FieldInsightsViewModel"
    }

    private val _uiState = MutableStateFlow<FieldInsightsUiState>(FieldInsightsUiState.Loading)
    val uiState: StateFlow<FieldInsightsUiState> = _uiState.asStateFlow()

    private var loadedStudyId: Int? = null

    /** Loads once per field; a configuration change keeps the loaded data. */
    fun load(studyId: Int) {

        if (studyId == loadedStudyId && _uiState.value !is FieldInsightsUiState.Error) return
        loadedStudyId = studyId

        viewModelScope.launch {
            _uiState.value = FieldInsightsUiState.Loading

            runCatching { repo.load(studyId) }
                .onSuccess { data ->
                    _uiState.value = data?.let { FieldInsightsUiState.Success(it) }
                        ?: FieldInsightsUiState.Error(R.string.field_insights_error_loading)
                }
                .onFailure { e ->
                    Log.e(TAG, "Error loading field insights: ", e)
                    _uiState.value = FieldInsightsUiState.Error(R.string.field_insights_error_loading)
                }
        }
    }
}

sealed class FieldInsightsUiState {
    object Loading : FieldInsightsUiState()
    data class Success(val data: FieldInsightsData) : FieldInsightsUiState()
    data class Error(val messageRes: Int) : FieldInsightsUiState()
}
