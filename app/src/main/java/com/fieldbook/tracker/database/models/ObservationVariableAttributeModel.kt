package com.fieldbook.tracker.database.models

import com.fieldbook.tracker.database.*

/**
 * Are attribute names unique?
 */
class ObservationVariableAttributeModel(val map: Row) {
        val internal_id_observation_variable_attribute: Int by map
        val observation_variable_attribute_name: String? by map
    companion object {
        val defaultColumnDefs = mapOf(
                "validValuesMax" to "Text",
                "validValuesMin" to "Text",
                "category" to "Text",
                "decimalPlaces" to "Text",
                "percentBreaks" to "Text",
                "dateFormat" to "Text",
                "multiMeasures" to "Text",
                "minMeasures" to "Text",
                "maxMeasures" to "Text",
                "separatorValue" to "Text",
                "categoryLimit" to "Text",
                "formula" to "Text",
        )
        const val PK = "internal_id_observation_variable_attribute"
        const val FK = "observation_variable_attribute_db_id"
        val migrateFromTableName = null
        val tableName = "observation_variable_attributes"
        val columnDefs by lazy {
            mapOf(PK to "INTEGER PRIMARY KEY AUTOINCREMENT",
                    "observation_variable_attribute_name" to "Text",
            )}

        fun getAttributeNameById(id: Int) = withDatabase { db ->

            db.query(tableName,
                    where = "$PK = ?",
                    whereArgs = arrayOf("$id")).toFirst()["observation_variable_attribute_name"]

        }

        fun getAttributeIdByName(name: String): Int = withDatabase { db ->

            db.query(tableName,
                    where = "observation_variable_attribute_name LIKE ?",
                    whereArgs = arrayOf(name)).toFirst()[PK] as Int

        } ?: -1

        fun getAll() = withDatabase { it.query(tableName).toTable() }

        fun getAllNames() = withDatabase { db ->
            db.query(tableName, select = arrayOf("observation_variable_attribute_name"))
                    .toTable()
                    .map { it["observation_variable_attribute_name"].toString() }
        }

    }
}