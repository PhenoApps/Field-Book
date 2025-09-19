package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase

class TraitAliasVersion18 : FieldBookMigrator {

    companion object {
        const val TAG = "TraitAliasSynonymVersion18"
        const val VERSION = 18
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {
        db.execSQL("ALTER TABLE observation_variables ADD COLUMN observation_variable_alias TEXT")
        db.execSQL("UPDATE observation_variables SET observation_variable_alias = observation_variable_name")
    }
}