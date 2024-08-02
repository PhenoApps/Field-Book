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
open class FieldSortDialog(private val act: Activity,
                           private val field: FieldObject,
                           initialItems: Array<String>,
                           selectableItems: Array<String>) :
    SortDialog(act, initialItems, selectableItems), SortAdapter.Sorter {

    /**
     * Initialize views and all business logic
     */
    override fun setupUi() {
        super.setupUi()

        setTitle(R.string.field_sort_entries)

        //submits the new sort list to the field sort controller (where db queries should be handled)
        okButton?.setOnClickListener {

            (act as? FieldSortController)?.submitSortList(field, sortList.toTypedArray())

            dismiss()
        }
    }
}