package com.fieldbook.shared.sqldelight

import app.cash.sqldelight.db.SqlDriver

expect class DriverFactory {
    fun createDriver(): SqlDriver
}

fun createDatabase(driverFactory: DriverFactory): FieldbookDatabase {
    val driver = driverFactory.createDriver()
    val database = FieldbookDatabase(driver)
    return database
}

fun closeDatabase(driverFactory: DriverFactory) {
    driverFactory.createDriver().close()
}
