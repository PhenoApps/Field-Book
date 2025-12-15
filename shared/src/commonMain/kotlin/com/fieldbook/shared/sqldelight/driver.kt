package com.fieldbook.shared.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import com.fieldbook.shared.database.utils.DATABASE_VERSION
import com.fieldbook.shared.database.utils.DataHelper


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

    try {
        val oldVersion = queryUserVersion(driver)

        if (oldVersion == 0) {
            DataHelper.onCreate(driver)
        } else if (oldVersion < DATABASE_VERSION) {
            DataHelper.onUpgrade(driver, oldVersion, DATABASE_VERSION.toInt())
        }

    } catch (_: Throwable) {
        // Best-effort: don't crash DB creation if migration helper fails — let SQLDelight continue
    }

    return FieldbookDatabase(driver)
}

fun closeDatabase(driverFactory: DriverFactory) {
    driverFactory.close()
}

/**
 * Read PRAGMA user_version using the SqlDriver to determine the existing DB version.
 * This uses the driver.executeQuery API pattern: pass a mapper that returns a QueryResult.Value.
 */
private fun queryUserVersion(driver: SqlDriver): Int =
    try {
        val result = driver.executeQuery(
            identifier = null,
            sql = "PRAGMA user_version;",
            mapper = { cursor: SqlCursor ->
                val hasRow = (cursor.next() as? QueryResult.Value)?.value ?: false
                val v = if (hasRow) cursor.getLong(0) ?: 0L else 0L
                QueryResult.Value(v)
            },
            parameters = 0
        )

        ((result as? QueryResult.Value<*>)?.value as? Long)?.toInt() ?: 0
    } catch (_: Throwable) {
        0
    }


