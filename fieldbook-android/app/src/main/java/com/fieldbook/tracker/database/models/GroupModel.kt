package com.fieldbook.tracker.database.models

data class GroupModel(
    val id: Int,
    val groupName: String,
    val isExpanded: Boolean = true
)