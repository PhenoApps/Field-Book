package com.fieldbook.tracker.dialogs

import android.app.AlertDialog
import android.content.Context
import android.content.DialogInterface
import com.fieldbook.tracker.R

class SimpleListDialog {

    companion object {

        private const val TAG = "SimpleListDialog"

        /**
         * Show a simple list dialog with a title and a list of items
         * @param context The context to show the dialog in
         * @param titleResId The title of the dialog
         * @param items The list of items to show
         * @param onItemSelected The callback to call when an item is selected
         */
        fun <T> show(
            context: Context,
            titleResId: Int,
            items: List<T>,
            itemNames: List<String>,
            onItemSelected: (T) -> Unit
        ) {

            assert(items.size == itemNames.size)

            val builder = AlertDialog.Builder(context, R.style.AppAlertDialog)
            builder.setTitle(titleResId)
            builder.setSingleChoiceItems(
                itemNames.toTypedArray(),
                -1
            ) { dialog: DialogInterface, which: Int ->
                onItemSelected(items[which])
                dialog.dismiss()
            }
            builder.show()
        }
    }
}