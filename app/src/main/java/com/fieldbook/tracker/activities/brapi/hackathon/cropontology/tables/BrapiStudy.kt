package com.fieldbook.tracker.activities.brapi.hackathon.cropontology.tables

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import org.brapi.v2.model.core.BrAPIStudy

@Entity(tableName = "brapi_study")
data class BrapiStudy(

    @PrimaryKey(autoGenerate = true)
    val rowid: Int? = null,
    val studyName: String,
    val studyDbId: String?,
    val active: String,
    val culturalPractices: String?,
    val documentationUrl: String?,
    val license: String?,
    val locationDbId: String?,
    val locationName: String?,
    val obsUnitDescription: String?,
    val seasons: String?,
    val studyCode: String?,
    val studyPUI: String?,
    val studyType: String?,
    val trialDbId: String?,
    val trialName: String?,
    val description: String?,
    val commonCropName: String?,
    //add program name / dbid

) {
    override fun toString(): String {
        return "Study: ${studyName}"
    }
}