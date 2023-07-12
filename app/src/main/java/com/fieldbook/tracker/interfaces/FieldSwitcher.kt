package com.fieldbook.tracker.interfaces

import com.fieldbook.tracker.objects.FieldObject

/**
 * Any object with access to a context and database can switch fields if this is implemented.
 * Default implementation available in utils FieldSwitchImpl
 */
interface FieldSwitcher {
    fun switchField(field: FieldObject?)
    fun switchField(studyId: Int)
}