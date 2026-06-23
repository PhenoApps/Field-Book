package com.fieldbook.tracker.utilities

import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.google.android.material.color.MaterialColors
import android.content.res.ColorStateList
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Themed [AlertDialog] helpers (Soda Dark and appearance-aware via [AppThemeResolver]).
 */
object ThemedAlertDialog {

    enum class IconTint {
        /** [androidx.appcompat.R.attr.colorControlNormal] from the dialog theme. */
        ControlNormal,
        /** [R.attr.fb_icon_tint] from the caller context (activity or dialog). */
        Icon,
    }

    @JvmStatic
    fun dialogStyle(context: Context): Int = AppThemeResolver.alertDialogStyle(context)

    /** Context for custom views embedded in themed alert dialogs (use as [View] root context). */
    @JvmStatic
    fun contentContext(context: Context): Context =
        ContextThemeWrapper(context, dialogStyle(context))

    /** Inflate a dialog custom view with [contentContext] so theme attributes resolve against the dialog theme. */
    @JvmStatic
    @JvmOverloads
    fun inflate(
        context: Context,
        @LayoutRes layoutResId: Int,
        root: ViewGroup? = null,
        attachToRoot: Boolean = false,
    ): View = LayoutInflater.from(contentContext(context)).inflate(layoutResId, root, attachToRoot)

    @JvmStatic
    fun builder(context: Context): AlertDialog.Builder {
        val style = dialogStyle(context)
        val source = context
        return object : MaterialAlertDialogBuilder(source, style) {
            override fun create(): AlertDialog =
                super.create().also { wireSodaChrome(it, source) }

            override fun show(): AlertDialog {
                val dialog = create()
                dialog.show()
                applySodaChrome(dialog, source)
                return dialog
            }
        }
    }

    @JvmStatic
    fun dialog(context: Context): Dialog = Dialog(context, dialogStyle(context))

    /**
     * Ensures Soda Dark panel color after the dialog is laid out. Safe to call from [create];
     * does not fight [DialogInterface.setOnShowListener] chains.
     */
    @JvmStatic
    fun wireSodaChrome(dialog: AlertDialog, context: Context) {
        chainOnShow(dialog, context, null)
    }

    /** Runs [applySodaChrome] then an optional listener (use when replacing [Dialog.setOnShowListener]). */
    @JvmStatic
    @JvmOverloads
    fun chainOnShow(
        dialog: AlertDialog,
        context: Context,
        listener: DialogInterface.OnShowListener? = null,
    ) {
        dialog.setOnShowListener {
            applySodaChrome(dialog, context)
            listener?.onShow(it)
        }
    }

  /** Window + parent panel only (do not walk/mutate all Material children). */
    @JvmStatic
    fun applySodaChrome(dialog: Dialog, context: Context) {
        if (!AppThemeResolver.isSodaDark(PreferenceManager.getDefaultSharedPreferences(context))) {
            return
        }
        val surface = ContextCompat.getColor(context, R.color.soda_dark_row)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        if (dialog is AlertDialog) {
            dialog.findViewById<View>(androidx.appcompat.R.id.parentPanel)
                ?.setBackgroundColor(surface)
        }
    }

    /** Themed indeterminate progress dialog (replaces deprecated [android.app.ProgressDialog]). */
    @JvmStatic
    fun indeterminateProgressDialog(context: Context, message: CharSequence): AlertDialog {
        val view = inflate(context, R.layout.dialog_loading)
        view.findViewById<TextView>(R.id.loading_message).text = message
        return builder(context)
            .setView(view)
            .setCancelable(false)
            .create()
    }

    @JvmStatic
    fun resolveTintColor(context: Context, tint: IconTint): Int {
        val themed = if (tint == IconTint.ControlNormal) contentContext(context) else context
        val attr = if (tint == IconTint.ControlNormal) {
            androidx.appcompat.R.attr.colorControlNormal
        } else {
            R.attr.fb_icon_tint
        }
        return MaterialColors.getColor(themed, attr, 0)
    }

    @JvmStatic
    @JvmOverloads
    fun tintImageView(imageView: ImageView, context: Context, tint: IconTint = IconTint.ControlNormal) {
        val color = resolveTintColor(context, tint)
        if (color != 0) {
            ImageViewCompat.setImageTintList(imageView, ColorStateList.valueOf(color))
        }
    }

    /** [ImageView.setImageResource] then re-apply theme tint (required after programmatic icon swaps). */
    @JvmStatic
    @JvmOverloads
    fun setThemedImageResource(
        imageView: ImageView,
        context: Context,
        drawableRes: Int,
        tint: IconTint = IconTint.ControlNormal,
    ) {
        imageView.setImageResource(drawableRes)
        tintImageView(imageView, context, tint)
    }

    /** Walk [root] and tint all [ImageView] descendants for dialog toolbars / icon rows. */
    @JvmStatic
    @JvmOverloads
    fun tintDialogIcons(root: View, context: Context, tint: IconTint = IconTint.ControlNormal) {
        when (root) {
            is ImageView -> tintImageView(root, context, tint)
            is ViewGroup -> {
                for (i in 0 until root.childCount) {
                    tintDialogIcons(root.getChildAt(i), context, tint)
                }
            }
        }
    }

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
