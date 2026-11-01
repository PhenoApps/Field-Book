package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.TextView
import androidx.core.content.edit
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.AttributeAdapter
import com.fieldbook.tracker.dialogs.AttributeChooserDialog
import com.fieldbook.tracker.dialogs.CodeDisplayAttributeChooserDialog
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.views.CodeDisplayView
import com.google.android.material.floatingactionbutton.FloatingActionButton

/**
 * Display-only trait that draws a barcode or QR code for the current entry, as large as the
 * trait area allows. No observation is recorded. The code format and the attribute or trait value
 * to encode are chosen from the settings button and saved to preferences, shared by all
 * code display traits.
 */
class CodeDisplayTraitLayout : BaseTraitLayout {

    companion object {
        const val TYPE = "code_display"
    }

    private var root: View? = null
    private var container: View? = null
    private var codeView: CodeDisplayView? = null
    private var valueTv: TextView? = null

    //the trait container is a scroll view, so the code is sized to it explicitly and follows it
    //as the input view and toolbars change around it
    private val containerLayoutListener =
        View.OnLayoutChangeListener { v, _, top, _, bottom, _, oldTop, _, oldBottom ->
            if (bottom - top != oldBottom - oldTop) v.post { fitToContainer() }
        }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    override fun setNaTraitsText() {}

    override fun type(): String = TYPE

    override fun layoutId(): Int = R.layout.trait_code_display

    override fun init(act: Activity) {
        root = act.findViewById(R.id.trait_code_display_root)
        codeView = act.findViewById(R.id.trait_code_display_code)
        valueTv = act.findViewById(R.id.trait_code_display_value)

        act.findViewById<FloatingActionButton>(R.id.trait_code_display_settings_btn)
            ?.setOnClickListener { showSettings() }

        container?.removeOnLayoutChangeListener(containerLayoutListener)
        container = act.findViewById<View>(R.id.svTraitContainer)?.also {
            it.addOnLayoutChangeListener(containerLayoutListener)
        }
    }

    override fun loadLayout() {
        super.loadLayout()
        fitToContainer()
        showCode()
    }

    override fun onExit() {
        container?.removeOnLayoutChangeListener(containerLayoutListener)
        container = null
    }

    private fun fitToContainer() {
        val holder = container ?: return
        val view = root ?: return
        val available = holder.height - holder.paddingTop - holder.paddingBottom
        if (available <= 0) return
        val params = view.layoutParams ?: return
        if (params.height != available) {
            params.height = available
            view.layoutParams = params
        }
    }

    private fun selectedFormat() =
        CodeDisplayView.formatOf(prefs.getString(GeneralKeys.CODE_DISPLAY_FORMAT, null))

    private fun showCode() {
        val format = selectedFormat()
        val value = queryValue()

        if (value.isEmpty()) {
            codeView?.clear()
            valueTv?.text = context.getString(R.string.trait_code_display_empty)
            return
        }

        valueTv?.text = if (codeView?.setCode(value, format) == true) value
        else context.getString(R.string.trait_code_display_invalid, CodeDisplayView.FORMATS[format], value)
    }

    //resolves the chosen attribute the same way infobars do, empty if it isn't available for this entry
    private fun queryValue(): String {
        val act = collectActivity
        val attribute = CodeDisplayAttributeChooserDialog.getSelectedAttribute(act) ?: return ""

        if (attribute.trait == null) {
            val available = getDatabase().getAllObservationUnitAttributeNames(prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0)) +
                    context.getString(R.string.field_name_attribute)
            if (attribute.label !in available) return ""
        }

        val value = act.queryForLabelValue(act.observationUnit, attribute)
        return if (value == context.getString(R.string.main_infobar_data_missing)) "" else value
    }

    private fun showSettings() {
        val format = CodeDisplayView.FORMATS[selectedFormat()]
        val data = CodeDisplayAttributeChooserDialog.getSelectedAttribute(collectActivity)?.label.orEmpty()

        val items = arrayOf(
            context.getString(R.string.trait_code_display_format_item, format),
            context.getString(R.string.trait_code_display_data_item, data)
        )

        AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.trait_code_display_settings_title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> showFormatChooser()
                    1 -> showDataChooser()
                }
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
            .show()
    }

    private fun showFormatChooser() {
        val formats = CodeDisplayView.FORMATS.keys.toList()

        AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.trait_code_display_format_title)
            .setSingleChoiceItems(
                CodeDisplayView.FORMATS.values.toTypedArray(),
                formats.indexOf(selectedFormat())
            ) { dialog, which ->
                prefs.edit { putString(GeneralKeys.CODE_DISPLAY_FORMAT, formats[which].name) }
                showCode()
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel, null)
            .create()
            .show()
    }

    private fun showDataChooser() {
        CodeDisplayAttributeChooserDialog().apply {
            setOnAttributeSelectedListener(object : AttributeChooserDialog.OnAttributeSelectedListener {
                override fun onAttributeSelected(model: AttributeAdapter.AttributeModel) {
                    showCode()
                }
            })
        }.show(collectActivity.supportFragmentManager, "codeDisplayAttributeChooserDialog")
    }
}
