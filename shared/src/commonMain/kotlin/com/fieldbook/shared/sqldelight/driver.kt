package com.fieldbook.shared.sqldelight

import app.cash.sqldelight.db.SqlDriver

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

fun createDatabase(driverFactory: DriverFactory): FieldbookDatabase {
    val driver = driverFactory.getDriver()
    return FieldbookDatabase(driver)
}

fun closeDatabase(driverFactory: DriverFactory) {
    driverFactory.close()
}



