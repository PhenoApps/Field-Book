package com.fieldbook.shared.screens.brapi.field

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.fieldbook.shared.brapi.BrAPIServiceFactory
import com.fieldbook.shared.brapi.BrapiPaginationManager
import com.fieldbook.shared.brapi.BrapiResult
import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationUnitDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiTraitDetails
import com.fieldbook.shared.database.repository.StudyRepository
import com.fieldbook.shared.database.repository.TraitRepository
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.preferences.PreferenceKeys
import com.fieldbook.shared.screens.brapi.BrapiFieldImportSupport
import com.fieldbook.shared.utilities.FieldSwitchImpl
import com.russhwolf.settings.Settings
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class BrapiStudyPreviewUiState(
    val traits: List<BrapiTraitDetails> = emptyList(),
    val observationUnits: List<BrapiObservationUnitDetails> = emptyList(),
    val loadingTraits: Boolean = false,
    val loadingUnits: Boolean = false,
    val traitError: String? = null,
    val unitError: String? = null,
    val importing: Boolean = false,
)

class BrapiStudyPreviewScreenViewModel(
    private val studyRepository: StudyRepository = StudyRepository(),
    private val traitRepository: TraitRepository = TraitRepository(),
    private val settings: Settings = Settings(),
    private val fieldSwitchImpl: FieldSwitchImpl = FieldSwitchImpl(),
) : ViewModel() {

    private val _uiState = MutableStateFlow(BrapiStudyPreviewUiState())
    val uiState: StateFlow<BrapiStudyPreviewUiState> = _uiState.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages = _messages.asSharedFlow()

    private val _importCompleted = MutableSharedFlow<Unit>()
    val importCompleted = _importCompleted.asSharedFlow()

    fun loadStudy(study: BrapiStudyDetails, defaultBrapiBaseUrl: String) {
        _uiState.value = BrapiStudyPreviewUiState()
        loadTraits(study, defaultBrapiBaseUrl)
        loadObservationUnits(study, defaultBrapiBaseUrl)
    }

    fun importStudy(study: BrapiStudyDetails, defaultBrapiBaseUrl: String) {
        val state = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(importing = true) }
            try {
                val sourceUrl = settings.getString(PreferenceKeys.BRAPI_BASE_URL, defaultBrapiBaseUrl)
                val result = BrapiFieldImportSupport.importStudy(
                    study = study,
                    observationUnits = state.observationUnits,
                    traits = state.traits,
                    studyRepository = studyRepository,
                    traitRepository = traitRepository,
                    sourceUrl = sourceUrl,
                )

                fieldSwitchImpl.switchField(result.fieldId)
                settings.putInt(GeneralKeys.SELECTED_FIELD_ID.key, result.fieldId)
                _messages.emit("Imported ${study.studyName ?: study.studyDbId} with ${result.importedTraitCount} trait(s)")
                _importCompleted.emit(Unit)
            } catch (e: Exception) {
                _messages.emit(e.message ?: "Error importing BrAPI study")
            } finally {
                _uiState.update { it.copy(importing = false) }
            }
        }
    }

    private fun loadTraits(study: BrapiStudyDetails, defaultBrapiBaseUrl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingTraits = true, traitError = null) }

            when (val result = createBrapiService(defaultBrapiBaseUrl)
                .getStudyTraits(study.studyDbId, getBrapiPageSize())) {
                is BrapiResult.Success -> {
                    _uiState.update { it.copy(traits = result.value) }
                }

                is BrapiResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            traitError = result.message ?: result.statusCode?.let { status -> "HTTP $status" }
                            ?: "Unable to load traits."
                        )
                    }
                }
            }

            _uiState.update { it.copy(loadingTraits = false) }
        }
    }

    private fun loadObservationUnits(study: BrapiStudyDetails, defaultBrapiBaseUrl: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loadingUnits = true, unitError = null) }

            when (val result = createBrapiService(defaultBrapiBaseUrl)
                .getStudyObservationUnits(study.studyDbId, getBrapiPageSize())) {
                is BrapiResult.Success -> {
                    _uiState.update { it.copy(observationUnits = result.value) }
                }

                is BrapiResult.Failure -> {
                    _uiState.update {
                        it.copy(
                            unitError = result.message ?: result.statusCode?.let { status -> "HTTP $status" }
                            ?: "Unable to load observation units."
                        )
                    }
                }
            }

            _uiState.update { it.copy(loadingUnits = false) }
        }
    }

    private fun getBrapiPageSize(): Int {
        return BrapiPaginationManager.fromSettings(settings).pageSize
    }

    private fun createBrapiService(defaultBrapiBaseUrl: String) = BrAPIServiceFactory.create(
        baseUrl = settings.getString(PreferenceKeys.BRAPI_BASE_URL, defaultBrapiBaseUrl),
        bearerToken = settings.getStringOrNull(PreferenceKeys.BRAPI_TOKEN),
        version = settings.getString(PreferenceKeys.BRAPI_VERSION, BrAPIServiceFactory.VERSION_V2),
    )
}

fun brapiStudyPreviewScreenViewModelFactory() = viewModelFactory {
    initializer {
        BrapiStudyPreviewScreenViewModel(
            studyRepository = StudyRepository(),
            traitRepository = TraitRepository(),
            settings = Settings(),
            fieldSwitchImpl = FieldSwitchImpl(),
        )
    }
}
