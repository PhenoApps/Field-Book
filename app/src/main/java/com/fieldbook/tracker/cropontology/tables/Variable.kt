package com.fieldbook.tracker.cropontology.tables

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.fieldbook.tracker.cropontology.models.Method
import com.fieldbook.tracker.cropontology.models.Scale
import com.fieldbook.tracker.cropontology.models.Trait

@Entity(tableName = "variables")
data class Variable(

    @PrimaryKey(autoGenerate = true)
    val rowid: Int? = null,

    val observationVariableDbId: String?,
    val name: String?,
    val ontologyDbId: String?,
    val ontologyName: String?,
    val institution: String?,
    val scientist: String?,
    val language: String?,
    val crop: String?,

    @Embedded
    val trait: Trait?,

    @Embedded
    val method: Method?,

    @Embedded
    val scale: Scale?,

    val defaultValue: String?
) {
    override fun toString(): String {
        return "Variable: $name \n Trait: $trait \n Method: $method \n Scale: $scale" //$trait $method $scale $defaultValue"
    }
}