package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase

class TraitAliasSynonymVersion18 : FieldBookMigrator {

    companion object {
        const val TAG = "TraitAliasSynonymVersion18"
        const val VERSION = 18
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {
        // add variable alias
        db.execSQL("ALTER TABLE observation_variables ADD COLUMN observation_variable_alias TEXT")
        db.execSQL("UPDATE observation_variables SET observation_variable_alias = observation_variable_name")

        // add variable synonyms
        db.execSQL("ALTER TABLE observation_variables ADD COLUMN variable_synonyms TEXT")
        db.execSQL("UPDATE observation_variables SET variable_synonyms = '[\"' || observation_variable_name || '\"]'")
    }
}