package com.fieldbook.tracker.utilities

import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.models.GroupModel
import com.fieldbook.tracker.interfaces.FieldGroupController
import javax.inject.Inject


class FieldGroupControllerImpl @Inject constructor(private val db: DataHelper) : FieldGroupController {

    override fun getStudyGroupNameById(groupId: Int?): String? = db.getStudyGroupNameById(groupId)

    override fun getStudyGroupIdByName(groupName: String?): Int? = db.getStudyGroupIdByName(groupName)

    override fun getAllStudyGroups(): List<GroupModel> = db.allStudyGroups

    override fun updateIsExpanded(groupId: Int, isExpanded: Boolean) = db.updateStudyGroupIsExpanded(groupId, isExpanded)

    override fun getIsExpanded(groupId: Int): Boolean = db.getStudyGroupIsExpanded(groupId)
}