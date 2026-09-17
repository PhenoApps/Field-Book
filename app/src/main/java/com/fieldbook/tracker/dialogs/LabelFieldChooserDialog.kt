package com.fieldbook.tracker.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.AttributeAdapter
import com.fieldbook.tracker.utilities.BackgroundUiTask

/**
 * Extension of AttributeChooserDialog that adds label-specific options
 * (date, blank, default value) to the attributes tab. Used by the label print
 * trait layout for assigning field values to template placeholders.
 *
 * The user sees the same tabbed interface as infobars (attributes, traits, other)
 * with these additional special options prepended to the attributes list.
 */
class LabelFieldChooserDialog(
    private val dateLabel: String = "",
    private val blankLabel: String = "",
    private val defaultValueLabel: String = ""
) : AttributeChooserDialog(
    showTraits = true,
    showOther = true,
    showSystemAttributes = true
) {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState)
        (dialog as? AlertDialog)?.setTitle(
            context?.getString(R.string.dialog_att_chooser_title_default)
        )

        // Override the show listener to inject special options after data loads
        dialog.setOnShowListener {
            toggleProgressVisibility(true)
            BackgroundUiTask.execute(
                backgroundBlock = ::loadDataWithSpecialOptions,
                uiBlock = ::setupTabLayout,
                onCanceled = ::setupTabLayout
            )
        }

        return dialog
    }

    /**
     * Loads data from the database and prepends special label options
     * (default value, date, blank) to the attributes list.
     */
    private fun loadDataWithSpecialOptions() {
        try {
            val activity = requireActivity() as CollectActivity
            attributes =
                activity.getDatabase().getAllObservationUnitAttributeNames(activity.studyId.toInt())
            val attributesList = attributes.toMutableList()

            // Add system attribute (field name)
            attributesList.add(0, getString(R.string.field_name_attribute))

            // Prepend special label options at the top
            val specialOptions = mutableListOf<String>()
            if (blankLabel.isNotEmpty()) specialOptions.add(0, blankLabel)
            if (dateLabel.isNotEmpty()) specialOptions.add(0, dateLabel)
            if (defaultValueLabel.isNotEmpty()) specialOptions.add(0, defaultValueLabel)
            attributesList.addAll(0, specialOptions)

            attributes = attributesList.toTypedArray()
            visibleTraits = activity.getDatabase().allTraitObjects.toTypedArray()
            nonVisibleTraits = visibleTraits.filter { !it.visible }.toTypedArray()
            visibleTraits = visibleTraits.filter { it.visible }.toTypedArray()
        } catch (e: Exception) {
            Log.d(TAG, "Error loading data in LabelFieldChooserDialog")
            e.printStackTrace()
        }
    }
}
