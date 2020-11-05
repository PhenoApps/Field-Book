package com.fieldbook.tracker.database.models

import com.fieldbook.tracker.database.*

/**
 * Are attribute names unique?
 */
class ObservationUnitAttributeModel(val map: Row) {
        val internal_id_observation_unit_attribute: Int by map
        val observation_unit_attribute_name: String? by map
        val study_db_id: Int by map //fk to study table
    companion object {
        const val PK = "internal_id_observation_unit_attribute"
        const val FK = "observation_unit_attribute_db_id"
        val migrateFromTableName = "plot_attributes"
        val tableName = "observation_units_attributes"
        val columnDefs by lazy {
            mapOf(PK to "INTEGER PRIMARY KEY AUTOINCREMENT",
                    "observation_unit_attribute_name" to "TEXT",
                    StudyModel.FK to "INT REFERENCES ${StudyModel.tableName}(${StudyModel.PK}) ON DELETE CASCADE")
        }
        val migratePattern by lazy {
            mapOf("attribute_id" to PK,
                    "attribute_name" to "observation_unit_attribute_name",
                    "exp_id" to StudyModel.FK,)
        }

        fun getAllNames(eid: Int): Array<String> = withDatabase { db ->
            db.query(tableName,
                    where = "${StudyModel.FK} = ?",
                    whereArgs = arrayOf(eid.toString())).toTable()
                .map { it["observation_unit_attribute_name"] as String }
                .toTypedArray()
        } ?: emptyArray()

        fun getIdByName(name: String): Int = withDatabase { db ->
            db.query(tableName,
                where = "observation_unit_attribute_name = ?",
                whereArgs = arrayOf(name)).toFirst()[PK] as Int
        } ?: -1
    }
}