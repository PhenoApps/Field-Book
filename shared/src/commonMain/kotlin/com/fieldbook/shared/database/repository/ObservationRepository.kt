package com.fieldbook.shared.database.repository

import com.fieldbook.shared.sqldelight.FieldbookDatabase

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
}
