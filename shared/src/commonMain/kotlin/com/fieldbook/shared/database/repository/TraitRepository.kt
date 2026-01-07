package com.fieldbook.shared.database.repository

import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.sqldelight.Observation_variables
import com.fieldbook.shared.sqldelight.createDatabase

class TraitRepository() {
    private val db: FieldbookDatabase = createDatabase()

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

    fun getAllTraitsWithAttributes(): List<TraitObject> {
        return db.observation_variablesQueries.getAllTraitsWithAttributes().executeAsList().map {
            TraitObject(
                id = it.internal_id_observation_variable,
                name = it.observation_variable_name ?: "",
                format = it.observation_variable_field_book_format,
                defaultValue = it.default_value,
                minimum = it.minimum,
                maximum = it.maximum,
                categories = it.categories,
                visible = it.visible,
                realPosition = it.position?.toInt() ?: 0,
                externalDbId = it.external_db_id,
                traitDataSource = it.trait_data_source,
                additionalInfo = it.additional_info,
                commonCropName = it.common_crop_name,
                language = it.language,
                dataType = it.data_type,
                observationVariableDbId = it.observation_variable_db_id,
                ontologyDbId = it.ontology_db_id,
                ontologyName = it.ontology_name,
                details = it.observation_variable_details
            )
        }
    }

    // New: return traits ordered by position
    fun getAllTraitsOrdered(): List<TraitObject> {
        return db.observation_variablesQueries.getAllTraitsOrdered().executeAsList()
            .map { it.toTraitObject() }
    }

    // New: update visible flag for a trait
    fun updateTraitVisibility(id: Long, visibleFlag: Boolean) {
        val valStr = if (visibleFlag) "true" else "false"
        db.observation_variablesQueries.updateTraitVisibility(valStr, id)
    }

    // New: update position for a trait
    fun updateTraitPosition(id: Long, position: Int) {
        db.observation_variablesQueries.updateTraitPosition(position.toLong(), id)
    }
}
