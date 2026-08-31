package com.fieldbook.tracker.dialogs

import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.AttributeAdapter

/**
 * Dialog to choose the attribute used to name media captured for a field's entries.
 * By default media is named with the unique id.
 */
class ImageNamingAttributeChooserDialog : UniqueAttributeChooserDialog() {

    companion object {
        const val TAG = "ImageNamingAttributeDialog"
    }

    interface OnImageNamingAttributeSelectedListener {
        fun onImageNamingAttributeSelected(label: String, applyToAll: Boolean)
    }

    override val titleResId = R.string.image_naming_attribute_dialog_title

    private var onImageNamingAttributeSelectedListener: OnImageNamingAttributeSelectedListener? = null

    fun setOnImageNamingAttributeSelectedListener(listener: OnImageNamingAttributeSelectedListener) {
        onImageNamingAttributeSelectedListener = listener
    }

    override fun onAttributeClicked(model: AttributeAdapter.AttributeModel, position: Int) {
        val applyToAll = applyAllCheckbox.isChecked
        onImageNamingAttributeSelectedListener?.onImageNamingAttributeSelected(model.label, applyToAll)
        dismiss()
    }
}
