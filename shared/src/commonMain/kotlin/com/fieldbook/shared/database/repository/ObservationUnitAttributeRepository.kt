package com.fieldbook.shared.database.repository

import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.sqldelight.createDatabase

class ObservationUnitAttributeRepository(
    private val db: FieldbookDatabase = createDatabase()
) {

    /**
     * Returns all observation unit attribute names for the given study id.
     * Filters out null or blank names.
     */
    fun getAllNames(studyId: Long?): List<String> {
        if (studyId == null) return emptyList()
        return db.observation_units_attributesQueries
            .getAllNamesByStudyId(studyId)
            .executeAsList()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
    }
}
