package com.fieldbook.tracker.dialogs

import android.content.Context
import android.view.LayoutInflater
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.fieldbook.tracker.R

class LoadingDialog(private val context: Context) {

    private var loadingDialog: AlertDialog? = null

    fun show(messageResId: Int) {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_loading, null)
        val messageTv = dialogView.findViewById<TextView>(R.id.loading_message)
        messageTv.text = context.getString(messageResId)

        loadingDialog = AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setView(dialogView)
            .create()

        loadingDialog?.show()
    }

    fun dismiss() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }
}