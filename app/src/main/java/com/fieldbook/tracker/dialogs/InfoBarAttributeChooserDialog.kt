package com.fieldbook.tracker.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.activities.PreferencesActivity
import com.fieldbook.tracker.preferences.GeneralKeys
import androidx.core.content.edit
import com.fieldbook.tracker.adapters.AttributeAdapter

/**
 * Dialog to choose an attribute for an InfoBar. Extends the AttributeChooserDialog
 * to add specific functionality for InfoBars, such as custom title and a customize button,
 * selected attribute highlighting, and attribute on-click handling that saves choice
 * to InfoBar preference
 */
class InfobarAttributeChooserDialog : AttributeChooserDialog() {

    companion object {
        private const val ARG_INFO_BAR_POSITION = "infoBarPosition"

        /** Factory method to create a new dialog with an infoBarPosition argument. */
        fun newInstance(infoBarPosition: Int): InfobarAttributeChooserDialog {
            val args = Bundle().apply {
                putInt(ARG_INFO_BAR_POSITION, infoBarPosition)
            }
            return InfobarAttributeChooserDialog().apply {
                arguments = args
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val infoBarPosition = arguments?.getInt(ARG_INFO_BAR_POSITION)
            ?: throw IllegalStateException("InfobarAttributeChooserDialog requires an infoBarPosition argument")

        // Create the base dialog then add a title and a customize button.
        val dialog = super.onCreateDialog(savedInstanceState) as AlertDialog
        dialog.setTitle(getString(R.string.dialog_infobar_att_chooser_title, infoBarPosition+1)) // adjust for zero indexing
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.dialog_att_chooser_customize)) { _, _ ->
            // When pressed opens the Number of InfoBars setting in PreferencesActivity
            startActivity(Intent(requireContext(), PreferencesActivity::class.java).apply {
                putExtra(GeneralKeys.INFOBAR_UPDATE, true)
                putExtra("infoBarPosition", infoBarPosition)
            })
        }

        return dialog
    }

    /** Retrieves InfoBar's currently selected attribute. */
    override fun getSelected(): String? {
        val infoBarPosition = arguments?.getInt(ARG_INFO_BAR_POSITION)
        return PreferenceManager.getDefaultSharedPreferences(requireActivity())
            .getString("DROP$infoBarPosition", null)
    }

    /** Saves newly selected attribute to preferences and refreshes the InfoBars. */
    override fun onAttributeClicked(model: AttributeAdapter.AttributeModel, position: Int) {
        val infoBarPosition = requireArguments().getInt(ARG_INFO_BAR_POSITION)
        super.onAttributeClicked(model, position)
        PreferenceManager.getDefaultSharedPreferences(requireActivity()).edit {
            putString(
                "DROP$infoBarPosition",
                model.label
            )
        }
        (activity as? CollectActivity)?.refreshInfoBarAdapter()
    }
}
