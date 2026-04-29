package com.fieldbook.shared.database.repository

import com.fieldbook.shared.database.models.ObservationUnitModel
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.sqldelight.Observation_units
import com.fieldbook.shared.sqldelight.createDatabase

class ObservationUnitRepository() {
    private val db: FieldbookDatabase
        get() = createDatabase()

    private fun Observation_units.toMap(): Map<String, Any?> {
        return mapOf(
            "internal_id_observation_unit" to internal_id_observation_unit,
            "study_id" to study_id,
            "observation_unit_db_id" to observation_unit_db_id,
            "primary_id" to primary_id,
            "secondary_id" to secondary_id,
            "geo_coordinates" to geo_coordinates
        )
    }

    fun getAllObservationUnits(studyId: Long?): List<ObservationUnitModel> {
        return db.observation_unitsQueries.selectAll(studyId).executeAsList().map { r ->
            ObservationUnitModel(r.toMap())
        }
    }

    fun getObservationUnitById(studyId: Long?, id: String): ObservationUnitModel? {
        return db.observation_unitsQueries.selectById(id, studyId).executeAsOneOrNull()?.let { r ->
            ObservationUnitModel(r.toMap())
        }
    }
}
