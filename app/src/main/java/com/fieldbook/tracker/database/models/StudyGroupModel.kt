package com.fieldbook.tracker.database.models

data class StudyGroupModel(
    val id: Int,
    val groupName: String,
    val isExpanded: Boolean = true
)