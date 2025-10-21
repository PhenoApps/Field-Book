package com.fieldbook.tracker.dialogs

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.ImageButton
import androidx.fragment.app.DialogFragment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.interfaces.CollectRangeController
import com.fieldbook.tracker.preferences.GeneralKeys
import org.phenoapps.utils.SoftKeyboardUtil

/**
 * Dialog to quickly jump to a specific range of data in the CollectActivity.
 */
class QuickGotoDialog(
    private val controller: CollectRangeController,
    private val primaryClicked: Boolean = true,
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

        val swapButton = view.findViewById<ImageButton>(R.id.quick_goto_swap_btn)

        primaryEt.hint = primary
        secondaryEt.hint = secondary

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

        dialog.setOnShowListener {

            with(if (primaryClicked) primaryEt else secondaryEt) {
                this.requestFocus()
                SoftKeyboardUtil.Companion.showKeyboard(context, this)
            }
        }

        swapButton.setOnClickListener {

            toggleInputType(primaryEt)
            toggleInputType(secondaryEt)


            swapButton.setImageResource(if (secondaryEt.inputType == InputType.TYPE_CLASS_NUMBER) {
                R.drawable.ic_trait_text
            } else {
                R.drawable.ic_trait_numeric
            })
        }

        return dialog
    }

    private fun toggleInputType(editText: EditText) {

        editText.inputType = if (editText.inputType == InputType.TYPE_CLASS_NUMBER) {
            InputType.TYPE_CLASS_TEXT
        } else {
            InputType.TYPE_CLASS_NUMBER
        }
    }
}
