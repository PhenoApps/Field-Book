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

    //lowercased imported level names per study
    private val imported = hashMapOf<String, MutableSet<String>>()

    //recorded level names per study, keyed by lowercased name to keep the server's casing for queries
    private val available = hashMapOf<String, MutableMap<String, String>>()

    init {
        fields.filter { it.studyId >= 0 && !it.studyDbId.isNullOrEmpty() }.forEach { field ->

            val studyDbId = field.studyDbId

            //the entry is created even without a level so the study still counts as imported
            val importedLevels = imported.getOrPut(studyDbId) { hashSetOf() }
            field.observationLevel?.takeIf { it.isNotEmpty() }?.let { importedLevels.add(it.lowercase()) }

            val availableLevels = available.getOrPut(studyDbId) { linkedMapOf() }
            decode(field.studyDbLevels).forEach { availableLevels.putIfAbsent(it.lowercase(), it) }
        }
    }

    fun isImported(studyDbId: String, level: String?): Boolean =
        level != null && level.lowercase() in imported[studyDbId].orEmpty()

    /**
     * Levels recorded for the study by earlier imports, empty if none were recorded.
     */
    fun knownLevels(studyDbId: String): Collection<String> = available[studyDbId]?.values.orEmpty()

    /**
     * Recorded levels that haven't been imported yet,
     * or null when the study has no recorded levels and all of its units need to be fetched.
     */
    fun remainingLevels(studyDbId: String): List<String>? =
        knownLevels(studyDbId).filterNot { isImported(studyDbId, it) }.takeIf { it.isNotEmpty() }

    /**
     * True once every level the study's units had is imported.
     * Fields imported before levels were recorded have no level list, so their studies count as fully imported.
     */
    fun isFullyImported(studyDbId: String): Boolean {
        val importedLevels = imported[studyDbId] ?: return false
        return available[studyDbId].orEmpty().keys.all { it in importedLevels }
    }
}
