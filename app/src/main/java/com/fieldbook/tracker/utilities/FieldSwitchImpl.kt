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
import androidx.core.content.edit

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

        if (field != null && field.studyId != -1 && field.dateImport != null && field.dateImport.isNotBlank()) {

            database.switchField(field.studyId)

            //get all entry props from field
            val entryProps = database.getAllObservationUnitAttributeNames(field.studyId).toMutableList()

            //remove unique id as a choice for the initial primary/secondary ids
            val uniqueId = field.uniqueId
            entryProps.remove(uniqueId)

            //attempt to automatically select based on previous selections
            val primaryName = preferences.getString(GeneralKeys.PRIMARY_NAME, "")
            val secondaryName = preferences.getString(GeneralKeys.SECONDARY_NAME, "")

            //add some basic logic to match row/col or block/rep if it exists, otherwise just use the first two
            val hasPrimary = entryProps.indexOfFirst { it.equals(primaryName, true) }
            val hasRow = entryProps.indexOfFirst { it.lowercase() in POSSIBLE_ROW_IDS }
            val hasRange = entryProps.indexOfFirst { it.equals("range", true) }
            val hasBlock = entryProps.indexOfFirst { it.equals("block", true) }

            val primary = if (field.primaryId == "null" || field.primaryId == null || field.primaryId.isEmpty()) {
                if (hasPrimary != -1) {
                    entryProps.removeAt(hasPrimary)
                } else if (hasRow != -1) {
                    entryProps.removeAt(hasRow)
                } else if (hasRange != -1) {
                    entryProps.removeAt(hasRange)
                } else if (hasBlock != -1) {
                    entryProps.removeAt(hasBlock)
                } else if (entryProps.isNotEmpty()) entryProps.removeAt(0) else ""
            } else field.primaryId

            val hasSecondary = entryProps.indexOfFirst { it.equals(secondaryName, true) }
            val hasCol = entryProps.indexOfFirst { it.lowercase() in POSSIBLE_COLUMN_IDS }
            val hasPlot = entryProps.indexOfFirst { it.equals("plot", true) }

            val secondary = if (field.secondaryId == "null" || field.secondaryId == null || field.secondaryId.isEmpty()) {
                if (hasSecondary != -1) {
                    entryProps.removeAt(hasSecondary)
                } else if (hasCol != -1) {
                    entryProps.removeAt(hasCol)
                } else if (hasPlot != -1) {
                    entryProps.removeAt(hasPlot)
                } else if (entryProps.isNotEmpty()) entryProps.removeAt(0) else ""
            } else field.secondaryId

            Log.d(TAG, "Field Switched: ${field.studyId}\tUnique: $uniqueId\tPrimary: $primary\tSecondary: $secondary")

            //clear field selection after updates
            preferences.edit {
                putInt(GeneralKeys.SELECTED_FIELD_ID, field.studyId)
                    .putString(GeneralKeys.FIELD_FILE, field.name)
                    .putString(GeneralKeys.FIELD_ALIAS, field.alias)
                    .putString(GeneralKeys.FIELD_OBS_LEVEL, field.observationLevel)
                    .putString(GeneralKeys.UNIQUE_NAME, field.uniqueId)
                    .putString(GeneralKeys.PRIMARY_NAME, primary)
                    .putString(GeneralKeys.SECONDARY_NAME, secondary)
                    .putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, true)
                    .putString(GeneralKeys.LAST_PLOT, null)
            }
        }
    }
}