package com.fieldbook.tracker.dialogs

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity

class InvalidValueDialog(private val context: Context) {

    /**
     * Shows a dialog asking the user if they want to save an invalid value.
     */
    fun show(
        errorMessage: String,
        studyId: String,
        plotId: String,
        traitId: String,
        rep: String,
        onReject: ((String, String, String, String) -> Unit)? = null
    ) {
        val message = errorMessage + "\n\n" + context.getString(R.string.dialog_save_invalid_value_message)

        AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.dialog_invalid_value_title)
            .setMessage(message)
            .setPositiveButton(R.string.dialog_yes, null)
            .setNegativeButton(R.string.dialog_no) { dialog, _ ->

                if (onReject != null) {

                    onReject.invoke(studyId, plotId, traitId, rep)

                } else {

                    deleteObservation(studyId, plotId, traitId, rep)

                }

                dialog.dismiss()
            }
            .setCancelable(false)
            .show()
    }

    private fun deleteObservation(studyId: String, plotId: String, traitId: String, rep: String) {
        if (context is CollectActivity) {
            context.getDatabase().deleteTrait(studyId, plotId, traitId, rep)
        }
    }

    companion object {
        @JvmStatic
        fun show(
            context: Context, errorMessage: String, studyId: String,
            plotId: String, traitId: String, rep: String
        ) {
            InvalidValueDialog(context).show(errorMessage, studyId, plotId, traitId, rep, null)
        }

        @JvmStatic
        fun show(
            context: Context, errorMessage: String, studyId: String,
            plotId: String, traitId: String, rep: String,
            onReject: OnRejectListener?
        ) {
            InvalidValueDialog(context).show(
                errorMessage,
                studyId,
                plotId,
                traitId,
                rep
            ) { s, p, t, r ->
                onReject?.onReject(s, p, t, r)
            }
        }
    }

    /**
     * Override the default delete trait action when user wants to reject the invalid obs
     */
    interface OnRejectListener {
        fun onReject(studyId: String, plotId: String, traitId: String, rep: String)
    }
}