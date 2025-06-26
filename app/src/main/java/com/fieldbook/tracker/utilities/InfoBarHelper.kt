package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.core.content.edit
import androidx.fragment.app.FragmentManager
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.AttributeAdapter.AttributeModel
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.dialogs.InfobarAttributeChooserDialog
import com.fieldbook.tracker.objects.InfoBarModel
import com.fieldbook.tracker.preferences.DropDownKeyModel
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject

/**
 * Helper class for handling all infobar data and logic.
 * Used in collect activity.
 */
class InfoBarHelper @Inject constructor(@ActivityContext private val context: Context)  {

    companion object {
        const val TAG = "InfoBarHelper"
    }

    private val preferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    /**
     * Reads the number of preference infobars and creates models for each one
     */
    fun getInfoBarData(): ArrayList<InfoBarModel> {

        val database: DataHelper = (context as CollectActivity).getDatabase()

        val studyId = context.studyId

        val noDataString = context.getString(R.string.main_infobar_data_missing)
        val selectDataString = context.getString(R.string.main_infobar_select_data)

        val fieldNameChoice = context.getString(R.string.field_name_attribute)

        val attributes =
            (database.getAllObservationUnitAttributeNames(studyId.toInt()) + fieldNameChoice).toMutableList()

        val entryId = context.observationUnit

        //get the preference number of infobars to load, default to 3 if pref isn't set
        val numInfoBars: Int = preferences.getInt(PreferenceKeys.INFOBAR_NUMBER, 3)

        //initialize a list of infobar models that will be served to the adapter
        val infoBarModels = ArrayList<InfoBarModel>()

        //iterate and build the arraylist
        for (i in 0 until numInfoBars) {

            //get the preferred infobar label
            //adapter preferred values are saved as DROP1, DROP2, DROP3, DROP4, DROP5 in preferences, initialize the label with it
            val keys = GeneralKeys.getDropDownKeys(i)
            val (attributeLabel, traitId) = keys.getValues(context)

            val attribute = if (traitId == DropDownKeyModel.DEFAULT_TRAIT_ID) {
                AttributeModel(
                    when (attributeLabel in attributes) {
                        true -> attributeLabel
                        else -> {
                            if (attributes.isEmpty()) {
                                selectDataString
                            } else attributes.removeAt(0)
                        }
                    }, null
                )
            } else {
                val trait = database.getTraitById(traitId)
                AttributeModel(trait.name, trait = trait)
            }

            //query the database for the label's value
            val value = if (attribute.label == selectDataString) noDataString
            else (context).queryForLabelValue(entryId, attribute)

            context.preference.edit {
                putString(keys.attributeKey, attribute.label)
                putString(keys.traitKey, attribute.trait?.id ?: "-1")
            }

            val isWordWrapped =
                preferences.getBoolean(GeneralKeys.getIsInfoBarWordWrapped(i), false)

            infoBarModels.add(InfoBarModel(attribute.label, value, isWordWrapped))
        }

        return infoBarModels
    }

    /**
     * Sets the infoBar position state and calls the dialog for users to choose an attribute.
     */
    fun showInfoBarChoiceDialog(fragmentManager: FragmentManager, position: Int) {
        try {
            val dialog = InfobarAttributeChooserDialog.newInstance(position)
            dialog.show(fragmentManager, "infobarAttributeChooserDialog")
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(TAG, "Error showing infobar dialog: ${e.message}")
        }
    }
}