package com.fieldbook.tracker.activities.brapi.io

import com.fieldbook.tracker.objects.FieldObject
import org.json.JSONArray

/**
 * Tracks which observation levels of each BrAPI study have been imported as fields,
 * and which levels the study's units had at import time (FieldObject.studyDbLevels).
 * A study stays available for import until each of its known levels has a field.
 * Level names are compared ignoring case.
 */
class BrapiImportedLevels(fields: List<FieldObject>) {

    companion object {

        //written as a json array by BrAPIServiceV2.saveStudyDetails
        fun decode(json: String?): List<String> = try {
            if (json.isNullOrBlank()) emptyList()
            else JSONArray(json).let { array -> (0 until array.length()).map { array.getString(it) } }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private val imported = hashMapOf<String, MutableSet<String>>()
    private val available = hashMapOf<String, MutableSet<String>>()

    init {
        fields.filter { it.studyId >= 0 && !it.studyDbId.isNullOrEmpty() }.forEach { field ->

            val studyDbId = field.studyDbId

            //the entry is created even without a level so the study still counts as imported
            val importedLevels = imported.getOrPut(studyDbId) { hashSetOf() }
            field.observationLevel?.takeIf { it.isNotEmpty() }?.let { importedLevels.add(it.lowercase()) }

            available.getOrPut(studyDbId) { hashSetOf() }
                .addAll(decode(field.studyDbLevels).map { it.lowercase() })
        }
    }

    /**
     * Imported level names for the study, lowercased.
     */
    fun importedLevels(studyDbId: String): Set<String> = imported[studyDbId].orEmpty()

    fun isImported(studyDbId: String, level: String?): Boolean =
        level != null && level.lowercase() in importedLevels(studyDbId)

    /**
     * True once every level the study's units had is imported.
     * Fields imported before levels were recorded have no level list, so their studies count as fully imported.
     */
    fun isFullyImported(studyDbId: String): Boolean {
        val importedLevels = imported[studyDbId] ?: return false
        val availableLevels = available[studyDbId].orEmpty()
        return importedLevels.containsAll(availableLevels)
    }
}
