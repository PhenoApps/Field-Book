package com.fieldbook.tracker.dialogs

import android.app.Activity
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.SortAdapter
import com.fieldbook.tracker.interfaces.FieldSortController
import com.fieldbook.tracker.objects.FieldObject

/**
Opens from the FieldEditorActivity FieldAdapter "sort" option.
Allows user to make and order a list of field attributes to sort by.
 */
class FieldSortDialogFragment : SortDialogFragment(), SortAdapter.Sorter {
    fun newInstance(
        activity: Activity,
        field: FieldObject,
        initialItems: Array<String>,
        selectableItems: Array<String>
    ): FieldSortDialogFragment {
        return FieldSortDialogFragment().apply {
            this.act = activity
            this.field = field
            this.initialItems = initialItems
            this.selectableItems = selectableItems
            this.dialogTitle = activity.getString(R.string.dialog_field_sort_title)
        }
    }

    override fun setupUi() {
        super.setupUi()

        //submits the new sort list to the field sort controller (where db queries should be handled)
        okButton?.setOnClickListener {

            (act as? FieldSortController)?.submitSortList(field, sortList.toTypedArray())

            dismiss()
        }
    }
}