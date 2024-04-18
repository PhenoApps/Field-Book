package com.fieldbook.tracker.activities.brapi.hackathon.cropontology.tables

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "brapi_study_fts")
@Fts4(contentEntity = BrapiStudy::class)
data class BrapiStudyFts(
    @Embedded
    val study: BrapiStudy?
)