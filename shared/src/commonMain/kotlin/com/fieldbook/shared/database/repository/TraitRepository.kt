package com.fieldbook.shared.database.repository

import com.fieldbook.shared.database.models.TraitObject
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.sqldelight.Observation_variables
import com.fieldbook.shared.sqldelight.createDatabase

class TraitRepository() {
    private val db: FieldbookDatabase
        get() = createDatabase()

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

    private fun ensureAttributeId(attrName: String): Long {
        return db.observation_variablesQueries.getAttributeIdByName(attrName).executeAsOneOrNull()
            ?: run {
                db.observation_variablesQueries.insertObservationVariableAttribute(attrName)
                db.observation_variablesQueries.getAttributeIdByName(attrName).executeAsOne()
            }
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

    fun getVisibleTraitsWithAttributes(): List<TraitObject> {
        return db.observation_variablesQueries.getVisibleTraitsWithAttributes().executeAsList().map {
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

    fun getAllTraitsOrdered(): List<TraitObject> {
        return db.observation_variablesQueries.getAllTraitsOrdered().executeAsList()
            .map { it.toTraitObject() }
    }

    fun getTraitWithAttributes(id: Long): TraitObject? {
        return db.observation_variablesQueries.getTraitWithAttributesById(id).executeAsOneOrNull()?.let {
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

    fun updateTraitVisibility(id: Long, visibleFlag: Boolean) {
        val valStr = if (visibleFlag) "true" else "false"
        db.observation_variablesQueries.updateTraitVisibility(valStr, id)
    }

    fun updateTraitPosition(id: Long, position: Int) {
        db.observation_variablesQueries.updateTraitPosition(position.toLong(), id)
    }

    fun deleteTrait(id: Long) {
        db.observation_variablesQueries.deleteObservationVariableValuesByTraitId(id)
        db.observation_variablesQueries.deleteTrait(id)
    }

    fun deleteAllTraits() {
        getAllTraits().forEach { trait ->
            trait.id?.let { db.observation_variablesQueries.deleteObservationVariableValuesByTraitId(it) }
        }
        db.observation_variablesQueries.deleteAllTraits()
    }

    fun getAllTraitNames(): List<String> {
        return db.observation_variablesQueries.getAllTraitNames().executeAsList().mapNotNull { it.observation_variable_name }
    }

    fun getMaxPositionFromTraits(): Int {
        return db.observation_variablesQueries.getMaxPositionFromTraits().executeAsOne().toInt()
    }

    fun insertTrait(trait: TraitObject) {
        // insert the base observation_variables row
        db.observation_variablesQueries.insertTrait(
            trait.name,
            trait.format,
            trait.defaultValue,
            trait.visible,
            trait.realPosition.toLong(),
            trait.externalDbId,
            trait.traitDataSource,
            trait.additionalInfo,
            trait.commonCropName,
            trait.language,
            trait.dataType,
            trait.observationVariableDbId,
            trait.ontologyDbId,
            trait.ontologyName,
            trait.details
        )

        val insertedRow = db.observation_variablesQueries.getTraitByNameAndPosition(
            trait.name,
            trait.realPosition.toLong()
        ).executeAsOneOrNull()
        val insertedId = insertedRow?.internal_id_observation_variable ?: return

        val attrs: Map<String, String> = mapOf(
            "validValuesMin" to (trait.minimum ?: ""),
            "validValuesMax" to (trait.maximum ?: ""),
            "category" to (trait.categories ?: ""),
        )

        attrs.forEach { (attrName, attrValue) ->
            val attrId = ensureAttributeId(attrName)
            db.observation_variablesQueries.insertObservationVariableValue(
                insertedId,
                attrId,
                attrValue
            )
        }
    }

    fun updateTrait(trait: TraitObject) {
        val traitId = trait.id ?: return

        db.observation_variablesQueries.updateTrait(
            trait.name,
            trait.format,
            trait.defaultValue,
            trait.details,
            traitId
        )

        val attrs: Map<String, String> = mapOf(
            "validValuesMin" to (trait.minimum ?: ""),
            "validValuesMax" to (trait.maximum ?: ""),
            "category" to (trait.categories ?: ""),
        )

        attrs.forEach { (attrName, attrValue) ->
            val attrId = ensureAttributeId(attrName)
            db.observation_variablesQueries.deleteObservationVariableValue(traitId, attrId)
            db.observation_variablesQueries.insertObservationVariableValue(traitId, attrId, attrValue)
        }
    }
}
