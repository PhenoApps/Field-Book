package com.fieldbook.shared.sqldelight

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.fieldbook.shared.database.utils.DATABASE_NAME

actual class DriverFactory {
    actual fun createDriver(): SqlDriver {
        return NativeSqliteDriver(FieldbookDatabase.Schema, DATABASE_NAME)
    }
}

