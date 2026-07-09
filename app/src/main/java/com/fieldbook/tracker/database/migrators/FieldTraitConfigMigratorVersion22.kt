package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.fieldbook.tracker.database.FieldTraitConfigTable
import com.fieldbook.tracker.database.Migrator

/**
 * New field trait config table for organizing trait lists per field.
 */
class FieldTraitConfigMigratorVersion22 : FieldBookMigrator {

    companion object {
        private const val TAG = "FieldTraitConfigMigratorVersion22"
        const val VERSION = 22
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {

        Log.d(TAG, "Starting migration to version 22 - Adding field_trait_config table")

        createFieldTraitConfigTable(db)

        createIndexes(db)

        Log.d(TAG, "Completed migration to version 22")

    }

    private fun createFieldTraitConfigTable(db: SQLiteDatabase) {
        val query = ("""
            CREATE TABLE IF NOT EXISTS ${FieldTraitConfigTable.TABLE_NAME} (
                ${FieldTraitConfigTable.ID} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${FieldTraitConfigTable.STUDY_ID} INTEGER NOT NULL UNIQUE,
                ${FieldTraitConfigTable.TRAIT_IDS} TEXT,
                ${FieldTraitConfigTable.CREATED_AT} TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                ${FieldTraitConfigTable.UPDATED_AT} TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                FOREIGN KEY(${FieldTraitConfigTable.STUDY_ID}) REFERENCES ${Migrator.Study.tableName}(${Migrator.Study.PK}) ON DELETE CASCADE
            )
        """).trimIndent()

        db.execSQL(query)
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_field_trait_config_study_id ON ${FieldTraitConfigTable.TABLE_NAME}(${FieldTraitConfigTable.STUDY_ID})")
    }
}
