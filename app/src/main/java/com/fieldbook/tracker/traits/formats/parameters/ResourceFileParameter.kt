package com.fieldbook.tracker.traits.formats.parameters

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.FragmentActivity
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.dialogs.FileExploreDialogFragment
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult
import com.google.android.material.textfield.TextInputEditText
import org.phenoapps.utils.BaseDocumentTreeUtil
import androidx.core.net.toUri

/**
 * Display just the file name and store the file uri in the db
 * Attain this by saving the uri temporarily in the EditText's tag
 */
class ResourceFileParameter() : BaseFormatParameter(
    nameStringResourceId = R.string.trait_parameter_resource_file,
    defaultLayoutId = R.layout.list_item_trait_parameter_file_name,
    parameter = Parameters.RESOURCE_FILE
) {

    private var activity: Activity? = null

    fun setActivity(activity: Activity) {
        this.activity = activity
    }

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(defaultLayoutId, parent, false)
        return ResourceFileViewHolder(view)
    }

    inner class ResourceFileViewHolder(itemView: View) : ViewHolder(itemView) {
        private val resourceFileEditText: TextInputEditText =
            itemView.findViewById(R.id.list_item_trait_parameter_resource_file_et)

        init {
            resourceFileEditText.setOnClickListener {
                (activity as? FragmentActivity)?.let { fragmentActivity ->
                    val dir = BaseDocumentTreeUtil.Companion.getDirectory(fragmentActivity, R.string.dir_resources)
                    if (dir != null && dir.exists()) {
                        showComposeFileExplorerDialog(fragmentActivity, dir.uri.toString())
                    }
                }
            }

            resourceFileEditText.addTextChangedListener { editable ->

                if (editable.toString().isNotBlank()) {

                    textInputLayout.setEndIconDrawable(android.R.drawable.ic_notification_clear_all)

                    textInputLayout.setEndIconOnClickListener {

                        resourceFileEditText.text?.clear()

                        textInputLayout.endIconDrawable = null
                    }

                } else {

                    textInputLayout.endIconDrawable = null

                }
            }

            resourceFileEditText.isFocusable = false
            resourceFileEditText.isClickable = true
        }

        override fun merge(traitObject: TraitObject) = traitObject.apply {

            resourceFile = resourceFileEditText.tag as? String ?: resourceFileEditText.text.toString()

        }

        override fun load(traitObject: TraitObject?): Boolean {

            val resourceFile = traitObject?.resourceFile

            resourceFileEditText.setText(getFileName(resourceFile))

            if (!resourceFile.isNullOrEmpty()) {

                resourceFileEditText.tag = resourceFile

            }

            return true
        }

        override fun validate(
            database: DataHelper,
            initialTraitObject: TraitObject?,
        ) = ValidationResult()

        private fun showComposeFileExplorerDialog(activity: FragmentActivity, path: String) {

            val dialog = FileExploreDialogFragment().apply {

                arguments = Bundle().apply {
                    putString("path", path)
                    putString("dialogTitle", activity.getString(R.string.main_toolbar_resources))
                    putStringArray("include", arrayOf("jpg", "jpeg", "png", "bmp"))
                }

                setOnFileSelectedListener { selectedUri ->
                    handleFileSelected(selectedUri)
                }
            }

            dialog.show(activity.supportFragmentManager, "FileExploreDialog")
        }

        private fun handleFileSelected(uri: Uri) {

            resourceFileEditText.setText(getFileName(uri))

            resourceFileEditText.tag = uri.toString() // save the uri in the editText's tag

        }

        private fun getFileName(uri: Uri) = uri.lastPathSegment?.substringAfterLast('/') ?: uri.toString()

        private fun getFileName(uriString: String?): String {
            if (uriString.isNullOrEmpty()) return ""

            return try {
                getFileName(uriString.toUri())
            } catch (_: Exception) {
                uriString
            }
        }
    }
}