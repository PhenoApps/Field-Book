package com.fieldbook.shared.sqldelight

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.fieldbook.shared.database.utils.DATABASE_NAME

actual class DriverFactory {
    private var cached: SqlDriver? = null

    actual fun getDriver(): SqlDriver {
        val existing = cached
        if (existing != null) return existing

        val created = NativeSqliteDriver(
            schema = FieldbookDatabase.Schema,
            name = DATABASE_NAME
        ).also { d ->
            try { d.execute(null, "PRAGMA busy_timeout=5000;", 0, null) } catch (_: Throwable) {}
        }

        cached = created
        return created
    }

    actual fun close() {
        cached?.close()
        cached = null
    }
}

