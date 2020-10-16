package com.fieldbook.tracker.database.models

import com.fieldbook.tracker.database.*
import io.swagger.client.model.Study

/**
 * ObservationVariable Attribute/Value Models are used to store
 * user-input traits, these values are transposed
 * and used as columns in the range view.
 */
data class ObservationVariableValueModel(val map: Row) {
        val internal_id_observation_variable_value: Int by map //comp. pk 1
        val observation_variable_attribute_db_id: Int by map      //foreign key to unit attribute
        val observation_variable_attribute_value: String? by map //says Name in excel, but should this be value?, string value saved for this attribute
        val observation_variable_id: Int by map //comp. pk 2
    companion object {
        const val PK = "internal_id_observation_variable_value"
        const val FK = "observation_variable_value_db_id"
        val tableName = "observation_variable_values"
        val columnDefs by lazy {
            mapOf(PK to "INTEGER PRIMARY KEY AUTOINCREMENT",
                    ObservationVariableAttributeModel.FK to "INT REFERENCES ${ObservationVariableAttributeModel.tableName}(${ObservationVariableAttributeModel.PK}) ON DELETE CASCADE",
                    "observation_variable_attribute_value" to "TEXT",
                    ObservationVariableModel.FK to "INT REFERENCES ${ObservationVariableModel.tableName}(${ObservationVariableModel.PK}) ON DELETE CASCADE",
            )}

        fun getVariableValues(id: Int) = withDatabase { db ->

            return@withDatabase db.query(tableName,
                    where = "${ObservationVariableModel.FK} = ?",
                    whereArgs = arrayOf("$id")).toTable()

        }

        fun getAll() = withDatabase { it.query(tableName).toTable() }
    }
}