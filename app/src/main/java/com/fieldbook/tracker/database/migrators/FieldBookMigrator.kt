package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase

interface FieldBookMigrator {
    fun migrate(db: SQLiteDatabase): Result<Any>
}