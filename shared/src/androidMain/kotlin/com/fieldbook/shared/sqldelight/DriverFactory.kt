package com.fieldbook.shared.sqldelight

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.fieldbook.shared.database.utils.DATABASE_NAME

actual class DriverFactory(private val context: Context) {
    actual fun createDriver(): SqlDriver {
        return AndroidSqliteDriver(FieldbookDatabase.Schema, context, DATABASE_NAME)
    }
}

