package com.fieldbook.tracker.utilities

import android.content.Context
import android.util.Log
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.interfaces.FieldSwitcher
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.preferences.GeneralKeys
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject

/**
 * Field Switcher implementation allows an object with context to switch currently selected field.
 * This handles all necessary preferences and database updates.
 */
class FieldSwitchImpl @Inject constructor(@ActivityContext private val context: Context): FieldSwitcher {

    companion object {
        private const val TAG = "FieldSwitchImpl"
        private val POSSIBLE_COLUMN_IDS = listOf("col", "column", "column_id", "range")
        private val POSSIBLE_ROW_IDS = listOf("row", "row_id")
    }

    @Inject
    lateinit var database: DataHelper

    private val preferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    override fun switchField(studyId: Int) {
        if (!::database.isInitialized) {
            database = DataHelper(context)
        }
        val f = database.getFieldObject(studyId)
        switchField(f)
    }

    override fun switchField(field: FieldObject?) {

        if (field != null && field.exp_id != -1 && field.date_import != null && field.date_import.isNotBlank()) {

            database.switchField(field.exp_id)

            //get all entry props from field
            val entryProps = database.getAllObservationUnitAttributeNames(field.exp_id).toMutableList()

            //remove unique id as a choice for the initial primary/secondary ids
            val uniqueId = field.unique_id
            entryProps.remove(uniqueId)

            //add some basic logic to match row/col or block/rep if it exists, otherwise just use the first two
            val hasRow = entryProps.indexOfFirst { it.lowercase() in POSSIBLE_ROW_IDS }
            val hasBlock = entryProps.indexOfFirst { it.equals("block", true) }

            val primary = if (field.primary_id == "null" || field.primary_id == null || field.primary_id.isEmpty()) {
                if (hasRow != -1) {
                    entryProps.removeAt(hasRow)
                } else if (hasBlock != -1) {
                    entryProps.removeAt(hasBlock)
                } else if (entryProps.isNotEmpty()) entryProps.removeAt(0) else ""
            } else field.primary_id

            val hasCol = entryProps.indexOfFirst { it.lowercase() in POSSIBLE_COLUMN_IDS }
            val hasRep = entryProps.indexOfFirst { it.equals("rep", true) }

            val secondary = if (field.secondary_id == "null" || field.secondary_id == null || field.secondary_id.isEmpty()) {
                if (hasCol != -1) {
                    entryProps.removeAt(hasCol)
                } else if (hasRep != -1) {
                    entryProps.removeAt(hasRep)
                } else if (entryProps.isNotEmpty()) entryProps.removeAt(0) else ""
            } else field.secondary_id

            Log.d(TAG, "Field Switched: ${field.exp_id}\tUnique: $uniqueId\tPrimary: $primary\tSecondary: $secondary")

            //clear field selection after updates
            preferences.edit().putInt(GeneralKeys.SELECTED_FIELD_ID, field.exp_id)
                .putString(GeneralKeys.FIELD_FILE, field.exp_name)
                .putString(GeneralKeys.FIELD_ALIAS, field.exp_alias)
                .putString(GeneralKeys.FIELD_OBS_LEVEL, field.observation_level)
                .putString(GeneralKeys.UNIQUE_NAME, field.unique_id)
                .putString(GeneralKeys.PRIMARY_NAME, primary)
                .putString(GeneralKeys.SECONDARY_NAME, secondary)
                .putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, true)
                .putString(GeneralKeys.LAST_PLOT, null).apply()

        }
    }
}