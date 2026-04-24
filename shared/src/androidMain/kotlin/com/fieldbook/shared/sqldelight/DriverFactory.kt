package com.fieldbook.shared.sqldelight

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.fieldbook.shared.database.utils.DATABASE_NAME

actual class DriverFactory(private val context: Context) {
    private var cached: SqlDriver? = null

    actual fun getDriver(): SqlDriver {
        val existing = cached
        if (existing != null) return existing

        val created = AndroidSqliteDriver(FieldbookDatabase.Schema, context, DATABASE_NAME)
        cached = created
        return created
    }

    actual fun close() {
        cached?.close()
        cached = null
    }
}
