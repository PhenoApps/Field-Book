// package com.fieldbook.tracker.dialogs

// import android.app.AlertDialog
// import android.app.Dialog
// import android.os.Bundle
// import android.view.View
// import android.widget.CheckBox
// import com.fieldbook.tracker.R

// /**
//  * Dialog to choose a search attribute for a field.
//  * Extends the AttributeChooserDialog to add a checkbox for applying the selection to all fields.
//  */
// class SearchAttributeChooserDialog : AttributeChooserDialog(
//     showTraits = false,
//     showOther = false,
//     showSystemAttributes = false
// ) {

//     companion object {
//         const val TAG = "SearchAttributeChooserDialog"
//     }

//     interface OnSearchAttributeSelectedListener {
//         fun onSearchAttributeSelected(label: String, applyToAll: Boolean)
//     }

//     private var onSearchAttributeSelectedListener: OnSearchAttributeSelectedListener? = null
//     private var applyAllCheckbox: CheckBox? = null

//     fun setOnSearchAttributeSelectedListener(listener: OnSearchAttributeSelectedListener) {
//         onSearchAttributeSelectedListener = listener
//     }

//     override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
//         // Create the base dialog
//         val dialog = super.onCreateDialog(savedInstanceState) as AlertDialog
        
//         // Set the title
//         dialog.setTitle(R.string.search_attribute_dialog_title)
        
//         // Find and show the checkbox
//         dialog.findViewById<CheckBox>(R.id.dialog_collect_att_chooser_checkbox)?.let {
//             applyAllCheckbox = it
//             it.visibility = View.VISIBLE
//         }
        
//         return dialog
//     }

//     override fun onAttributeClicked(label: String, position: Int) {
//         val applyToAll = applyAllCheckbox?.isChecked ?: false
//         onSearchAttributeSelectedListener?.onSearchAttributeSelected(label, applyToAll)
//         dismiss()
//     }
// }

package com.fieldbook.tracker.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.CheckBox
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.FieldEditorActivity
import com.fieldbook.tracker.adapters.AttributeAdapter
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.BackgroundUiTask

/**
 * Dialog to choose a search attribute for a field.
 * Extends the AttributeChooserDialog to add a checkbox for applying the selection to all fields.
 */
class SearchAttributeChooserDialog : AttributeChooserDialog(
    showTraits = false,
    showOther = false,
    showSystemAttributes = false
) {

    companion object {
        const val TAG = "SearchAttributeDialog"
    }

    interface OnSearchAttributeSelectedListener {
        fun onSearchAttributeSelected(label: String, applyToAll: Boolean)
    }

    private var onSearchAttributeSelectedListener: OnSearchAttributeSelectedListener? = null

    fun setOnSearchAttributeSelectedListener(listener: OnSearchAttributeSelectedListener) {
        onSearchAttributeSelectedListener = listener
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Create the base dialog
        val dialog = super.onCreateDialog(savedInstanceState) as AlertDialog
        
        // Set the title and show checkbox
        dialog.setTitle(R.string.search_attribute_dialog_title)
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
            val activity = requireActivity() as FieldEditorActivity
            
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

    override fun onAttributeClicked(model: AttributeAdapter.AttributeModel, position: Int) {
        val applyToAll = applyAllCheckbox.isChecked
        onSearchAttributeSelectedListener?.onSearchAttributeSelected(model.label, applyToAll)
        dismiss()
    }
}
