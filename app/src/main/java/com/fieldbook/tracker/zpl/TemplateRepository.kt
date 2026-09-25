package com.fieldbook.tracker.zpl

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.database.sqlite.SQLiteConstraintException
import android.util.Log
import androidx.core.content.edit
import com.fieldbook.tracker.database.LabelTemplateTable
import com.fieldbook.tracker.database.dao.ObservationVariableValueDao
import com.fieldbook.tracker.database.models.TraitAttributes
import com.fieldbook.tracker.database.withDatabase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import org.json.JSONException
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages ZPL templates stored in the database's label_templates table.
 * Placeholder assignments are stored in SharedPreferences as JSON, keyed by template ID.
 */
@Singleton
class TemplateRepository @Inject constructor(private val prefs: SharedPreferences) {

    companion object {
        private const val TAG = "TemplateRepository"
        const val KEY_ASSIGNMENTS_JSON = "zpl_template_assignments_by_id_json"
        const val KEY_DEFAULT_TEMPLATE_ID = "zpl_default_template_id"
        const val KEY_STUDY_ASSIGNMENTS_PREFIX = "zpl_study_template_assignments_"

        /**
         * Removes the default template and all saved assignments. Call after replacing the
         * database without its preferences, since these are keyed by template ID and would
         * otherwise point at unrelated templates in the new database.
         */
        @JvmStatic
        fun clearTemplatePreferences(prefs: SharedPreferences) {
            val keys = prefs.all.keys.filter {
                it == KEY_DEFAULT_TEMPLATE_ID || it == KEY_ASSIGNMENTS_JSON
                        || it.startsWith(KEY_STUDY_ASSIGNMENTS_PREFIX)
            }
            prefs.edit {
                keys.forEach { remove(it) }
            }
        }

        /** Built-in template used as the default when none is set. */
        const val BUILT_IN_DEFAULT_TEMPLATE_NAME = "2×1 Simple"

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

    /**
     * Sets the built-in default template as the default when no default is set.
     * The built-in templates themselves are installed with the database (see
     * ZplTemplateMigratorVersion22), so deleted built-ins stay deleted.
     */
    fun ensureDefaultTemplate() {
        if (getDefaultTemplateId() == null) {
            getAllTemplates().entries
                .find { it.value == BUILT_IN_DEFAULT_TEMPLATE_NAME }
                ?.let { setDefaultTemplateId(it.key) }
        }
    }

    /**
     * The template a trait prints with: its own template if it still exists, otherwise the
     * default template. Null when neither is available.
     */
    fun resolveTemplateId(traitTemplateId: String?): String? {
        if (!traitTemplateId.isNullOrEmpty() && getTemplateName(traitTemplateId) != null) {
            return traitTemplateId
        }
        ensureDefaultTemplate()
        return getDefaultTemplateId()
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
     * Saves an imported template without replacing an existing one: reuses a template with the
     * same ZPL, otherwise saves under the first free "name (n)".
     * Returns the ID of the template to use.
     */
    fun importTemplate(name: String, zpl: String): String? {
        val templates = getAllTemplates()

        // reuse an identical template, preferring the same name, so importing the same file
        // again (or traits that are skipped as duplicates) doesn't pile up copies
        val identical = templates.entries.filter { getTemplate(it.key) == zpl }
        (identical.find { it.value == name } ?: identical.firstOrNull())?.let { return it.key }

        return saveTemplate(uniqueName(name, templates.values.toSet()), zpl)
    }

    /**
     * Updates a template's name and ZPL by ID, so renaming keeps the same template.
     * Returns false if the update failed, e.g. the name belongs to another template.
     */
    fun updateTemplate(id: String, name: String, zpl: String): Boolean = withDatabase { db ->
        val values = ContentValues().apply {
            put(LabelTemplateTable.NAME, name)
            put(LabelTemplateTable.ZPL, zpl)
        }
        try {
            db.update(LabelTemplateTable.TABLE_NAME, values, "${LabelTemplateTable.ID} = ?", arrayOf(id)) > 0
        } catch (e: SQLiteConstraintException) {
            Log.w(TAG, "Template name already in use: $name", e)
            false
        }
    } ?: false

    /**
     * Saves a copy of a template under the first free "name (n)". Returns the copy's ID.
     */
    fun duplicateTemplate(id: String): String? {
        val name = getTemplateName(id) ?: return null
        val zpl = getTemplate(id) ?: return null
        return saveTemplate(uniqueName(name, getAllTemplates().values.toSet()), zpl)
    }

    private fun uniqueName(base: String, existingNames: Set<String>): String {
        var uniqueName = base
        var suffix = 2
        while (uniqueName in existingNames) {
            uniqueName = "$base ($suffix)"
            suffix++
        }
        return uniqueName
    }

    /**
     * Number of traits that use the template as their own (not through the default).
     */
    fun countTraitsUsing(id: String): Int =
        ObservationVariableValueDao.countByAttributeValue(TraitAttributes.PRINT_TEMPLATE_ID.key, id)

    /**
     * Deletes a template by ID, along with its saved assignments.
     * Traits that used it are cleared so they print with the default template.
     */
    fun deleteTemplate(id: String) {
        withDatabase { db ->
            db.delete(LabelTemplateTable.TABLE_NAME, "${LabelTemplateTable.ID} = ?", arrayOf(id))
        }
        ObservationVariableValueDao.clearAttributeValue(TraitAttributes.PRINT_TEMPLATE_ID.key, id)
        removeAssignments(id)
    }

    /**
     * Returns the default template ID from preferences.
     */
    fun getDefaultTemplateId(): String? {
        val defaultId = prefs.getString(KEY_DEFAULT_TEMPLATE_ID, null) ?: return null
        // single row lookup, this runs on every plot change through resolveTemplateId
        return if (getTemplateName(defaultId) != null) defaultId else null
    }

    /**
     * Sets the default template ID.
     */
    fun setDefaultTemplateId(id: String?) {
        prefs.edit {
            if (id == null) remove(KEY_DEFAULT_TEMPLATE_ID)
            else putString(KEY_DEFAULT_TEMPLATE_ID, id)
        }
    }

    /**
     * Placeholder -> field option assignments for a template in a study.
     * Assignments saved for this study override the template's last saved assignments,
     * which act as the starting point in studies that haven't configured the template.
     */
    fun getAssignments(studyId: Int, templateId: String): Map<String, String> {
        val studyLevel = readJsonMap(prefs.getString(studyAssignmentsKey(studyId, templateId), null)
            ?.let { json -> runCatching { JSONObject(json) }.getOrNull() })
        return getTemplateAssignments(templateId) + studyLevel
    }

    /**
     * The template's last saved assignments, independent of study.
     */
    private fun getTemplateAssignments(templateId: String): Map<String, String> =
        readJsonMap(prefs.getString(KEY_ASSIGNMENTS_JSON, null)?.let { json ->
            runCatching { JSONObject(json).optJSONObject(templateId) }.getOrNull()
        })

    /**
     * Saves assignments for a template in a study, and as the template's defaults for other studies.
     */
    fun saveAssignments(studyId: Int, templateId: String, assignments: Map<String, String>) {
        val root = prefs.getString(KEY_ASSIGNMENTS_JSON, null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() } ?: JSONObject()
        root.put(templateId, JSONObject(assignments))

        prefs.edit {
            putString(KEY_ASSIGNMENTS_JSON, root.toString())
            putString(studyAssignmentsKey(studyId, templateId), JSONObject(assignments).toString())
        }
    }

    /**
     * Removes a template's saved assignments, both its defaults and every study's.
     */
    private fun removeAssignments(templateId: String) {
        val root = prefs.getString(KEY_ASSIGNMENTS_JSON, null)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        root?.remove(templateId)

        val studyKeySuffix = "_$templateId"
        val studyKeys = prefs.all.keys.filter {
            it.startsWith(KEY_STUDY_ASSIGNMENTS_PREFIX) && it.endsWith(studyKeySuffix)
        }

        prefs.edit {
            if (root != null) putString(KEY_ASSIGNMENTS_JSON, root.toString())
            studyKeys.forEach { remove(it) }
        }
    }

    private fun studyAssignmentsKey(studyId: Int, templateId: String) =
        "$KEY_STUDY_ASSIGNMENTS_PREFIX${studyId}_$templateId"

    private fun readJsonMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        return try {
            obj.keys().asSequence().associateWith { obj.getString(it) }
        } catch (e: JSONException) {
            Log.w(TAG, "Malformed assignments JSON, ignoring", e)
            emptyMap()
        }
    }
}

/**
 * Gives classes Hilt doesn't create, like trait format parameters, the singleton [TemplateRepository].
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface TemplateRepositoryEntryPoint {
    fun templateRepository(): TemplateRepository

    companion object {
        fun get(context: Context): TemplateRepository = EntryPointAccessors
            .fromApplication(context.applicationContext, TemplateRepositoryEntryPoint::class.java)
            .templateRepository()
    }
}
