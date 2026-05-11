package com.fieldbook.tracker.interfaces

import com.fieldbook.tracker.objects.FieldObject

interface FieldSyncController {
    fun startSync(field: FieldObject)
    fun onSyncComplete()
}