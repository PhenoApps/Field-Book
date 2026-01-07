package com.fieldbook.shared.screens.collect

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import com.fieldbook.shared.database.models.FieldObject
import com.fieldbook.shared.database.models.ObservationUnitModel
import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.database.repository.ObservationRepository
import com.fieldbook.shared.database.repository.ObservationUnitPropertyRepository
import com.fieldbook.shared.database.repository.ObservationUnitRepository
import com.fieldbook.shared.database.repository.StudyRepository
import com.fieldbook.shared.database.repository.TraitRepository
import com.fieldbook.shared.objects.RangeObject
import com.fieldbook.shared.preferences.GeneralKeys
import com.fieldbook.shared.theme.AppColors
import com.russhwolf.settings.Settings


// TODO refactor to use ViewModel() ?
class CollectScreenController {
    private val observationUnitRepository = ObservationUnitRepository()
    private val traitRepository = TraitRepository()
    private val observationRepository = ObservationRepository()
    private val observationUnitPropertyRepository = ObservationUnitPropertyRepository()
    private val studyRepository = StudyRepository()

    private val settings: Settings = Settings()

    val studyId: Int = settings.getInt(GeneralKeys.SELECTED_FIELD_ID.key, 0)
    val field: FieldObject = studyRepository.getById(studyId)

    var units by mutableStateOf<List<ObservationUnitModel>>(emptyList())
        private set
    var rangeID by mutableStateOf<Array<Int>>(emptyArray())
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

    var traitValues by mutableStateOf<Map<Long, List<String>>>(emptyMap())
        private set
    var traitValuesLoading by mutableStateOf(true)
        private set
    private var lastUnitId: String? = null

    val primaryId = settings.getString(GeneralKeys.PRIMARY_NAME.key, "")
    val secondaryId = settings.getString(GeneralKeys.SECONDARY_NAME.key, "")
    val uniqueId = settings.getString(GeneralKeys.UNIQUE_NAME.key, "")

    var cRange: RangeObject by mutableStateOf(RangeObject())

    init {
        loadUnits()
        loadTraits()
        loadTraitValues()
    }

    fun updateCurrentRange(id: Int) {
        try {
            cRange = observationUnitPropertyRepository.getRangeFromId(
                id.toLong(),
                primaryId,
                secondaryId,
                uniqueId
            )
        } catch (e: Exception) {
            // On error, ensure UI doesn't crash and show an empty range
            e.printStackTrace()
            cRange = RangeObject("", "", "")
        }
    }

    private fun loadUnits() {
        try {
            units = observationUnitRepository.getAllObservationUnits(studyId.toLong())
            rangeID = observationUnitPropertyRepository.allRangeID(studyId)
            unitLoading = false
        } catch (e: Exception) {
            e.printStackTrace()
            unitError = e.message
            unitLoading = false
        }
    }

    private fun loadTraits() {
        try {
            traits = traitRepository.getAllTraitsWithAttributes()
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
            observationRepository.upsertObservation(
                plotId = plotId,
                traitDbId = trait.id!!,
                value = value,
                studyId = studyId.toLong()
            )
            traitValues = traitValues.toMutableMap().apply {
                put(trait.id!!, listOf(value))
            }
        }
    }

    /**
     * Add a new observation for the current trait and unit, and persist to DB.
     */
    fun addCurrentTraitValue(value: String) {
        val trait = traits.getOrNull(currentTraitIndex)
        val unit = units.getOrNull(currentUnitIndex)
        val plotId = unit?.observation_unit_db_id

        if (plotId != null && trait?.id != null) {
            observationRepository.insertObservation(
                plotId = plotId,
                traitDbId = trait.id!!,
                value = value,
                studyId = studyId.toLong()
            )
            traitValues = traitValues.toMutableMap().apply {
                val currentList = get(trait.id!!) ?: emptyList()
                put(trait.id!!, currentList + value)
            }
        }
    }

    fun getDisplayColor(): Color {
        val defaultArgb = AppColors.fb_value_saved_color.argb
        var storedArgb = settings.getInt(GeneralKeys.SAVED_DATA_COLOR.key, defaultArgb)

        // Check if the alpha channel is 0 (fully transparent).
        // The 'ushr 24' operation isolates the alpha byte.
        if ((storedArgb ushr 24) == 0) {
            // If alpha is 0, assume it's an RGB value and make it fully opaque.
            storedArgb = storedArgb or 0xFF000000.toInt()
        }

        return Color(storedArgb)
    }
}
