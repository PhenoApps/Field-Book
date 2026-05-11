package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.fieldbook.tracker.database.Migrator.Study
import com.fieldbook.tracker.database.GroupsTable

/**
 * Migration for adding groups table
 * Adds group_id as FK, and is_archived to studies table
 */
class GroupMigratorVersion14: FieldBookMigrator {

    companion object {
        private const val TAG = "GroupMigratorVersion14"
        const val VERSION = 14
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {

        Log.d(TAG, "Starting migration to version 14 - Adding groups table")

        createGroupsTable(db)

        addArchivedColumnToStudies(db)

        addGroupIdToStudies(db)

        createIndexes(db)

        Log.d(TAG, "Completed migration to version 14")

    }

    private fun createGroupsTable(db: SQLiteDatabase) {
        val query = ("""
            CREATE TABLE IF NOT EXISTS ${GroupsTable.TABLE_NAME} (
                ${GroupsTable.ID} INTEGER PRIMARY KEY AUTOINCREMENT, 
                ${GroupsTable.GROUP_NAME} TEXT UNIQUE NOT NULL,
                ${GroupsTable.GROUP_TYPE} TEXT NOT NULL,
                ${GroupsTable.IS_EXPANDED} TEXT DEFAULT 'true' NOT NULL
            )
        """).trimIndent()

        db.execSQL(query)
    }

    private fun addArchivedColumnToStudies(db: SQLiteDatabase) {
        db.execSQL("""
            ALTER TABLE ${Study.tableName} 
            ADD COLUMN is_archived TEXT DEFAULT 'false' NOT NULL
        """)
    }

    private fun addGroupIdToStudies(db: SQLiteDatabase) {
        db.execSQL("""
            ALTER TABLE ${Study.tableName} 
            ADD COLUMN ${GroupsTable.FK} INTEGER DEFAULT NULL 
            REFERENCES ${GroupsTable.TABLE_NAME}(id) ON DELETE SET NULL
        """)
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_group_studies_name ON ${GroupsTable.TABLE_NAME}(group_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_studies_group_id ON ${Study.tableName}(${GroupsTable.FK})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_studies_archived ON ${Study.tableName}(is_archived)")
    }
}