package com.fieldbook.tracker.activities.brapi.hackathon.cropontology.dao

import androidx.room.*
import com.fieldbook.tracker.activities.brapi.hackathon.cropontology.models.RankedStudy

@Dao
interface StudyFtsDao {

    @Query("""
        SELECT brapi_study.*, matchinfo(brapi_study_fts, "pcx") as "matchInfo"
        FROM brapi_study
        JOIN brapi_study_fts ON brapi_study_fts.rowid = brapi_study.rowid
        WHERE brapi_study_fts MATCH :query
    """)
    fun search(query: String): List<RankedStudy>

    @Query("""INSERT INTO brapi_study_fts (brapi_study_fts) VALUES ("rebuild")""")
    fun rebuild()
}