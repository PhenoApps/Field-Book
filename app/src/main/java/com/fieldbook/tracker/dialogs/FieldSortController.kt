package com.fieldbook.tracker.dialogs

import com.fieldbook.tracker.objects.FieldObject

interface FieldSortController {
    fun showSortDialog(field: FieldObject?)
    fun submitSortList(field: FieldObject?, attributes: Array<String?>?)
}