package com.fieldbook.tracker.traits.formats.parameters

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ZplEditorActivity
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult
import com.fieldbook.tracker.zpl.TemplateRepository
import com.fieldbook.tracker.zpl.TemplateRepositoryEntryPoint
import com.google.android.material.textfield.TextInputEditText

class PrintTemplateParameter() : BaseFormatParameter(
    nameStringResourceId = R.string.label_config_template,
    defaultLayoutId = R.layout.list_item_trait_parameter_print_template,
    parameter = Parameters.PRINT_TEMPLATE
) {

    companion object {

        /**
         * Shows the saved templates as a single choice list, with an entry at the top
         * to create a new template in the editor.
         */
        fun showTemplatePicker(
            context: Context,
            templateRepository: TemplateRepository,
            currentTemplateId: String?,
            onTemplateSelected: (templateId: String) -> Unit,
            onCreateNew: () -> Unit
        ) {
            templateRepository.ensureDefaultTemplate()
            val templates = templateRepository.getAllTemplates().entries
                .sortedBy { it.value.lowercase() }

            val items = arrayOf(context.getString(R.string.zpl_template_create_new)) +
                    templates.map { it.value }.toTypedArray()

            val checkedIndex = templates.indexOfFirst { it.key == currentTemplateId }
                .let { if (it >= 0) it + 1 else -1 }

            AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(R.string.label_config_template)
                .setSingleChoiceItems(items, checkedIndex) { dialog, which ->
                    dialog.dismiss()
                    if (which == 0) onCreateNew()
                    else onTemplateSelected(templates[which - 1].key)
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }

        /**
         * Intent that opens the editor on a blank template.
         */
        fun newTemplateIntent(context: Context) = Intent(context, ZplEditorActivity::class.java)
    }

    private var activity: Activity? = null
    private var editorLauncher: ((Intent) -> Unit)? = null

    fun setActivity(activity: Activity) {
        this.activity = activity
    }

    fun setEditorLauncher(launcher: (Intent) -> Unit) {
        this.editorLauncher = launcher
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(defaultLayoutId, parent, false)
        return PrintTemplateViewHolder(view)
    }

    inner class PrintTemplateViewHolder(itemView: View) : ViewHolder(itemView) {
        private val templateEditText: TextInputEditText =
            itemView.findViewById(R.id.list_item_trait_parameter_print_template_et)

        private val templateRepository: TemplateRepository =
            TemplateRepositoryEntryPoint.get(itemView.context)

        init {
            templateEditText.setOnClickListener {
                showTemplatePicker(
                    context = itemView.context,
                    templateRepository = templateRepository,
                    currentTemplateId = templateEditText.tag as? String,
                    onTemplateSelected = ::updateTemplate,
                    onCreateNew = ::launchNewTemplateEditor
                )
            }

            templateEditText.isFocusable = false
            templateEditText.isClickable = true
        }

        private fun launchNewTemplateEditor() {
            val intent = newTemplateIntent(itemView.context)
            editorLauncher?.invoke(intent) ?: activity?.startActivity(intent)
        }

        fun updateTemplate(templateId: String) {
            templateEditText.setText(templateRepository.getTemplateName(templateId) ?: "")
            templateEditText.tag = templateId
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            printTemplateId = templateEditText.tag as? String ?: ""
        }

        override fun load(traitObject: TraitObject?): Boolean {
            val repository = templateRepository
            val templateId = repository.resolveTemplateId(traitObject?.printTemplateId)

            templateEditText.setText(templateId?.let { repository.getTemplateName(it) } ?: "")
            templateEditText.tag = templateId
            return true
        }

        override fun validate(
            traitRepo: TraitRepository,
            initialTraitObject: TraitObject?
        ): ValidationResult {
            if (templateEditText.text.isNullOrBlank()) {
                return ValidationResult(
                    result = false,
                    error = itemView.context.getString(R.string.label_fields_select)
                )
            }
            return ValidationResult(result = true)
        }
    }
}
