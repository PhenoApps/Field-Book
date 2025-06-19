package com.fieldbook.tracker.database.dao

import android.util.Log
import com.fieldbook.tracker.database.withDatabase
import androidx.core.database.sqlite.transaction
import com.fieldbook.tracker.database.Migrator.ObservationVariableAttribute
import com.fieldbook.tracker.database.models.AttributeDefinition

/**
 * Helper class to access ObservationVariableAttribute and ObservationVariableValue tables in a cleaner way
 */
class TraitAttributeValuesHelper(private val traitId: String) {

    companion object {
        const val TAG = "TraitAttributeValues"
    }

    private val attributeValueMap = mutableMapOf<String, String>()
    private var isLoaded = false

    /**
     * Load all attribute values a trait
     */
    fun load() {
        if (isLoaded) return

        if (traitId.isEmpty()) {
            isLoaded = true
            return
        }

        withDatabase { db ->
            try {
                val values = ObservationVariableValueDao.getVariableValues(traitId.toInt())
                values?.forEach { value ->
                    val attrId = value[ObservationVariableAttribute.FK] as? Int ?: return@forEach
                    val attrName = ObservationVariableAttributeDao.getAttributeNameById(attrId).toString()
                    val attrValue = value["observation_variable_attribute_value"] as? String ?: ""
                    attributeValueMap[attrName] = attrValue
                }
                isLoaded = true
                Log.d(TAG, "Loaded ${attributeValueMap.size} attributes for trait ID: $traitId")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading attributes for trait ID: $traitId", e)
            }
        }
    }

    /**
     * Used to create and update existing traits
     */
    fun save() {
        if (traitId.isEmpty()) return

        withDatabase { db ->
            db.transaction {
                try {
                    attributeValueMap.forEach { (attributeName, attributeValue) ->
                        val attrId = ObservationVariableAttributeDao.getAttributeIdByName(attributeName)

                        if (attrId != -1) {
                            // try updating the value first
                            val rowsUpdated = ObservationVariableValueDao.update(traitId, attrId.toString(), attributeValue)

                            if (rowsUpdated != null && rowsUpdated == 0) { // if nothing was updated, insert a new value instead
                                ObservationVariableValueDao.insertAttributeValue(attrId.toString(), attributeValue, traitId)
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error occurred while saving:", e)
                }
            }
        }
    }

    fun getString(attribute: AttributeDefinition): String {
        ensureLoaded()
        return attributeValueMap[attribute.key] ?: attribute.defaultValue
    }

    fun getBoolean(attribute: AttributeDefinition): Boolean {
        return getString(attribute).toBoolean()
    }

    fun setValue(attribute: AttributeDefinition, value: String) {
        ensureLoaded()
        attributeValueMap[attribute.key] = value
    }

    private fun ensureLoaded() {
        if (!isLoaded) load()
    }
}