package com.fieldbook.tracker.database.dao

import android.util.Log
import com.fieldbook.tracker.database.ObservationVariableAttributeDetailsView
import com.fieldbook.tracker.database.models.TraitAttributes
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
                        with (ObservationVariableAttributeDetailsView) {
                            val traitId = cursor.getString(cursor.getColumnIndexOrThrow(ObservationVariableAttributeDetailsView.INTERNAL_ID))
                            val attributes = buildMap {
                                TraitAttributes.ALL.forEach { attribute ->
                                    put(attribute.key, cursor.getString(
                                        cursor.getColumnIndexOrThrow(attribute.key)
                                    ))
                                }
                            }
                            attributeMap[traitId] = attributes
                        }
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