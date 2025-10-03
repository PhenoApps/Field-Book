package com.fieldbook.shared.utilities

import com.fieldbook.shared.database.models.FieldObject
import com.fieldbook.shared.preferences.GeneralKeys
import com.russhwolf.settings.Settings

/**
 * Field Switcher implementation allows an object with context to switch currently selected field.
 * This handles all necessary preferences and database updates.
 */
class FieldSwitchImpl() {

    private val preferences: Settings = Settings()

    companion object {
        private const val TAG = "FieldSwitchImpl"
        private val POSSIBLE_COLUMN_IDS = listOf("col", "column", "column_id", "range")
        private val POSSIBLE_ROW_IDS = listOf("row", "row_id")
    }

    fun switchField(field: FieldObject?) {
        if (field != null && field.exp_id != -1 && field.date_import.isNotBlank()) {

            val uniqueId = field.unique_id
            val primary = field.primary_id
            val secondary = field.secondary_id

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
