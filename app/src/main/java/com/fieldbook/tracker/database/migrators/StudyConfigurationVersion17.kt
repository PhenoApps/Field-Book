package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase

class StudyConfigurationVersion17 : FieldBookMigrator {

    companion object {
        const val TAG = "StudyConfigurationVersion17"
        const val VERSION = 17
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {
        db.execSQL("ALTER TABLE studies ADD COLUMN start_corner TEXT")
        db.execSQL("ALTER TABLE studies ADD COLUMN walking_direction TEXT")
        db.execSQL("ALTER TABLE studies ADD COLUMN walking_pattern TEXT")
    }

}