package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase

/**
 * Adds study_db_levels to studies, a json array of the observation levels a BrAPI study's units had
 * when one of its levels was imported. Used to keep partially imported studies available for import.
 */
class StudyDbLevelsMigratorVersion22 : FieldBookMigrator {

    companion object {
        const val TAG = "StudyDbLevelsMigratorVersion22"
        const val VERSION = 22
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {
        try {
            db.execSQL("ALTER TABLE studies ADD COLUMN study_db_levels TEXT")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
