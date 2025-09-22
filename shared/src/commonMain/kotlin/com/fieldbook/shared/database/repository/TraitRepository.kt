package com.fieldbook.shared.database.repository

import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.sqldelight.Observation_variables

class TraitRepository(private val db: FieldbookDatabase) {
    private fun Observation_variables.toTraitObject(): TraitObject {
        return TraitObject(
            name = observation_variable_name ?: "",
            id = internal_id_observation_variable.toString(),
            realPosition = position?.toInt() ?: 0
            // Other fields use default values from TraitObject
        )
    }

    fun getAllTraits(): List<TraitObject> {
        return db.observation_variablesQueries.getAllTraits().executeAsList().map { it.toTraitObject() }
    }
}
