package com.fieldbook.tracker.dialogs

import com.fieldbook.tracker.utilities.ThemedAlertDialog
import android.content.Context
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R

class LoadingDialog(private val context: Context) {

    private var loadingDialog: AlertDialog? = null

    fun show(messageResId: Int) {
        val dialogView = ThemedAlertDialog.inflate(context, R.layout.dialog_loading)
        val messageTv = dialogView.findViewById<TextView>(R.id.loading_message)
        messageTv.text = context.getString(messageResId)

        loadingDialog = ThemedAlertDialog.builder(context)
            .setView(dialogView)
            .create()

        loadingDialog?.show()
    }

    fun dismiss() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }
}