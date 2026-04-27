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

        return synchronized(this) {
            val rechecked = cached
            if (rechecked != null) {
                rechecked
            } else {
                AndroidSqliteDriver(FieldbookDatabase.Schema, context, DATABASE_NAME).also { driver ->
                    try {
                        driver.execute(null, "PRAGMA busy_timeout=5000;", 0, null)
                    } catch (_: Throwable) {
                    }
                    cached = driver
                }
            }
        }
    }

    actual fun close() {
        synchronized(this) {
            cached?.close()
            cached = null
        }
    }
}

