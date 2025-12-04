package com.fieldbook.tracker.dialogs

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R

class InvalidValueDialog(private val context: Context) {

    /**
     * Shows a dialog asking the user if they want to save an invalid value.
     */
    fun show(
        onAccept: (() -> Unit)? = null,
        onReject: (() -> Unit)? = null
    ) {
        val message = context.getString(R.string.dialog_save_invalid_value_message)

        AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.dialog_invalid_value_title)
            .setMessage(message)
            .setPositiveButton(R.string.dialog_yes) { dialog, _ ->

                onAccept?.invoke()

                dialog.dismiss()

            }
            .setNegativeButton(R.string.dialog_no) { dialog, _ ->

                onReject?.invoke()

                dialog.dismiss()

            }
            .setCancelable(false)
            .show()
    }

    fun show(
        onAccept: Runnable?,
        onReject: Runnable?
    ) {
        show(
            onAccept = { onAccept?.run() },
            onReject = { onReject?.run() }
        )
    }
}