package com.fieldbook.tracker.dialogs

import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.AttributeAdapter

/**
 * Dialog to choose a search attribute for a field.
 * The chosen attribute is what the barcode search matches entries against.
 */
class SearchAttributeChooserDialog : UniqueAttributeChooserDialog() {

    companion object {
        const val TAG = "SearchAttributeDialog"
    }

    interface OnSearchAttributeSelectedListener {
        fun onSearchAttributeSelected(label: String, applyToAll: Boolean)
    }

    override val titleResId = R.string.search_attribute_dialog_title

    private var onSearchAttributeSelectedListener: OnSearchAttributeSelectedListener? = null

    fun setOnSearchAttributeSelectedListener(listener: OnSearchAttributeSelectedListener) {
        onSearchAttributeSelectedListener = listener
    }

    override fun onAttributeClicked(model: AttributeAdapter.AttributeModel, position: Int) {
        val applyToAll = applyAllCheckbox.isChecked
        onSearchAttributeSelectedListener?.onSearchAttributeSelected(model.label, applyToAll)
        dismiss()
    }
}
