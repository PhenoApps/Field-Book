package com.fieldbook.tracker.cropontology.tables

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "variables_fts")
@Fts4(contentEntity = Variable::class)
data class VariableFts(
    val name: String?,
    val ontologyName: String?,
    val scaleName: String?,
    val methodName: String?,
    val institution: String?,
    val scientist: String?,
    val crop: String?,
    val description: String?,
    val methodClass: String?,
    val traitName: String?,
    val traitClass: String?,
    val traitDescription: String?,
    val entity: String?,
    val attribute: String?
)