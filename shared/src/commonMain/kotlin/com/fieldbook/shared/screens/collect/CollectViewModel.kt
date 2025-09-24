package com.fieldbook.shared.screens.collect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.fieldbook.shared.database.models.ObservationUnitModel
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.database.repository.ObservationRepository
import com.fieldbook.shared.database.repository.ObservationUnitRepository
import com.fieldbook.shared.database.repository.TraitRepository
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.sqldelight.DriverFactory
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.russhwolf.settings.Settings


// TODO refactor to use actual ViewModel() ?
class CollectViewModel(driverFactory: DriverFactory) {
    private val db = FieldbookDatabase(driverFactory.createDriver())
    private val observationUnitRepository = ObservationUnitRepository(db)
    private val traitRepository = TraitRepository(db)
    private val observationRepository = ObservationRepository(db)
    private val settings: Settings = Settings()

    private val studyId: Int = settings.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)

    var units by mutableStateOf<List<ObservationUnitModel>>(emptyList())
        private set
    var unitLoading by mutableStateOf(true)
        private set
    var unitError by mutableStateOf<String?>(null)
        private set
    var currentUnitIndex by mutableStateOf(0)
        private set

    var traits by mutableStateOf<List<TraitObject>>(emptyList())
        private set
    var traitLoading by mutableStateOf(true)
        private set
    var traitError by mutableStateOf<String?>(null)
        private set
    var currentTraitIndex by mutableStateOf(0)
        private set

    var traitValues by mutableStateOf<Map<Long, String>>(emptyMap())
        private set
    var traitValuesLoading by mutableStateOf(true)
        private set
    private var lastUnitId: String? = null

    init {
        loadUnits()
        loadTraits()
        loadTraitValues()
    }

    private fun loadUnits() {
        try {
            units = observationUnitRepository.getAllObservationUnits(studyId.toLong())
            unitLoading = false
        } catch (e: Exception) {
            e.printStackTrace()
            unitError = e.message
            unitLoading = false
        }
    }

    private fun loadTraits() {
        try {
            traits = traitRepository.getAllTraits()
            traitLoading = false
        } catch (e: Exception) {
            e.printStackTrace()
            traitError = e.message
            traitLoading = false
        }
    }

    fun updateCurrentUnitIndex(index: Int) {
        if (index in units.indices) {
            currentUnitIndex = index
            loadTraitValues()
        }
    }

    fun updateCurrentTraitIndex(index: Int) {
        if (index in traits.indices) {
            currentTraitIndex = index
        }
    }

    private fun loadTraitValues() {
        val unit = units.getOrNull(currentUnitIndex)
        val plotId = unit?.observation_unit_db_id
        if (plotId != null && plotId != lastUnitId) {
            traitValuesLoading = true
            traitValues = observationRepository.getUserDetail(studyId.toLong(), plotId)
            traitValuesLoading = false
            lastUnitId = plotId
        }
    }

    /**
     * Update the observation for the current trait and unit, and persist to DB.
     */
    fun updateCurrentTraitValue(value: String) {
        val trait = traits.getOrNull(currentTraitIndex)
        val unit = units.getOrNull(currentUnitIndex)
        val plotId = unit?.observation_unit_db_id

        if (plotId != null && trait?.id != null) {
            // Fetch existing observation to get lastSyncedTime if present
            val existingObs = observationRepository.getObservation(studyId.toLong(), plotId, trait.id!!)
            val lastSyncedTime = existingObs?.lastSyncedTime
            observationRepository.insertObservation(
                studyId.toLong(),
                plotId,
                trait.id!!,
                value,
                notes = "",
                lastSyncedTime = lastSyncedTime
            )
            println("Saved observation: studyId=$studyId, plotId=$plotId, traitId=${trait.id}, value=$value")
            traitValues = traitValues.toMutableMap().apply {
                put(trait.id!!, value)
            }
            setCurrentValueAsEdited()
        }
    }

    /**
     * Mark the current value as edited (placeholder implementation).
     */
    fun setCurrentValueAsEdited() {
        // Placeholder: could set a flag, log, or trigger UI update
        println("Current value marked as edited.")
    }
}
