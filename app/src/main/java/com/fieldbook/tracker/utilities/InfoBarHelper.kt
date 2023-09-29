package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.SharedPreferences
import android.provider.ContactsContract.Data
import android.util.Log
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.dialogs.CollectAttributeChooserDialog
import com.fieldbook.tracker.objects.InfoBarModel
import com.fieldbook.tracker.preferences.GeneralKeys
import dagger.hilt.android.qualifiers.ActivityContext
import java.util.Arrays
import java.util.StringJoiner
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

    private val ep: SharedPreferences by lazy {
        context.getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Reads the number of preference infobars and creates models for each one
     */
    fun getInfoBarData(): ArrayList<InfoBarModel> {

        //get the preference number of infobars to load
        val numInfoBars: Int = ep.getInt(GeneralKeys.INFOBAR_NUMBER, 2)

        //initialize a list of infobar models that will be served to the adapter
        val infoBarModels = ArrayList<InfoBarModel>()

        //iterate and build the arraylist
        for (i in 0 until numInfoBars) {

            var database : DataHelper = (context as CollectActivity).getDatabase();

            //ensure that the initialLabel is actually a plot attribute

            //get all plot attribute names for the study
            val attributes: List<String> = ArrayList(Arrays.asList(*database.rangeColumnNames))

            //get all traits for this study
            val traits = database.allTraitObjects

            //create a new array with just trait names
            val traitNames = ArrayList<String>()
            if (traits != null) {
                for (t in traits) {
                    traitNames.add(t.trait)
                }
            }

            //get the default label for the infobar using 'Select' (used in original adapter code)
            //      or the first item in the attributes list
            var defaultLabel = "Select"
            if (attributes.size > 0) {
                defaultLabel = attributes[0]
            }

            //get the preferred infobar label, default to above if it doesn't exist for this position
            //adapter preferred values are saved as DROP1, DROP2, DROP3, DROP4, DROP5 in preferences, intiailize the label with it
            var initialLabel: String = ep.getString("DROP$i", defaultLabel) ?: defaultLabel

            //check if the label is an attribute or a trait, this will decide how to query the database for the value
            val isAttribute = attributes.contains(initialLabel)

            //check if the label actually exists in the attributes/traits (this will reset on field switch)
            //if it doesn't exist, default to the first attribute in the list
            if (!isAttribute) {
                if (!traitNames.contains(initialLabel)) {
                    if (attributes.isNotEmpty()) {
                        initialLabel = attributes[0]
                    }
                }
            }

            //query the database for the label's value
            (context as? CollectActivity)?.getRangeBox()?.getPlotID()?.let { plot ->

                val value = (context as CollectActivity).queryForLabelValue(plot, initialLabel, isAttribute)

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