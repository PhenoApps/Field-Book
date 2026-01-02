package com.fieldbook.shared.sqldelight

import app.cash.sqldelight.db.SqlDriver
import com.fieldbook.shared.AppContext

/**
 * Expect/Actual DriverFactory:
 * - Maintain a single shared SqlDriver per platform.
 * - Provide a method to close it before replacing the DB file.
 *
 * Each platform must implement getDriver() and close().
 */
expect class DriverFactory {
    fun getDriver(): SqlDriver
    fun close()
}

fun createDatabase(): FieldbookDatabase {
    val driverFactory: DriverFactory = AppContext.driverFactory()
    val driver = driverFactory.getDriver()
    return FieldbookDatabase(driver)
}

fun closeDatabase() {
    val driverFactory: DriverFactory = AppContext.driverFactory()
    driverFactory.close()
}
