package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.dialogs.CollectAttributeChooserDialog
import com.fieldbook.tracker.objects.InfoBarModel
import com.fieldbook.tracker.preferences.GeneralKeys
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

//    @Inject
//    override lateinit var database: DataHelper

    private val ad = CollectAttributeChooserDialog(context as CollectActivity)

    private val preferences: SharedPreferences by lazy {
        PreferenceManager.getDefaultSharedPreferences(context)
    }

    /**
     * Reads the number of preference infobars and creates models for each one
     */
    fun getInfoBarData(): ArrayList<InfoBarModel> {

        val database: DataHelper = (context as CollectActivity).getDatabase()

        //get the preference number of infobars to load, default to 3 if pref isn't set
        val numInfoBars: Int = preferences.getInt(GeneralKeys.INFOBAR_NUMBER, 3)

        //get all plot attribute names for the study
        val attributes: MutableList<String> = ArrayList(database.rangeColumnNames.toList())
        attributes.add(0, context.getString(R.string.field_name_attribute))

        //get all traits for this study
        val traits = database.allTraitObjects

        //create a new array with just trait names
        val traitNames = ArrayList<String>()
        if (traits != null) {
            for (t in traits) {
                traitNames.add(t.name)
            }
        }

        //initialize a list of infobar models that will be served to the adapter
        val infoBarModels = ArrayList<InfoBarModel>()

        //iterate and build the arraylist
        for (i in 0 until numInfoBars) {

            //get the preferred infobar label, default to "Select" if it doesn't exist for this position
            //adapter preferred values are saved as DROP1, DROP2, DROP3, DROP4, DROP5 in preferences, initialize the label with it
            var initialLabel: String = preferences.getString(
                "DROP$i",
                context.getString(R.string.infobars_attribute_placeholder)
            ) ?: context.getString(R.string.infobars_attribute_placeholder)

            //check if the label is an attribute or a trait, this will decide how to query the database for the value
            var isAttribute = attributes.contains(initialLabel)

            //check if the label actually exists in the attributes/traits (this will reset on field switch)
            //if it doesn't exist, default to the next attribute in the list
            if (!isAttribute) {
                if (!traitNames.contains(initialLabel)) {
                    if (i in attributes.indices) {
                        initialLabel = attributes[i]
                        isAttribute = true
                    } else {
                        initialLabel = context.getString(R.string.infobars_attribute_placeholder)
                    }
                }
            }

            //query the database for the label's value
            (context as? CollectActivity)?.getRangeBox()?.getPlotID()?.let { plot ->

                val value = (context).queryForLabelValue(plot, initialLabel, isAttribute)

                infoBarModels.add(InfoBarModel(initialLabel, value))
            }
        }

        return infoBarModels
    }

    /**
     * Sets the infoBar position state and calls the dialog for users to choose an attribute.
     */
    fun showInfoBarChoiceDialog(position: Int) {

        try {

            ad.infoBarPosition = position

            ad.show()

        } catch (e: Exception) {

            e.printStackTrace()

            Log.d(TAG, "Error showing infobar dialog: ${e.message}")
        }
    }
}