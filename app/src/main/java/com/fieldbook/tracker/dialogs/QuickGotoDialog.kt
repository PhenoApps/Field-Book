package com.fieldbook.tracker.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.fragment.app.DialogFragment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.interfaces.CollectRangeController
import com.fieldbook.tracker.preferences.GeneralKeys

/**
 * Dialog to quickly jump to a specific range of data in the CollectActivity.
 */
class QuickGotoDialog(
    private val controller: CollectRangeController,
    private val callback: (String, String) -> Unit): DialogFragment() {

    companion object {
        const val TAG = "QuickGotoDialog"
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.dialog_quick_goto, null)

        val prefs = controller.getPreferences()

        //get primary/secondary from preferences and set hints of the edit texts
        val primary = prefs.getString(GeneralKeys.PRIMARY_NAME, "")
        val secondary = prefs.getString(GeneralKeys.SECONDARY_NAME, "")

        val primaryEt = view.findViewById<EditText>(R.id.quick_goto_primary_et)
        val secondaryEt = view.findViewById<EditText>(R.id.quick_goto_secondary_et)

        primaryEt.hint = primary
        secondaryEt.hint = secondary

        //get current primary/secondary and set the edit texts
        controller.getRangeBox().let {
            primaryEt.setText(it.primaryIdTv.text.toString())
            secondaryEt.setText(it.secondaryIdTv.text.toString())
        }

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.dialog_quick_goto_title)
            .setView(view)
            .setPositiveButton(R.string.dialog_quick_goto_go) { _, _ ->
                val primaryId = primaryEt.text.toString()
                val secondaryId = secondaryEt.text.toString()
                callback(primaryId, secondaryId)
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> }
            .create()

        return dialog
    }
}
