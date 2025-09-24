package com.fieldbook.shared.database.repository

import com.fieldbook.shared.database.models.ObservationObject
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import kotlinx.datetime.Instant

class ObservationRepository(private val db: FieldbookDatabase) {
    /**
     * Returns a map of observation_variable_name to value for the given studyId and plotId.
     */
    fun getUserDetail(studyId: Long, plotId: String): Map<Long, String> {
        return db.observationsQueries.getUserDetail(studyId, plotId)
            .executeAsList()
            .filter { it.value_ != null }
            .associate { it.observation_variable_db_id to it.value_!! }
    }

    fun getRep(studyId: Long, plotId: String, traitId: Long): Int {
        return db.observationsQueries.countObservations(
            study_id = studyId,
            observation_unit_id = plotId,
            observation_variable_db_id = traitId
        ).executeAsOne().toInt()
    }

    fun insertObservation(
        studyId: Long,
        plotId: String,
        traitId: Long,
        value: String,
        notes: String,
        lastSyncedTime: Instant?,
        rep: String? = (getRep(studyId, plotId, traitId) + 1).toString()
    ) {
        // Always remove existing trait before inserting again
        // Based on plot_id, prevent duplicates
        db.observationsQueries.deleteTrait(
            study_id = studyId,
            observation_unit_id = plotId,
            observation_variable_db_id = traitId
        )
        db.observationsQueries.insertObservation(
            study_id = studyId,
            observation_unit_id = plotId,
            observation_variable_db_id = traitId,
            value_ = value
        )
    }

    fun getObservation(studyId: Long, plotId: String, traitId: Long): ObservationObject? {
        val row = db.observationsQueries.getObservation(
            study_id = studyId,
            observation_unit_id = plotId,
            observation_variable_db_id = traitId
        ).executeAsOneOrNull() ?: return null
        return ObservationObject(
            id = row.internal_id_observation,
            studyId = row.study_id,
            observationVariableName = row.observation_variable_name,
            observationVariableDbId = row.observation_variable_db_id,
            observationUnitId = row.observation_unit_id,
            value = row.value_,
            lastSyncedTime = null, // Placeholder: update if/when lastSyncedTime is added to schema
            rep = null // Placeholder: can be calculated if needed
        )
    }
}
