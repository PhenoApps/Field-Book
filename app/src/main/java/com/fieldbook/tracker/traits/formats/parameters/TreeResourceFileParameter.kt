package com.fieldbook.tracker.traits.formats.parameters

import android.app.Activity
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.core.net.toUri
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.FragmentActivity
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.dialogs.FileExploreDialogFragment
import com.fieldbook.tracker.dialogs.TreeConstructorDialogFragment
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult
import com.fieldbook.tracker.traits.formats.ui.ParameterScrollView
import com.google.android.material.textfield.TextInputEditText
import org.phenoapps.utils.BaseDocumentTreeUtil

/**
 * Resource file row for Tree Architecture:
 * - Tap field / folder icon → pick an existing schema JSON from resources
 * - [Edit tree schema] → fullscreen Constructor (loads the picked file when present)
 *
 * Constructor save and file-pick results use FragmentResult so they survive Activity recreate.
 */
class TreeResourceFileParameter : BaseFormatParameter(
    nameStringResourceId = R.string.trait_parameter_resource_file,
    defaultLayoutId = R.layout.list_item_trait_parameter_tree_schema,
    parameter = Parameters.RESOURCE_FILE,
) {

    companion object {
        /** Dedicated key so tree schema picks do not collide with other FileExplore users. */
        const val REQUEST_KEY_SCHEMA_FILE = "TreeResourceFileParameter.schema_file"
    }

    private var activity: Activity? = null
    private var parametersScrollView: ParameterScrollView? = null

    fun setActivity(activity: Activity) {
        this.activity = activity
    }

    fun setParametersScrollView(scrollView: ParameterScrollView) {
        parametersScrollView = scrollView
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(defaultLayoutId, parent, false)
        return TreeResourceFileViewHolder(view)
    }

    inner class TreeResourceFileViewHolder(itemView: View) : ViewHolder(itemView) {
        private val resourceFileEditText: TextInputEditText =
            itemView.findViewById(R.id.list_item_trait_parameter_resource_file_et)
        private val editSchemaButton: Button = itemView.findViewById(R.id.tree_edit_schema_button)

        init {
            registerFragmentResults()

            editSchemaButton.setOnClickListener {
                (activity as? FragmentActivity)?.let { act ->
                    val existing = resourceFileEditText.tag as? String
                        ?: resourceFileEditText.text?.toString()?.takeIf { it.isNotBlank() }
                    TreeConstructorDialogFragment.show(
                        activity = act,
                        traitName = draftTraitNameFromParameters(),
                        existingResourceRef = existing,
                    )
                }
            }
            resourceFileEditText.setOnClickListener { openSchemaFilePicker() }
            textInputLayout.setEndIconOnClickListener { openSchemaFilePicker() }
            resourceFileEditText.addTextChangedListener { editable ->
                if (editable.toString().isNotBlank()) {
                    textInputLayout.setEndIconDrawable(android.R.drawable.ic_notification_clear_all)
                    textInputLayout.setEndIconOnClickListener {
                        resourceFileEditText.text?.clear()
                        resourceFileEditText.tag = null
                        textInputLayout.setEndIconDrawable(R.drawable.ic_tb_folder)
                        textInputLayout.setEndIconOnClickListener { openSchemaFilePicker() }
                    }
                } else {
                    textInputLayout.setEndIconDrawable(R.drawable.ic_tb_folder)
                    textInputLayout.setEndIconOnClickListener { openSchemaFilePicker() }
                }
            }
            resourceFileEditText.isFocusable = false
            resourceFileEditText.isClickable = true
        }

        /** Tree-owned: read Name parameter ET under the scroll view (no ParameterScrollView helper). */
        private fun draftTraitNameFromParameters(): String =
            parametersScrollView
                ?.findViewById<TextInputEditText>(R.id.list_item_trait_parameter_name_et)
                ?.text
                ?.toString()
                ?.trim()
                .orEmpty()

        private fun registerFragmentResults() {
            val act = activity as? FragmentActivity ?: return
            act.supportFragmentManager.setFragmentResultListener(
                TreeConstructorDialogFragment.REQUEST_KEY_SCHEMA_SAVED,
                act,
            ) { _, bundle ->
                bundle.getString(TreeConstructorDialogFragment.RESULT_RESOURCE_REF)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { applyResourceRef(it) }
            }
            act.supportFragmentManager.setFragmentResultListener(
                REQUEST_KEY_SCHEMA_FILE,
                act,
            ) { _, bundle ->
                bundle.getString(FileExploreDialogFragment.RESULT_URI)
                    ?.takeIf { it.isNotBlank() }
                    ?.let { applyResourceRef(it) }
            }
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {
            resourceFile = resourceFileEditText.tag as? String ?: resourceFileEditText.text.toString()
        }

        override fun load(traitObject: TraitObject?): Boolean {
            val resourceFile = traitObject?.resourceFile
            resourceFileEditText.setText(resourceFile?.let(::displayName).orEmpty())
            if (!resourceFile.isNullOrEmpty()) resourceFileEditText.tag = resourceFile
            return true
        }

        override fun validate(traitRepo: TraitRepository, initialTraitObject: TraitObject?) =
            ValidationResult().apply {
                val hasSchema = !resourceFileEditText.tag?.toString().isNullOrBlank() ||
                    !resourceFileEditText.text.isNullOrBlank()
                result = hasSchema
                error = if (hasSchema) null else itemView.context.getString(R.string.tree_schema_required)
                if (!hasSchema) resourceFileEditText.error = error
            }

        private fun openSchemaFilePicker() {
            (activity as? FragmentActivity)?.let { fragmentActivity ->
                val dir = BaseDocumentTreeUtil.getDirectory(fragmentActivity, R.string.dir_resources)
                if (dir != null && dir.exists()) {
                    val dialog = FileExploreDialogFragment().apply {
                        arguments = Bundle().apply {
                            putString("path", dir.uri.toString())
                            putString("dialogTitle", fragmentActivity.getString(R.string.main_toolbar_resources))
                            // Schema files only — .trt trait bundles are not tree schemas.
                            putStringArray("include", arrayOf("json"))
                            putString(
                                FileExploreDialogFragment.ARG_RESULT_REQUEST_KEY,
                                REQUEST_KEY_SCHEMA_FILE,
                            )
                        }
                    }
                    dialog.show(fragmentActivity.supportFragmentManager, "TreeSchemaFileExplore")
                }
            }
        }

        private fun applyResourceRef(resourceRef: String) {
            // Store leaf name under resources/; content:// is not portable across devices.
            val leaf = com.fieldbook.tracker.utilities.TreeSchemaLoader.schemaLeaf(resourceRef)
                .ifBlank { resourceRef.substringAfterLast('/') }
            resourceFileEditText.setText(displayName(leaf))
            resourceFileEditText.tag = leaf
        }

        private fun displayName(resourceRef: String): String =
            if (resourceRef.contains("://")) {
                resourceRef.toUri().lastPathSegment?.substringAfterLast('/') ?: resourceRef
            } else {
                resourceRef.substringAfterLast('/')
            }
    }
}
