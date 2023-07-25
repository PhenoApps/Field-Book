package com.fieldbook.tracker.interfaces

import android.content.SharedPreferences
import com.fieldbook.tracker.database.DataHelper

interface FieldController {
    fun getDatabase(): DataHelper
    fun getPreferences(): SharedPreferences
    fun getFieldSwitcher(): FieldSwitcher
}