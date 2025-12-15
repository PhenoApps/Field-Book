package com.fieldbook.shared.sqldelight

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import com.fieldbook.shared.database.utils.DATABASE_VERSION

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
        // Query the current user_version (old DB version)
        val oldVersion = queryUserVersion(driver)

        // Call SQLDelight's Schema.migrate and supply AfterVersion callbacks for complex programmatic migrations
        FieldbookDatabase.Schema.migrate(
            driver,
            oldVersion.toLong(),
            DATABASE_VERSION,
//            AfterVersion(6) { d -> Migrator.migrateV6(d) },
//            AfterVersion(9) { d -> Migrator.migrateV9(d) },
//            AfterVersion(10) { d -> Migrator.fixGeoCoordinates(d) }
        )
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
private fun queryUserVersion(driver: SqlDriver): Int {
    return try {
        val result = driver.executeQuery(null, "PRAGMA user_version;", { cursor: SqlCursor ->
            // The mapper is invoked with a cursor positioned on the first row (if any).
            // Mirror generated code which directly reads columns without calling cursor.next().
            val v = cursor.getLong(0) ?: 0L
            QueryResult.Value(v)
        }, 0)

        when (result) {
            is QueryResult.Value<*> -> {
                val vAny = result.value
                (vAny as? Long)?.toInt() ?: 0
            }
            else -> 0
        }
    } catch (_: Throwable) {
        0
    }
}
