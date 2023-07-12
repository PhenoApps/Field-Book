package com.fieldbook.tracker.utilities

import android.content.Context
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

    @Inject
    lateinit var database: DataHelper

    private val prefs by lazy {
        context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
    }

    override fun switchField(studyId: Int) {
        if (!::database.isInitialized) {
            database = DataHelper(context)
        }
        val f = database.getFieldObject(studyId)
        switchField(f)
    }

    override fun switchField(field: FieldObject?) {

        if (field != null && field.exp_id != -1) {

            database.switchField(field.exp_id)

            //clear field selection after updates
            prefs.edit().putInt(GeneralKeys.SELECTED_FIELD_ID, field.exp_id)
                .putString(GeneralKeys.FIELD_FILE, field.exp_name)
                .putString(GeneralKeys.FIELD_ALIAS, field.exp_alias)
                .putString(GeneralKeys.FIELD_OBS_LEVEL, field.observation_level)
                .putString(GeneralKeys.UNIQUE_NAME, field.unique_id)
                .putString(GeneralKeys.PRIMARY_NAME, field.primary_id)
                .putString(GeneralKeys.SECONDARY_NAME, field.secondary_id)
                .putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED, true)
                .putString(GeneralKeys.LAST_PLOT, null).apply()

        }
    }
}