package com.fieldbook.shared.utilities

import com.fieldbook.shared.database.models.FieldObject
import com.fieldbook.shared.database.repository.ObservationUnitAttributeRepository
import com.fieldbook.shared.database.repository.StudyRepository
import com.fieldbook.shared.preferences.GeneralKeys
import com.russhwolf.settings.Settings

/**
 * Field Switcher implementation allows an object with context to switch currently selected field.
 * This handles all necessary preferences and database updates.
 */
class FieldSwitchImpl(
    private val repo: ObservationUnitAttributeRepository? = null,
    private val studyRepository: StudyRepository? = null
) {

    private val preferences: Settings = Settings()

    companion object {
        private const val TAG = "FieldSwitchImpl"
        private val POSSIBLE_COLUMN_IDS = listOf("col", "column", "column_id", "range")
        private val POSSIBLE_ROW_IDS = listOf("row", "row_id")
    }

    fun switchField(field: FieldObject?) {
        if (field != null && field.exp_id != -1 && field.date_import.isNotBlank()) {

            studyRepository?.switchField(field.exp_id)

            // Get all entry props from repository if available, otherwise empty list
            val entryProps =
                repo?.getAllNames(field.exp_id.toLong())?.toMutableList() ?: mutableListOf()

            // remove unique id as a choice for the initial primary/secondary ids
            val uniqueId = field.unique_id
            entryProps.remove(uniqueId)

            // attempt to automatically select based on previous selections saved in settings
            val primaryName = preferences.getString(GeneralKeys.PRIMARY_NAME.key, "")
            val secondaryName = preferences.getString(GeneralKeys.SECONDARY_NAME.key, "")

            // add some basic logic to match row/col or block/rep if it exists, otherwise just use the first two
            val hasPrimary = entryProps.indexOfFirst { it.equals(primaryName, true) }
            val hasRow = entryProps.indexOfFirst { it.lowercase() in POSSIBLE_ROW_IDS }
            val hasRange = entryProps.indexOfFirst { it.equals("range", true) }
            val hasBlock = entryProps.indexOfFirst { it.equals("block", true) }

            val primary = if (field.primary_id == "null" || field.primary_id.isEmpty()) {
                when {
                    hasPrimary != -1 -> entryProps.removeAt(hasPrimary)
                    hasRow != -1 -> entryProps.removeAt(hasRow)
                    hasRange != -1 -> entryProps.removeAt(hasRange)
                    hasBlock != -1 -> entryProps.removeAt(hasBlock)
                    entryProps.isNotEmpty() -> entryProps.removeAt(0)
                    else -> ""
                }
            } else field.primary_id

            val hasSecondary = entryProps.indexOfFirst { it.equals(secondaryName, true) }
            val hasCol = entryProps.indexOfFirst { it.lowercase() in POSSIBLE_COLUMN_IDS }
            val hasPlot = entryProps.indexOfFirst { it.equals("plot", true) }

            val secondary = if (field.secondary_id == "null" || field.secondary_id.isEmpty()) {
                when {
                    hasSecondary != -1 -> entryProps.removeAt(hasSecondary)
                    hasCol != -1 -> entryProps.removeAt(hasCol)
                    hasPlot != -1 -> entryProps.removeAt(hasPlot)
                    entryProps.isNotEmpty() -> entryProps.removeAt(0)
                    else -> ""
                }
            } else field.secondary_id

            // save preferences using Settings API
            preferences.putInt(GeneralKeys.SELECTED_FIELD_ID.key, field.exp_id)
            preferences.putString(GeneralKeys.FIELD_FILE.key, field.exp_name)
            preferences.putString(GeneralKeys.FIELD_ALIAS.key, field.exp_alias)
            preferences.putString(GeneralKeys.FIELD_OBS_LEVEL.key, field.observation_level ?: "")
            preferences.putString(GeneralKeys.UNIQUE_NAME.key, uniqueId)
            preferences.putString(GeneralKeys.PRIMARY_NAME.key, primary)
            preferences.putString(GeneralKeys.SECONDARY_NAME.key, secondary)
            preferences.putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED.key, true)
            preferences.putString(GeneralKeys.LAST_PLOT.key, "")
        }
    }
}
