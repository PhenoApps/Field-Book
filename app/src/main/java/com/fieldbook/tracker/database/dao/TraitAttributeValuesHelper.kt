package com.fieldbook.tracker.database.dao

import android.util.Log
import com.fieldbook.tracker.database.withDatabase
import androidx.core.database.sqlite.transaction
import com.fieldbook.tracker.database.ObservationVariableAttributeDetailsView
import com.fieldbook.tracker.database.models.AttributeDefinition
import com.fieldbook.tracker.database.models.TraitAttributes
import com.fieldbook.tracker.objects.TraitObject
import kotlin.text.isNullOrEmpty

/**
 * Helper class to access ObservationVariableAttribute and ObservationVariableValue tables in a cleaner way
 */
class TraitAttributeValuesHelper(var traitId: String? = null) {

    companion object {
        const val TAG = "TraitAttributeValues"

        private const val GET_ATTRIBUTE_VALUE_FROM_VIEW = """
            SELECT 
                * 
            FROM ${ObservationVariableAttributeDetailsView.VIEW_NAME}
            WHERE ${ObservationVariableAttributeDetailsView.INTERNAL_ID} = ?
        """

        /**
         * Load all attribute values for ALL traits
         */
        fun loadAttributeValuesForAllTraits(traits: List<TraitObject>?) {
            if (traits.isNullOrEmpty()) return

            val traitIds = traits.map { it.id }
            val attributeDetails = ObservationVariableDetailsView.getAttributeDetailsForTraits(traitIds)

            traits.forEach { trait ->
                attributeDetails[trait.id]?.let { attributes ->
                    attributes["validValuesMin"]?.let { trait.minimum = it }
                    attributes["validValuesMax"]?.let { trait.maximum = it }
                    attributes["category"]?.let { trait.categories = it }
                    attributes["closeKeyboardOnOpen"]?.let { trait.closeKeyboardOnOpen = it.toBoolean() }
                    attributes["cropImage"]?.let { trait.cropImage = it.toBoolean() }
                }
            }
        }
    }

    private val attributeValueMap = mutableMapOf<String, String>()
    private var isLoaded = false

    /**
     * Load all attribute values a trait
     */
    fun load() {
        if (isLoaded) return

        if (traitId.isNullOrEmpty()) {
            isLoaded = true
            return
        }

        withDatabase { db ->
            try {
                val cursor = db.rawQuery(GET_ATTRIBUTE_VALUE_FROM_VIEW, arrayOf(traitId))
                cursor.use { cursor ->
                    if (cursor.moveToFirst()) {
                        TraitAttributes.ALL.forEach { attribute ->
                            cursor.getString(cursor.getColumnIndexOrThrow(attribute.key))?.let { value ->
                                if (value.isNotEmpty()) {
                                    attributeValueMap[attribute.key] = value
                                }
                            }
                        }
                    }
                }
                //Log.d(TAG, "Loaded ${attributeValueMap.size} attributes for trait ID: $traitId")
            } catch (e: Exception) {
                Log.e(TAG, "Error loading attributes for trait ID: $traitId", e)
            }
        }
        isLoaded = true
    }

    /**
     * Used to create and update existing traits
     */
    fun save() {
        traitId?.let { traitId ->
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