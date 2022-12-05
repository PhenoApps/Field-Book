package com.fieldbook.tracker.adapters

import com.fieldbook.tracker.database.DataHelper

interface FieldController {
    fun getDatabase(): DataHelper
}