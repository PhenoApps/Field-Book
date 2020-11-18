package com.fieldbook.tracker.database.dao

import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.Migrator.ObservationVariableAttribute

class ObservationVariableAttributeDao {

    companion object {

        fun getAttributeNameById(id: Int) = withDatabase { db ->

            db.query(ObservationVariableAttribute.tableName,
                    where = "${ObservationVariableAttribute.PK} = ?",
                    whereArgs = arrayOf("$id")).toFirst()["observation_variable_attribute_name"]

        }

        fun getAttributeIdByName(name: String): Int = withDatabase { db ->

            db.query(ObservationVariableAttribute.tableName,
                    where = "observation_variable_attribute_name LIKE ?",
                    whereArgs = arrayOf(name)).toFirst()[ObservationVariableAttribute.PK] as Int

        } ?: -1

        fun getAll() = withDatabase { it.query(ObservationVariableAttribute.tableName).toTable() }

        fun getAllNames() = withDatabase { db ->
            db.query(ObservationVariableAttribute.tableName, select = arrayOf("observation_variable_attribute_name"))
                    .toTable()
                    .map { it["observation_variable_attribute_name"].toString() }
        }

    }
}