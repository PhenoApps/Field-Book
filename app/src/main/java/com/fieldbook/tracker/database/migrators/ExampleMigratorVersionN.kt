package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase

/**
 * Example migrator
 */
class ExampleMigratorVersionN: FieldBookMigrator {
    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {

        // Example migration: add a new column to the table or something!

    }
}