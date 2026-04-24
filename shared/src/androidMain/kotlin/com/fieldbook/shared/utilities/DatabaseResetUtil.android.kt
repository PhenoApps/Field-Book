package com.fieldbook.shared.utilities

import androidx.preference.PreferenceManager
import com.fieldbook.shared.AndroidAppContextHolder
import com.fieldbook.shared.database.utils.DATABASE_NAME
import com.fieldbook.shared.sqldelight.closeDatabase

actual fun resetLocalDatabaseAndPreferences(): Boolean {
    val context = AndroidAppContextHolder.context

    closeDatabase()
    PreferenceManager.getDefaultSharedPreferences(context).edit().clear().apply()

    return context.deleteDatabase(DATABASE_NAME)
}
