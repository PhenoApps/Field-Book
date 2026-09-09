package com.fieldbook.tracker.zpl

import android.content.ContentValues
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import com.fieldbook.tracker.database.LabelTemplateTable
import com.fieldbook.tracker.database.withDatabase
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages ZPL templates stored in the database's label_templates table.
 * Placeholders assignments are still stored in SharedPreferences as JSON.
 */
@Singleton
class TemplateRepository @Inject constructor(private val prefs: SharedPreferences) {

    companion object {
        private const val TAG = "TemplateRepository"
        const val KEY_ASSIGNMENTS_JSON = "zpl_template_assignments_json"
        const val KEY_SELECTED_TEMPLATE_ID = "zpl_selected_template_id"
        const val KEY_STUDY_ASSIGNMENTS_PREFIX = "zpl_study_assignments_"

        val BUILT_IN_TEMPLATES = mapOf(
            "3×2 Simple" to listOf(
                "^XA",
                "^POI",
                "^PW609",
                "^LL0406",
                "^FO0,25",
                "^FB599,2,0,C,0",
                "^A0,40,",
                "^FD{text1}^FS",
                "^FO180,120",
                "^BQ,,5",
                "^FDMA,{qrcode1}^FS",
                "^XZ"
            ).joinToString("\n"),
            "3×2 Detailed" to listOf(
                "^XA",
                "^POI",
                "^PW609",
                "^LL0406",
                "^FO0,25",
                "^FB599,2,0,C,0",
                "^A0,40,",
                "^FD{text1}^FS",
                "^FO30,120",
                "^BQ,,5",
                "^FDMA,{qrcode1}^FS",
                "^FO260,140",
                "^FB349,2,0,C,0",
                "^A0,30,",
                "^FD{text2}^FS",
                "^FO260,270",
                "^FB349,2,0,C,0",
                "^A0,28,",
                "^FD{text3}^FS",
                "^FO260,320",
                "^FB349,2,0,C,0",
                "^A0,28,",
                "^FD{text4}^FS",
                "^XZ"
            ).joinToString("\n"),
            "2×1 Simple" to listOf(
                "^XA",
                "^POI",
                "^PW406",
                "^LL0203",
                "^FO0,10",
                "^FB399,2,0,C,0",
                "^A0,30,",
                "^FD{text1}^FS",
                "^FO125,50",
                "^BQ,,4",
                "^FDMA,{qrcode1}^FS",
                "^XZ"
            ).joinToString("\n"),
            "2×1 Detailed" to listOf(
                "^XA",
                "^POI",
                "^PW406",
                "^LL0203",
                "^FO15,50",
                "^BQ,,4",
                "^FDMA,{qrcode1}^FS",
                "^FO0,10",
                "^FB406,1,0,C,0",
                "^A0,28,",
                "^FD{text1}^FS",
                "^FO155,60",
                "^FB250,1,0,C,0",
                "^A0,24,",
                "^FD{text2}^FS",
                "^FO155,130",
                "^FB250,1,0,C,0",
                "^A0,24,",
                "^FD{text3}^FS",
                "^FO155,155",
                "^FB250,1,0,C,0",
                "^A0,24,",
                "^FD{text4}^FS",
                "^XZ"
            ).joinToString("\n")
        )
    }

    fun getBuiltInTemplates(): Map<String, String> = BUILT_IN_TEMPLATES

    /**
     * Ensures built-in templates exist in the label_templates table.
     */
    fun ensureBuiltInTemplatesExist() {
        val existingNames = getAllTemplates().values.toSet()
        for ((name, zpl) in BUILT_IN_TEMPLATES) {
            if (name !in existingNames) {
                saveTemplate(name, zpl)
            }
        }
    }

    /**
     * Returns all stored templates as an id -> name map.
     */
    fun getAllTemplates(): Map<String, String> = withDatabase { db ->
        val result = mutableMapOf<String, String>()
        db.query(LabelTemplateTable.TABLE_NAME, arrayOf(LabelTemplateTable.ID, LabelTemplateTable.NAME), null, null, null, null, null)
            .use { cursor ->
                while (cursor.moveToNext()) {
                    result[cursor.getString(0)] = cursor.getString(1)
                }
            }
        result
    } ?: emptyMap()

    /**
     * Returns the ZPL content for the given ID.
     */
    fun getTemplate(id: String): String? = withDatabase { db ->
        db.query(LabelTemplateTable.TABLE_NAME, arrayOf(LabelTemplateTable.ZPL), "${LabelTemplateTable.ID} = ?", arrayOf(id), null, null, null)
            .use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    /**
     * Returns the template name for the given ID.
     */
    fun getTemplateName(id: String): String? = withDatabase { db ->
        db.query(LabelTemplateTable.TABLE_NAME, arrayOf(LabelTemplateTable.NAME), "${LabelTemplateTable.ID} = ?", arrayOf(id), null, null, null)
            .use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    /**
     * Saves or overwrites a template in the database.
     * Returns the ID of the saved template.
     */
    fun saveTemplate(name: String, zpl: String): String? = withDatabase { db ->
        val values = ContentValues().apply {
            put(LabelTemplateTable.NAME, name)
            put(LabelTemplateTable.ZPL, zpl)
        }

        // Check if name exists
        val existingId =
            db.query(LabelTemplateTable.TABLE_NAME, arrayOf(LabelTemplateTable.ID), "${LabelTemplateTable.NAME} = ?", arrayOf(name), null, null, null)
                .use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0) else null
                }

        if (existingId != null) {
            db.update(LabelTemplateTable.TABLE_NAME, values, "${LabelTemplateTable.ID} = ?", arrayOf(existingId))
            existingId
        } else {
            db.insert(LabelTemplateTable.TABLE_NAME, null, values).toString()
        }
    }

    /**
     * Deletes a template by ID.
     */
    fun deleteTemplate(id: String) = withDatabase { db ->
        db.delete(LabelTemplateTable.TABLE_NAME, "${LabelTemplateTable.ID} = ?", arrayOf(id))
        if (getSelectedTemplateId() == id) {
            clearSelection()
        }
    }

    /**
     * Returns the currently selected template ID from preferences.
     */
    fun getSelectedTemplateId(): String? {
        val selectedId = prefs.getString(KEY_SELECTED_TEMPLATE_ID, null) ?: return null
        return if (getAllTemplates().containsKey(selectedId)) selectedId else null
    }

    /**
     * Sets the selected template ID.
     */
    fun setSelectedTemplateId(id: String?) {
        prefs.edit {
            if (id == null) remove(KEY_SELECTED_TEMPLATE_ID)
            else putString(KEY_SELECTED_TEMPLATE_ID, id)
        }
    }

    fun clearSelection() {
        setSelectedTemplateId(null)
    }

    /**
     * Returns placeholder assignments for a specific study.
     * Stored as a JSON map of placeholder -> field option.
     */
    fun getStudyAssignments(studyId: Int): Map<String, String> {
        val key = "$KEY_STUDY_ASSIGNMENTS_PREFIX$studyId"
        val json = prefs.getString(key, null) ?: return emptyMap()
        return try {
            val root = JSONObject(json)
            val result = mutableMapOf<String, String>()
            val keys = root.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                result[k] = root.getString(k)
            }
            result
        } catch (e: JSONException) {
            Log.w(TAG, "Malformed study assignments JSON, returning empty map", e)
            emptyMap()
        }
    }

    /**
     * Saves placeholder assignments for a specific study.
     */
    fun saveStudyAssignments(studyId: Int, assignments: Map<String, String>) {
        val key = "$KEY_STUDY_ASSIGNMENTS_PREFIX$studyId"
        val root = JSONObject()
        for ((k, v) in assignments) {
            root.put(k, v)
        }
        prefs.edit {
            putString(key, root.toString())
        }
    }

    /**
     * Placeholder field assignments are still stored in Prefs, keyed by template NAME
     * for backwards compatibility and easy name-based lookups in the UI.
     */
    fun getAssignments(templateName: String): Map<String, String> {
        val json = prefs.getString(KEY_ASSIGNMENTS_JSON, null) ?: return emptyMap()
        return try {
            val root = JSONObject(json)
            val templateObj = root.optJSONObject(templateName) ?: return emptyMap()
            val result = mutableMapOf<String, String>()
            val keys = templateObj.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                result[key] = templateObj.getString(key)
            }
            result
        } catch (e: JSONException) {
            Log.w(TAG, "Malformed assignments JSON, returning empty map", e)
            emptyMap()
        }
    }

    fun saveAssignments(templateName: String, assignments: Map<String, String>) {
        val json = prefs.getString(KEY_ASSIGNMENTS_JSON, null)
        val root = if (json != null) {
            try {
                JSONObject(json)
            } catch (e: JSONException) {
                JSONObject()
            }
        } else JSONObject()

        val templateObj = JSONObject()
        for ((key, value) in assignments) {
            templateObj.put(key, value)
        }
        root.put(templateName, templateObj)
        prefs.edit {
            putString(KEY_ASSIGNMENTS_JSON, root.toString())
        }
    }
}
