package com.fieldbook.tracker.activities.brapi.hackathon.cropontology.dao

import androidx.room.*
import com.fieldbook.tracker.activities.brapi.hackathon.cropontology.tables.BrapiStudy

@Dao
interface BrapiStudyDao {

    @Update
    fun update(vararg e: BrapiStudy): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg items: BrapiStudy)

    @Delete
    fun delete(vararg e: BrapiStudy): Int

    @Query("SELECT studyName FROM brapi_study")
    fun getNames(): List<String>
}