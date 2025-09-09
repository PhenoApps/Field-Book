package com.fieldbook.tracker.database.dao

import android.util.Log
import com.fieldbook.tracker.database.ObservationVariableAttributeDetailsView
import com.fieldbook.tracker.database.withDatabase

class ObservationVariableDetailsView {
    companion object {
        const val TAG = "ObsVarAttriDetailView"

        /**
         * Get attribute details for multiple traits
         */
        fun getAttributeDetailsForTraits(traitIds: List<String>): Map<String, Map<String, String?>> = withDatabase { db ->
            if (traitIds.isEmpty()) return@withDatabase emptyMap()

            val placeholders = traitIds.joinToString(",") { "?" }
            val query = """
                SELECT 
                    *
                FROM ${ObservationVariableAttributeDetailsView.VIEW_NAME}
                WHERE ${ObservationVariableAttributeDetailsView.INTERNAL_ID} IN ($placeholders)
            """

            val attributeMap = mutableMapOf<String, Map<String, String?>>()

            try {
                db.rawQuery(query, traitIds.toTypedArray()).use { cursor ->
                    while (cursor.moveToNext()) {
                        val traitId = cursor.getString(cursor.getColumnIndexOrThrow(ObservationVariableAttributeDetailsView.INTERNAL_ID))
                        val attributes = mapOf(
                            "validValuesMax" to cursor.getString(cursor.getColumnIndexOrThrow(ObservationVariableAttributeDetailsView.VALID_VALUES_MAX)),
                            "validValuesMin" to cursor.getString(cursor.getColumnIndexOrThrow(ObservationVariableAttributeDetailsView.VALID_VALUES_MIN)),
                            "category" to cursor.getString(cursor.getColumnIndexOrThrow(ObservationVariableAttributeDetailsView.CATEGORY)),
                            "closeKeyboardOnOpen" to cursor.getString(cursor.getColumnIndexOrThrow(ObservationVariableAttributeDetailsView.CLOSE_KEYBOARD_ON_OPEN)),
                            "cropImage" to cursor.getString(cursor.getColumnIndexOrThrow(ObservationVariableAttributeDetailsView.CROP_IMAGE))
                        )
                        attributeMap[traitId] = attributes
                    }
                }
                Log.d(TAG, "Loaded attribute details for ${attributeMap.size} of ${traitIds.size} requested traits")
            } catch (e: Exception) {
                Log.e(TAG, "Error getting attribute details for specific traits", e)
            }

            attributeMap
        } ?: emptyMap()
    }
}