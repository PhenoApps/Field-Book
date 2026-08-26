package com.fieldbook.tracker.traits.formats.parameters

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.ZplEditorActivity
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult
import com.fieldbook.tracker.zpl.TemplateRepository
import com.google.android.material.textfield.TextInputEditText

class PrintTemplateParameter() : BaseFormatParameter(
    nameStringResourceId = R.string.label_config_template,
    defaultLayoutId = R.layout.list_item_trait_parameter_print_template,
    parameter = Parameters.PRINT_TEMPLATE
) {

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

        init {
            templateEditText.setOnClickListener {
                showTemplateSelectionDialog()
            }
            
            templateEditText.isFocusable = false
            templateEditText.isClickable = true
        }

        private fun showTemplateSelectionDialog() {
            val context = itemView.context
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val templateRepository = TemplateRepository(prefs)
            templateRepository.ensureBuiltInTemplatesExist()
            
            val templates = templateRepository.getAllTemplates()
            val templateNames = templates.values.toList().sorted()
            
            val options = mutableListOf<String>()
            options.add(context.getString(R.string.zpl_template_create_new))
            options.addAll(templateNames)

            val currentTemplateId = templateEditText.tag as? String ?: ""
            val currentTemplateName = if (currentTemplateId.isNotEmpty()) {
                templateRepository.getTemplateName(currentTemplateId) ?: ""
            } else ""

            val selectedIndex = if (currentTemplateName.isNotEmpty()) {
                options.indexOf(currentTemplateName).coerceAtLeast(-1)
            } else -1

            AlertDialog.Builder(context)
                .setTitle(R.string.label_config_template)
                .setSingleChoiceItems(options.toTypedArray(), selectedIndex) { dialog, which ->
                    if (which == 0) {
                        launchZplEditor(null)
                        dialog.dismiss()
                    }
                }
                .setPositiveButton(R.string.zpl_template_select) { dialog, _ ->
                    val listView = (dialog as AlertDialog).listView
                    val which = listView.checkedItemPosition
                    if (which > 0) {
                        val name = options[which]
                        val id = templates.entries.find { it.value == name }?.key
                        templateEditText.setText(name)
                        templateEditText.tag = id
                    }
                }
                .setNeutralButton(R.string.edit) { dialog, _ ->
                    val listView = (dialog as AlertDialog).listView
                    val which = listView.checkedItemPosition
                    if (which > 0) {
                        val name = options[which]
                        val id = templates.entries.find { it.value == name }?.key
                        launchZplEditor(id)
                    }
                }
                .setNegativeButton(R.string.dialog_cancel, null)
                .show()
        }

        private fun launchZplEditor(templateId: String?) {
            val context = itemView.context
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val templateRepository = TemplateRepository(prefs)
            val initialZpl = if (templateId != null) {
                templateRepository.getTemplate(templateId) ?: ""
            } else ""
            
            val intent = Intent(context, ZplEditorActivity::class.java).apply {
                putExtra(ZplEditorActivity.EXTRA_INITIAL_ZPL, initialZpl)
                putExtra(ZplEditorActivity.EXTRA_TEMPLATE_NAME, if (templateId != null) templateRepository.getTemplateName(templateId) else null)
            }
            
            editorLauncher?.invoke(intent) ?: activity?.startActivity(intent)
        }

        fun updateTemplate(name: String) {
            val context = itemView.context
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val templateRepository = TemplateRepository(prefs)
            val templates = templateRepository.getAllTemplates()
            val id = templates.entries.find { it.value == name }?.key
            
            templateEditText.setText(name)
            templateEditText.tag = id
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            printTemplateId = templateEditText.tag as? String ?: ""
        }

        override fun load(traitObject: TraitObject?): Boolean {
            val templateId = traitObject?.printTemplateId
            if (!templateId.isNullOrEmpty()) {
                val context = itemView.context
                val prefs = PreferenceManager.getDefaultSharedPreferences(context)
                val templateRepository = TemplateRepository(prefs)
                val name = templateRepository.getTemplateName(templateId)
                templateEditText.setText(name ?: "")
                templateEditText.tag = templateId
            } else {
                templateEditText.setText("")
                templateEditText.tag = null
            }
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
