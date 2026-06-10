package com.fieldbook.tracker.database.dao

import android.util.Log
import androidx.core.content.contentValuesOf
import com.fieldbook.tracker.database.FieldTraitConfigTable
import com.fieldbook.tracker.database.withDatabase
import com.fieldbook.tracker.database.query

/**
 * DAO for managing field-specific trait configurations (visibility and ordering).
 *
 * Each field (study) can have a FieldTraitConfig row that stores:
 * - trait_ids: comma-separated trait IDs that are visible in this field, in the desired order
 */
class FieldTraitConfigDao {

    companion object {
        private const val TAG = "FieldTraitConfigDao"
    }

    /**
     * Get the field-specific trait configuration for a study.
     * Returns null if the field has never been customized.
     */
    fun getFieldTraitConfig(studyId: Int): FieldTraitConfig? = withDatabase { db ->
        if (studyId < 0) return@withDatabase null

        db.query(
            FieldTraitConfigTable.TABLE_NAME,
            select = arrayOf(
                FieldTraitConfigTable.ID,
                FieldTraitConfigTable.TRAIT_IDS
            ),
            where = "${FieldTraitConfigTable.STUDY_ID} = ?",
            whereArgs = arrayOf(studyId.toString())
        ).use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getInt(cursor.getColumnIndexOrThrow(FieldTraitConfigTable.ID))
                val traitIds = cursor.getString(cursor.getColumnIndexOrThrow(FieldTraitConfigTable.TRAIT_IDS)) ?: ""
                FieldTraitConfig(id, studyId, traitIds)
            } else {
                null
            }
        }
    }

    /**
     * Check if a field has been customized (has any trait configuration).
     * Returns true only if a config row exists for this study.
     */
    fun hasFieldTraitConfig(studyId: Int): Boolean {
        if (studyId < 0) return false
        return getFieldTraitConfig(studyId) != null
    }

    /**
     * Get the trait IDs for a field in their custom order.
     * Returns empty list if field has no custom configuration.
     */
    fun getTraitIdsInOrder(studyId: Int): List<String> {
        val config = getFieldTraitConfig(studyId) ?: return emptyList()
        return config.getTraitIds()
    }

    /**
     * Initialize or update field trait configuration.
     * This creates the row if it doesn't exist, or updates it if it does.
     */
    fun setTraitConfiguration(studyId: Int, traitIdsInOrder: List<String>) {
        if (studyId < 0) {
            return
        }

        val traitIdsStr = traitIdsInOrder.joinToString(",")

        Log.d(TAG, "setTraitConfiguration: studyId=$studyId, ${traitIdsInOrder.size} traits in order")

        withDatabase { db ->
            val values = contentValuesOf(
                FieldTraitConfigTable.STUDY_ID to studyId,
                FieldTraitConfigTable.TRAIT_IDS to traitIdsStr,
                FieldTraitConfigTable.UPDATED_AT to System.currentTimeMillis().toString()
            )

            // Try to update first, if no rows affected, insert
            val updated = db.update(
                FieldTraitConfigTable.TABLE_NAME,
                values,
                "${FieldTraitConfigTable.STUDY_ID} = ?",
                arrayOf(studyId.toString())
            )

            if (updated == 0) {
                // If no existing row, insert a new one
                values.put(FieldTraitConfigTable.CREATED_AT, System.currentTimeMillis().toString())
                db.insert(FieldTraitConfigTable.TABLE_NAME, null, values)
            }
        }
    }

    /**
     * Delete the field trait configuration for a study.
     * This resets the field to use global defaults.
     */
    fun deleteFieldTraitConfig(studyId: Int) {
        if (studyId < 0) return

        withDatabase { db ->
            val deleted = db.delete(
                FieldTraitConfigTable.TABLE_NAME,
                "${FieldTraitConfigTable.STUDY_ID} = ?",
                arrayOf(studyId.toString())
            )
            Log.d(TAG, "deleteFieldTraitConfig: deleted $deleted row(s) for studyId=$studyId")
        }
    }

    /**
     * Clear all field trait configurations (used during reset/cleanup).
     */
    fun deleteAllConfigs() {
        withDatabase { db ->
            val deleted = db.delete(FieldTraitConfigTable.TABLE_NAME, null, null)
            Log.d(TAG, "deleteAllConfigs: deleted $deleted row(s)")
        }
    }
}

/**
 * Data class representing a field's trait configuration.
 */
data class FieldTraitConfig(
    val id: Int,
    val studyId: Int,
    val traitIds: String  // comma-separated visible trait IDs in order
) {
    fun getTraitIds(): List<String> = traitIds.parseIds()
}

/**
 * Parse comma-separated list of IDs, filtering out empty strings.
 */
internal fun String.parseIds(): List<String> {
    return if (isBlank()) {
        emptyList()
    } else {
        split(",").filter { it.isNotBlank() }
    }
}
