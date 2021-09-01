package com.fieldbook.tracker.cropontology.dao

import androidx.room.*
import com.fieldbook.tracker.cropontology.tables.Variable

@Dao
interface VariableDao {

    @Update
    fun update(vararg e: Variable?): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg items: Variable)

    @Delete
    fun delete(vararg e: Variable): Int

    @Query("SELECT name FROM variables")
    fun getNames(): List<String>
}