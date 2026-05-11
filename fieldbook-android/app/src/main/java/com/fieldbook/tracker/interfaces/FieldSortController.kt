package com.fieldbook.tracker.interfaces

import com.fieldbook.tracker.objects.FieldObject

interface FieldSortController {
    fun showSortDialog(field: FieldObject?)
    fun submitSortList(field: FieldObject?, attributes: Array<String?>?)
}