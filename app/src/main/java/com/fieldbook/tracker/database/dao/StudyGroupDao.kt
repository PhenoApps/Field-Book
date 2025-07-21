package com.fieldbook.tracker.database.dao

import android.util.Log
import androidx.core.content.contentValuesOf
import com.fieldbook.tracker.database.Migrator.Study
import com.fieldbook.tracker.database.StudyGroupsTable
import com.fieldbook.tracker.database.models.StudyGroupModel
import com.fieldbook.tracker.database.withDatabase
import com.fieldbook.tracker.database.query
import com.fieldbook.tracker.database.toFirst

/**
 * Assign groups to fields
 * Structured in a way that unused groups are deleted
 */
class StudyGroupDao {
    companion object {
        private const val TAG = "StudyGroupDao"

        /**
         * Returns all group names from the database
         */
        fun getAllStudyGroups(): List<StudyGroupModel>? = withDatabase { db ->
            val groups = mutableListOf<StudyGroupModel>()

            try {
                db.query(
                    StudyGroupsTable.TABLE_NAME,
                    select = arrayOf(StudyGroupsTable.ID, StudyGroupsTable.GROUP_NAME, StudyGroupsTable.IS_EXPANDED),
                ).use { cursor ->
                    while (cursor.moveToNext()) {
                        val id = cursor.getInt(cursor.getColumnIndexOrThrow(StudyGroupsTable.ID))
                        val name = cursor.getString(cursor.getColumnIndexOrThrow(StudyGroupsTable.GROUP_NAME))
                        val expanded =
                            cursor.getString(cursor.getColumnIndexOrThrow(StudyGroupsTable.IS_EXPANDED)) != "false"
                        groups.add(StudyGroupModel(id, name, expanded))
                    }
                }
            } catch (e: Exception) {
                    Log.e(TAG, "Column not found in getAllStudyGroups: ${e.message}")
                    return@withDatabase emptyList<StudyGroupModel>()
            }

            groups
        }

        /**
         * Get a group's ID by its name
         * Ungrouped fields will return null
         */
        fun getStudyGroupIdByName(groupName: String?): Int? = withDatabase { db ->
            if (groupName == null) return@withDatabase null

            db.query(
                StudyGroupsTable.TABLE_NAME,
                select = arrayOf(StudyGroupsTable.ID),
                where = "${StudyGroupsTable.GROUP_NAME} = ?",
                whereArgs = arrayOf(groupName)
            ).toFirst()[StudyGroupsTable.ID] as? Int
        }

        /**
         * Get a group's name by its ID
         */
        fun getStudyGroupNameById(groupId: Int?): String? = withDatabase { db ->
            if (groupId == null) return@withDatabase null

            db.query(
                StudyGroupsTable.TABLE_NAME,
                select = arrayOf(StudyGroupsTable.GROUP_NAME),
                where = "${StudyGroupsTable.ID} = ?",
                whereArgs = arrayOf("$groupId")
            ).toFirst()[StudyGroupsTable.GROUP_NAME] as? String
        }

        /**
         * Creates a new group with the given name if it doesn't exist
         * Returns the group ID
         */
        fun createOrGetStudyGroup(groupName: String): Int? = withDatabase { db ->
            // check if group already exists
            val existingId = getStudyGroupIdByName(groupName)
            if (existingId != null) {
                return@withDatabase existingId
            }

            val id = db.insert(
                StudyGroupsTable.TABLE_NAME,
                null,
                contentValuesOf(StudyGroupsTable.GROUP_NAME to groupName)
            )

            id.toInt()
        }

        /**
         * Deletes ALL study groups that have no associated studies
         */
        fun deleteUnusedStudyGroups() = withDatabase { db ->

            val query = """
                SELECT ${StudyGroupsTable.ID} 
                FROM ${StudyGroupsTable.TABLE_NAME} 
                WHERE ${StudyGroupsTable.ID} NOT IN (
                    SELECT DISTINCT ${StudyGroupsTable.FK} 
                    FROM ${Study.tableName} 
                    WHERE ${StudyGroupsTable.FK} IS NOT NULL
                )
            """

            db.rawQuery(query, null).use { cursor ->
                while (cursor.moveToNext()) {
                    try {
                        val groupId = cursor.getInt(cursor.getColumnIndexOrThrow(StudyGroupsTable.ID))
                        deleteStudyGroup(groupId)
                    } catch (e: Exception) {
                        Log.e(TAG, "Column not found in deleteUnusedStudyGroups: ${e.message}")
                    }
                }
            }
        }

        /**
         * Delete a studyGroup
         */
        private fun deleteStudyGroup(groupId: Int) = withDatabase { db ->
            db.delete(
                StudyGroupsTable.TABLE_NAME,
                "${StudyGroupsTable.ID} = ?",
                arrayOf("$groupId")
            )
        }

        fun updateStudyGroupIsExpanded(groupId: Int, isExpanded: Boolean) = withDatabase { db ->
            db.update(
                StudyGroupsTable.TABLE_NAME,
                contentValuesOf(StudyGroupsTable.IS_EXPANDED to if (isExpanded) "true" else "false"),
                "${StudyGroupsTable.ID} = ?",
                arrayOf("$groupId")
            )
        }

        fun getIsExpanded(groupId: Int): Boolean = withDatabase { db ->
            val result = db.query(
                StudyGroupsTable.TABLE_NAME,
                select = arrayOf(StudyGroupsTable.IS_EXPANDED),
                where = "${StudyGroupsTable.ID} = ?",
                whereArgs = arrayOf("$groupId")
            ).toFirst()[StudyGroupsTable.IS_EXPANDED] as? String

            result != "false"
        } == true
    }
}