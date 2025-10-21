package com.fieldbook.tracker.database.dao

import android.util.Log
import androidx.core.content.contentValuesOf
import com.fieldbook.tracker.database.GroupsTable
import com.fieldbook.tracker.database.models.GroupModel
import com.fieldbook.tracker.database.withDatabase
import com.fieldbook.tracker.database.query
import com.fieldbook.tracker.database.toFirst

/**
 * Assign groups to fields,traits,etc.
 * Can be structured in a way that unused groups can be deleted (for studies)
 */
class GroupDao(private val groupType: String) {

    companion object {
        private const val TAG = "GroupDao"
    }

    /**
     * Returns all group names from the database
     */
    fun getAllGroups(): List<GroupModel>? = withDatabase { db ->
        val groups = mutableListOf<GroupModel>()

        try {
            db.query(
                GroupsTable.TABLE_NAME,
                arrayOf(GroupsTable.ID, GroupsTable.GROUP_NAME, GroupsTable.IS_EXPANDED),
                "${GroupsTable.GROUP_TYPE} = ?", arrayOf(groupType)
            ).use { cursor ->
                while (cursor.moveToNext()) {
                    val id = cursor.getInt(cursor.getColumnIndexOrThrow(GroupsTable.ID))
                    val name = cursor.getString(cursor.getColumnIndexOrThrow(GroupsTable.GROUP_NAME))
                    val expanded =
                        cursor.getString(cursor.getColumnIndexOrThrow(GroupsTable.IS_EXPANDED)) != "false"
                    groups.add(GroupModel(id, name, expanded))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Column not found in getAllGroups: ${e.message}")
            return@withDatabase emptyList<GroupModel>()
        }

        groups
    }

    /**
     * Get a group's ID by its name and type
     * Returns null if groupName is null
     */
    fun getGroupIdByName(groupName: String?): Int? = withDatabase { db ->
        if (groupName == null) return@withDatabase null

        db.query(
            GroupsTable.TABLE_NAME,
            select = arrayOf(GroupsTable.ID),
            where = "${GroupsTable.GROUP_NAME} = ? AND ${GroupsTable.GROUP_TYPE} = ?",
            whereArgs = arrayOf(groupName, groupType)
        ).toFirst()[GroupsTable.ID] as? Int
    }

    /**
     * Get a group's name by its ID
     */
    fun getGroupNameById(groupId: Int?): String? = withDatabase { db ->
        if (groupId == null) return@withDatabase null

        db.query(
            GroupsTable.TABLE_NAME,
            select = arrayOf(GroupsTable.GROUP_NAME),
            where = "${GroupsTable.ID} = ? AND ${GroupsTable.GROUP_TYPE} = ?",
            whereArgs = arrayOf("$groupId", groupType)
        ).toFirst()[GroupsTable.GROUP_NAME] as? String
    }

    /**
     * Creates a new group with the given name if it doesn't exist
     * Returns the group ID
     */
    fun createOrGetGroup(groupName: String): Int? = withDatabase { db ->
        // check if group already exists
        val existingId = getGroupIdByName(groupName)
        if (existingId != null) {
            return@withDatabase existingId
        }

        val id = db.insert(
            GroupsTable.TABLE_NAME,
            null,
            contentValuesOf(
                GroupsTable.GROUP_NAME to groupName,
                GroupsTable.GROUP_TYPE to groupType
            )
        )

        id.toInt()
    }

    /**
     * Deletes ALL groups that have no associated studies/traits
     */
    fun deleteUnusedGroups() = withDatabase { db ->
        val tableName = GroupsTable.Type.getTableName(groupType)

        val query = """
                SELECT ${GroupsTable.ID} 
                FROM ${GroupsTable.TABLE_NAME} 
                WHERE ${GroupsTable.GROUP_TYPE} = ? AND ${GroupsTable.ID} NOT IN (
                    SELECT DISTINCT ${GroupsTable.FK} 
                    FROM $tableName 
                    WHERE ${GroupsTable.FK} IS NOT NULL
                )
            """

        db.rawQuery(query, arrayOf(groupType)).use { cursor ->
            while (cursor.moveToNext()) {
                try {
                    val groupId = cursor.getInt(cursor.getColumnIndexOrThrow(GroupsTable.ID))
                    deleteGroup(groupId)
                } catch (e: Exception) {
                    Log.e(TAG, "Column not found in deleteStudyGroups: ${e.message}")
                }
            }
        }
    }

    /**
     * Delete a group
     */
    private fun deleteGroup(groupId: Int) = withDatabase { db ->
        db.delete(
            GroupsTable.TABLE_NAME,
            "${GroupsTable.ID} = ?",
            arrayOf("$groupId")
        )
    }

    fun updateGroupIsExpanded(groupId: Int, isExpanded: Boolean) = withDatabase { db ->
        db.update(
            GroupsTable.TABLE_NAME,
            contentValuesOf(GroupsTable.IS_EXPANDED to if (isExpanded) "true" else "false"),
            "${GroupsTable.ID} = ?",
            arrayOf("$groupId")
        )
    }

    fun getIsExpanded(groupId: Int): Boolean = withDatabase { db ->
        val result = db.query(
            GroupsTable.TABLE_NAME,
            select = arrayOf(GroupsTable.IS_EXPANDED),
            where = "${GroupsTable.ID} = ?",
            whereArgs = arrayOf("$groupId")
        ).toFirst()[GroupsTable.IS_EXPANDED] as? String

        result != "false"
    } == true
}