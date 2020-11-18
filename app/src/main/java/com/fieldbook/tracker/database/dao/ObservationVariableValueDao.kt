package com.fieldbook.tracker.database.dao

import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.Migrator.ObservationVariable
import com.fieldbook.tracker.database.Migrator.ObservationVariableValue

class ObservationVariableValueDao {

    companion object {

        fun getVariableValues(id: Int) = withDatabase { db ->

            return@withDatabase db.query(ObservationVariableValue.tableName,
                    where = "${ObservationVariable.FK} = ?",
                    whereArgs = arrayOf("$id")).toTable()

        }

        fun getAll() = withDatabase { it.query(ObservationVariableValue.tableName).toTable() }
    }
}