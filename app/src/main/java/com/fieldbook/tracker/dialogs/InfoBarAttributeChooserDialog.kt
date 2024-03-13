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


class InfobarAttributeChooserDialog : AttributeChooserDialog() {

    companion object {
        private const val ARG_INFO_BAR_POSITION = "infoBarPosition"

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

        val dialog = super.onCreateDialog(savedInstanceState) as AlertDialog
        dialog.setTitle(getString(R.string.dialog_infobar_att_chooser_title, infoBarPosition+1)) // adjust for zero indexing
        dialog.setButton(AlertDialog.BUTTON_NEUTRAL, getString(R.string.dialog_att_chooser_customize)) { _, _ ->
            startActivity(Intent(requireContext(), PreferencesActivity::class.java).apply {
                putExtra(GeneralKeys.INFOBAR_UPDATE, true)
                putExtra("infoBarPosition", infoBarPosition)
            })
        }

        loadTabWithSelection(infoBarPosition)
        return dialog
    }

    private fun loadTabWithSelection(infoBarPosition: Int) {
        val selected = PreferenceManager.getDefaultSharedPreferences(requireActivity()).getString("DROP$infoBarPosition", null)
        loadTab(selected ?: "")
    }

    override fun onAttributeClicked(label: String, position: Int) {
        val infoBarPosition = requireArguments().getInt(ARG_INFO_BAR_POSITION)
        super.onAttributeClicked(label, position)
        PreferenceManager.getDefaultSharedPreferences(requireActivity()).edit().putString("DROP$infoBarPosition", label).apply()
        (activity as? CollectActivity)?.refreshInfoBarAdapter()
    }
}
