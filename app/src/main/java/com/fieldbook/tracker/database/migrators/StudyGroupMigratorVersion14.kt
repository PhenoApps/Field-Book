package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import com.fieldbook.tracker.database.Migrator.StudyGroup
import com.fieldbook.tracker.database.Migrator.Study

/**
 * Migration for adding study_groups table
 * Adds group_id as FK, and isArchived to studies table
 */
class StudyGroupMigratorVersion14: FieldBookMigrator {

    companion object {
        private const val TAG = "StudyGroupMigrator14"
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {

        Log.d(TAG, "Starting migration to version 14 - Adding group_studies table")

        createStudyGroupsTable(db)

        addArchivedColumnToStudies(db)

        addGroupIdToStudies(db)

        createIndexes(db)

        Log.d(TAG, "Completed migration to version 14")

    }

    private fun createStudyGroupsTable(db: SQLiteDatabase) {
        val query = ("""
            CREATE TABLE IF NOT EXISTS ${StudyGroup.TABLE_NAME} (
                ${StudyGroup.PK} INTEGER PRIMARY KEY AUTOINCREMENT, 
                group_name TEXT UNIQUE NOT NULL,
                isExpanded TEXT DEFAULT 'true' NOT NULL
            )
        """).trimIndent()

        db.execSQL(query)
    }

    private fun addArchivedColumnToStudies(db: SQLiteDatabase) {
        db.execSQL("""
            ALTER TABLE ${Study.tableName} 
            ADD COLUMN isArchived TEXT DEFAULT 'false' NOT NULL
        """)
    }

    private fun addGroupIdToStudies(db: SQLiteDatabase) {
        db.execSQL("""
            ALTER TABLE ${Study.tableName} 
            ADD COLUMN ${StudyGroup.FK} INTEGER DEFAULT NULL 
            REFERENCES ${StudyGroup.TABLE_NAME}(id) ON DELETE SET NULL
        """)
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_group_studies_name ON ${StudyGroup.TABLE_NAME}(group_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_studies_group_id ON ${Study.tableName}(${StudyGroup.FK})")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_studies_archived ON ${Study.tableName}(isArchived)")
    }
}