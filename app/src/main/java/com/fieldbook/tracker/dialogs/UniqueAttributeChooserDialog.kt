package com.fieldbook.tracker.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.BaseFieldActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.BackgroundUiTask

/**
 * Base dialog for choosing one of a field's unique attributes.
 * Only attributes whose values are unique across the field's entries are listed, so a choice always
 * maps back to a single entry.
 */
abstract class UniqueAttributeChooserDialog : AttributeChooserDialog(
    showTraits = false,
    showOther = false,
    showSystemAttributes = false
) {

    /** Title shown for the dialog, defined by the attribute being chosen. */
    protected abstract val titleResId: Int

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Create the base dialog
        val dialog = super.onCreateDialog(savedInstanceState) as AlertDialog

        // Set the title and show checkbox
        dialog.setTitle(titleResId)
        applyAllCheckbox.visibility = View.VISIBLE

        // Override the loading process with our own
        dialog.setOnShowListener {
            toggleProgressVisibility(true)
            BackgroundUiTask.execute(
                backgroundBlock = { loadUniqueAttributes() },
                uiBlock = {
                    toggleProgressVisibility(false)
                    loadTab(getString(R.string.dialog_att_chooser_attributes))
                },
                onCanceled = {
                    toggleProgressVisibility(false)
                    setupTabLayout()
                }
            )
        }

        return dialog
    }

    // Load unique attributes instead of regular attributes
    private fun loadUniqueAttributes() {
        try {
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireActivity())
            val activeFieldId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, -1)
            val activity = requireActivity() as BaseFieldActivity

            // Get unique attributes from database
            attributes = activity.getDatabase().getPossibleUniqueAttributes(activeFieldId)?.toTypedArray() ?: emptyArray()

            // Update the first tab's text
            tabLayout.post {
                tabLayout.getTabAt(0)?.text = getString(R.string.dialog_att_chooser_unique_attributes)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error loading unique attributes: ${e.message}")
            e.printStackTrace()
        }
    }
}
