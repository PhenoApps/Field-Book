package com.fieldbook.tracker.interfaces

import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.utilities.sort.FieldSortSpec

interface FieldSortController {
    fun showSortDialog(field: FieldObject?)
    fun submitSort(studyId: Int, sortSpec: FieldSortSpec)
}
