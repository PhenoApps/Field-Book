package com.fieldbook.tracker.database.dao

import androidx.core.content.contentValuesOf
import com.fieldbook.tracker.database.Migrator.StudyGroup
import com.fieldbook.tracker.database.Migrator.Study
import com.fieldbook.tracker.database.withDatabase
import com.fieldbook.tracker.database.query
import com.fieldbook.tracker.database.toFirst


class StudyGroupDao {
    companion object {
        private const val TAG = "StudyGroupDao"

        /**
         * Returns all group names from the database
         */
        fun getAllStudyGroups(): MutableList<Pair<Int, String>>? = withDatabase { db ->
            val groups = mutableListOf<Pair<Int, String>>()

            db.query(
                StudyGroup.TABLE_NAME,
                orderBy = "group_name"
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getInt(cursor.getColumnIndexOrThrow(StudyGroup.PK))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow("group_name"))
                    groups.add(Pair(id, name))
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
                StudyGroup.TABLE_NAME,
                select = arrayOf(StudyGroup.PK),
                where = "group_name = ?",
                whereArgs = arrayOf(groupName)
            ).toFirst()[StudyGroup.PK] as? Int
        }

        /**
         * Get a group's name by its ID
         */
        fun getStudyGroupNameById(groupId: Int?): String? = withDatabase { db ->
            if (groupId == null) return@withDatabase null

            db.query(
                StudyGroup.TABLE_NAME,
                select = arrayOf("group_name"),
                where = "${StudyGroup.PK} = ?",
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
                StudyGroup.TABLE_NAME,
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
                SELECT ${StudyGroup.PK} 
                FROM ${StudyGroup.TABLE_NAME} 
                WHERE ${StudyGroup.PK} NOT IN (
                    SELECT DISTINCT ${StudyGroup.FK} 
                    FROM ${Study.tableName} 
                    WHERE ${StudyGroup.FK} IS NOT NULL
                )
            """

            db.rawQuery(query, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val groupId = cursor.getInt(cursor.getColumnIndexOrThrow(StudyGroup.PK))
                    deleteStudyGroup(groupId)
                }
            }
        }

        /**
         * Delete a studyGroup
         */
        fun deleteStudyGroup(groupId: Int) = withDatabase { db ->
            db.delete(
                StudyGroup.TABLE_NAME,
                "${StudyGroup.PK} = ?",
                arrayOf("$groupId")
            )
        }
    }
}