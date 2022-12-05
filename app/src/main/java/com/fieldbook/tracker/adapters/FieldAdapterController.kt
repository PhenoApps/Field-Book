package com.fieldbook.tracker.adapters

interface FieldAdapterController: FieldController {
    fun queryAndLoadFields()
}