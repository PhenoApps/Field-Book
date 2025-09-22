package com.fieldbook.shared.database.repository

import com.fieldbook.shared.database.models.ObservationUnitModel
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.sqldelight.Observation_units

class ObservationUnitRepository(private val db: FieldbookDatabase) {
    private fun Observation_units.toMap(): Map<String, Any?> {
        return mapOf(
            "internal_id_observation_unit" to internal_id_observation_unit,
            "study_id" to study_id,
            "observation_unit_db_id" to observation_unit_db_id,
            "primary_id" to primary_id,
            "secondary_id" to secondary_id,
            "geo_coordinates" to geo_coordinates,
            "additional_info" to additional_info,
            "germplasm_db_id" to germplasm_db_id,
            "germplasm_name" to germplasm_name,
            "observation_level" to observation_level,
            "position_coordinate_x" to position_coordinate_x,
            "position_coordinate_x_type" to position_coordinate_x_type,
            "position_coordinate_y" to position_coordinate_y,
            "position_coordinate_y_type" to position_coordinate_y_type
        )
    }

    fun getAllObservationUnits(): List<ObservationUnitModel> {
        return db.observation_unitsQueries.selectAll().executeAsList().map { r ->
            ObservationUnitModel(r.toMap())
        }
    }
    fun getObservationUnitById(id: String): ObservationUnitModel? {
        return db.observation_unitsQueries.selectById(id).executeAsOneOrNull()?.let { r ->
            ObservationUnitModel(r.toMap())
        }
    }
}
