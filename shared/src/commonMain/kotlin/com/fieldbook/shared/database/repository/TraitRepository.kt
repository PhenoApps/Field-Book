package com.fieldbook.shared.database.repository

import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.sqldelight.Observation_variables

class TraitRepository(private val db: FieldbookDatabase) {
    private fun Observation_variables.toTraitObject(): TraitObject {
        return TraitObject(
            id = internal_id_observation_variable,
            name = observation_variable_name ?: "",
            format = observation_variable_field_book_format,
            defaultValue = default_value,
            visible = visible,
            realPosition = position?.toInt() ?: 0,
            externalDbId = external_db_id,
            traitDataSource = trait_data_source,
            additionalInfo = additional_info,
            commonCropName = common_crop_name,
            language = language,
            dataType = data_type,
            observationVariableDbId = observation_variable_db_id,
            ontologyDbId = ontology_db_id,
            ontologyName = ontology_name,
            details = observation_variable_details
        )
    }

    fun getAllTraits(): List<TraitObject> {
        return db.observation_variablesQueries.getAllTraits().executeAsList()
            .map { it.toTraitObject() }
    }
}
