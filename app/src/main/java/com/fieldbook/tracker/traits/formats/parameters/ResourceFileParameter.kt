package com.fieldbook.tracker.traits.formats.parameters

import android.app.Activity
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.FragmentActivity
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.FileExploreActivity
import com.fieldbook.tracker.activities.TraitEditorActivity
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.ValidationResult
import com.google.android.material.textfield.TextInputEditText
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.util.UUID
import javax.inject.Inject

class ResourceFileParameter @Inject constructor(
    uniqueId: String = UUID.randomUUID().toString(),
    override val nameStringResourceId: Int = R.string.trait_parameter_resource_file,
    override val defaultLayoutId: Int = R.layout.list_item_trait_parameter_file_name,
    override val parameter: Parameters = Parameters.RESOURCE_FILE
) : BaseFormatParameter(
    uniqueId,
    nameStringResourceId,
    defaultLayoutId,
    parameter
) {
    val attributeName = "resourceFile"
    val defaultValue = ""

    override fun createViewHolder(parent: ViewGroup): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(defaultLayoutId, parent, false)
        return ResourceFileViewHolder(view)
    }

    inner class ResourceFileViewHolder(itemView: View) : ViewHolder(itemView) {
        private val resourceFileEditText: TextInputEditText = 
            itemView.findViewById(R.id.list_item_trait_parameter_resource_file_et)
        
        init {
            // Set up click listener to open file explorer
            resourceFileEditText.setOnClickListener {
                val context = itemView.context
                if (context is FragmentActivity) {
                    val dir = BaseDocumentTreeUtil.Companion.getDirectory(context, R.string.dir_resources)
                    if (dir != null && dir.exists()) {
                        val intent = Intent(context, FileExploreActivity::class.java)
                        intent.putExtra("path", dir.uri.toString())
                        intent.putExtra("title", context.getString(R.string.main_toolbar_resources))
                        
                        // Use the same request code as defined in TraitEditorActivity
                        context.startActivityForResult(intent, TraitEditorActivity.REQUEST_RESOURCE_FILE_CODE)
                        
                        // Store reference to this view holder for result handling
                        currentViewHolder = this
                    }
                }
            }
        }

        override fun merge(traitObject: TraitObject): TraitObject {
            traitObject.resourceFile = resourceFileEditText.text.toString()
            return traitObject
        }

        override fun load(traitObject: TraitObject?): Boolean {
            initialTraitObject = traitObject
            resourceFileEditText.setText(traitObject?.resourceFile ?: "")
            return true
        }

        override fun validate(database: DataHelper, initialTraitObject: TraitObject?): ValidationResult {
            // Resource file is optional, so no validation needed
            return ValidationResult(true)
        }
        
        // Method to be called when file is selected
        fun setResourceFile(fileName: String) {
            resourceFileEditText.setText(fileName)
        }
    }

    companion object {
        // Use the same request code as defined in TraitEditorActivity
        private val REQUEST_FILE_EXPLORER_CODE = 6
        private var currentViewHolder: ResourceFileViewHolder? = null
        
        // Method to be called from activity's onActivityResult
        fun handleActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode == REQUEST_FILE_EXPLORER_CODE && resultCode == Activity.RESULT_OK && data != null) {
                val fileName = data.getStringExtra(FileExploreActivity.EXTRA_RESULT_KEY) ?: ""
                currentViewHolder?.setResourceFile(fileName)
            }
        }
    }
}