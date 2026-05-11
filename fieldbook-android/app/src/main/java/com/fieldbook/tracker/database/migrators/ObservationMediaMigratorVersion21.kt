package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase

class ObservationMediaMigratorVersion21 : FieldBookMigrator {

    companion object {
        const val TAG = "ObservationMediaMigratorVersion18"
        const val VERSION = 21
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {
        //add media URI columns to the observations table
        val alterStatements = listOf(
            "ALTER TABLE observations ADD COLUMN audio_uri TEXT",
            "ALTER TABLE observations ADD COLUMN video_uri TEXT",
            "ALTER TABLE observations ADD COLUMN photo_uri TEXT"
        )

        for (sql in alterStatements) {
            try {
                db.execSQL(sql)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}