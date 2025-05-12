package com.fieldbook.tracker.database.dao

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

            db.query(
                StudyGroupsTable.TABLE_NAME,
                select = arrayOf(StudyGroupsTable.ID, "group_name", "is_expanded"),
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getInt(cursor.getColumnIndexOrThrow(StudyGroupsTable.ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("group_name"))
                    val expanded = cursor.getString(cursor.getColumnIndexOrThrow("is_expanded")) != "false"
                    groups.add(StudyGroupModel(id, name, expanded))
                }
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
                where = "group_name = ?",
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
                select = arrayOf("group_name"),
                where = "${StudyGroupsTable.ID} = ?",
                whereArgs = arrayOf("$groupId")
            ).toFirst()["group_name"] as? String
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
                contentValuesOf("group_name" to groupName)
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
                    val groupId = cursor.getInt(cursor.getColumnIndexOrThrow(StudyGroupsTable.ID))
                    deleteStudyGroup(groupId)
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
                contentValuesOf("is_expanded" to if (isExpanded) "true" else "false"),
                "${StudyGroupsTable.ID} = ?",
                arrayOf("$groupId")
            )
        }

        fun getIsExpanded(groupId: Int): Boolean = withDatabase { db ->
            val result = db.query(
                StudyGroupsTable.TABLE_NAME,
                select = arrayOf("is_expanded"),
                where = "${StudyGroupsTable.ID} = ?",
                whereArgs = arrayOf("$groupId")
            ).toFirst()["is_expanded"] as? String

            result != "false"
        } == true
    }
}