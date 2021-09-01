package com.fieldbook.tracker.cropontology.dao

import androidx.room.*
import com.fieldbook.tracker.cropontology.models.RankedVariable

@Dao
interface VariableFtsDao {

    @Query("""
        SELECT variables.*, matchinfo(variables_fts, "pcx") as "matchInfo"
        FROM variables
        JOIN variables_fts ON variables_fts.rowid = variables.rowid
        WHERE variables_fts MATCH :query
    """)
    fun search(query: String): List<RankedVariable>

    @Query("""INSERT INTO variables_fts (variables_fts) VALUES ("rebuild")""")
    fun rebuild()
}