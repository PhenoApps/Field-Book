package com.fieldbook.tracker.utilities

import android.app.Dialog
import android.app.ProgressDialog
import android.content.Context
import android.graphics.Color
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import com.google.android.material.color.MaterialColors

/**
 * Themed [AlertDialog] helpers (Soda Dark and appearance-aware via [AppThemeResolver]).
 */
object ThemedAlertDialog {

    @JvmStatic
    fun dialogStyle(context: Context): Int = AppThemeResolver.alertDialogStyle(context)

    /** Context for custom views embedded in themed alert dialogs (use as [View] root context). */
    @JvmStatic
    fun contentContext(context: Context): Context =
        ContextThemeWrapper(context, dialogStyle(context))

    @JvmStatic
    fun builder(context: Context): AlertDialog.Builder =
        AlertDialog.Builder(context, dialogStyle(context))

    @JvmStatic
    fun dialog(context: Context): Dialog = Dialog(context, dialogStyle(context))

    @JvmStatic
    fun progressDialog(context: Context): ProgressDialog =
        ProgressDialog(context, dialogStyle(context))

    /** Primary text color from the themed alert dialog context. */
    @JvmStatic
    fun dialogTextColor(context: Context): Int {
        val themed = contentContext(context)
        return MaterialColors.getColor(themed, android.R.attr.textColorPrimary, Color.WHITE)
    }

    /** Apply alert-dialog primary text color to [TextView] descendants (e.g. resolution list). */
    @JvmStatic
    fun applyDialogTextColors(root: View) {
        val color = dialogTextColor(root.context)
        applyDialogTextColors(root, color)
    }

    private fun applyDialogTextColors(view: View, color: Int) {
        if (view is TextView) {
            view.setTextColor(color)
        }
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                applyDialogTextColors(view.getChildAt(i), color)
            }
        }
    }
}

/** Kotlin shorthand for [ThemedAlertDialog.builder]. */
fun Context.themedAlertDialogBuilder(): AlertDialog.Builder = ThemedAlertDialog.builder(this)
