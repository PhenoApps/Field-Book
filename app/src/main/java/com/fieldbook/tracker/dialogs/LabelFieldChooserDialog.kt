package com.fieldbook.tracker.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.utilities.BackgroundUiTask

/**
 * AttributeChooserDialog used by the label print trait layout for assigning field values
 * to template placeholders. It shows the same tabbed interface as infobars (attributes,
 * traits, other), with the field name added to the top of the attributes list.
 */
class LabelFieldChooserDialog : AttributeChooserDialog(
    showTraits = true,
    showOther = true,
    showSystemAttributes = true
) {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        (dialog as? AlertDialog)?.setTitle(
            context?.getString(R.string.dialog_att_chooser_title_default)
        )

        // Override the show listener to add the field name option after data loads
        dialog.setOnShowListener {
            toggleProgressVisibility(true)
            BackgroundUiTask.execute(
                backgroundBlock = ::loadData,
                uiBlock = ::setupTabLayout,
                onCanceled = ::setupTabLayout
            )
        }

        return dialog
    }

    /**
     * Loads attributes and traits from the database, with the field name as the first attribute.
     */
    private fun loadData() {
        try {
            val activity = requireActivity() as CollectActivity
            val studyAttributes =
                activity.getDatabase().getAllObservationUnitAttributeNames(activity.studyId.toInt())

            attributes = arrayOf(getString(R.string.field_name_attribute)) + studyAttributes
            visibleTraits = activity.getDatabase().allTraitObjects.toTypedArray()
            nonVisibleTraits = visibleTraits.filter { !it.visible }.toTypedArray()
            visibleTraits = visibleTraits.filter { it.visible }.toTypedArray()
        } catch (e: Exception) {
            Log.d(TAG, "Error loading data in LabelFieldChooserDialog")
            e.printStackTrace()
        }
    }
}
