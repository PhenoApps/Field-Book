package com.fieldbook.tracker.interfaces

import com.fieldbook.tracker.database.models.GroupModel

interface  FieldGroupController {
    fun getStudyGroupNameById(groupId: Int?): String?
    fun getStudyGroupIdByName(groupName: String?): Int?
    fun getAllStudyGroups(): List<GroupModel>
    fun updateIsExpanded(groupId: Int, isExpanded: Boolean)
    fun getIsExpanded(groupId: Int): Boolean
}