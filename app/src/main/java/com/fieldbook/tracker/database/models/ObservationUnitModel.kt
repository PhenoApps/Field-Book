package com.fieldbook.tracker.database.models

import com.fieldbook.tracker.database.query
import com.fieldbook.tracker.database.toTable
import com.fieldbook.tracker.database.withDatabase
import io.swagger.client.model.ObservationUnit
import java.util.HashMap

/**
 * Plot-level table structure.
 * Are XType, YType replacing the dynamic primary/secondary ids?
 */
data class ObservationUnitModel(val map: Map<String, Any?>) {

        val internal_id_observation_unit: Int by map //comp. pk 1
        val study_db_id: Int by map  //fk to studies table
        val observation_unit_db_id: String by map //unique id
        val geo_coordinates: String? by map //blob?
        val additionalInfo: String? by map //blob, can be replaced with value/attr query?
        val germplasmDbId: String? by map //brapId ?
        val germplasmName: String? by map
        val observationLevel: String? by map
        val position_coordinate_x: String? by map //x-axis value e.g row=1
        val positionCoordinateXType: String? by map //x-axis label?
        val position_coordinate_y: String? by map //y-axis value e.g col=2
        val positionCoordinateYType: String? by map

    companion object {
        const val PK = "internal_id_observation_unit"
        const val FK = "observation_unit_id"
        val migrateFromTableName = "plots"
        val tableName = "observation_units"
        val columnDefs by lazy {
            mapOf(PK to "INTEGER PRIMARY KEY AUTOINCREMENT",
                    "study_db_id" to "INT REFERENCES ${StudyModel.tableName}(${StudyModel.PK}) ON DELETE CASCADE",
                    "observation_unit_db_id" to "TEXT",
                    "geo_coordinates" to "TEXT",
                    "additional_info" to "TEXT",
                    "germplasm_db_id" to "TEXT",
                    "germplasm_name" to "TEXT",
                    "observation_level" to "TEXT",
                    "position_coordinate_x" to "TEXT",
                    "position_coordinate_x_type" to "TEXT",
                    "position_coordinate_y" to "TEXT",
                    "position_coordinate_y_type" to "TEXT"
            )
        }
        val migratePattern by lazy {
            mapOf("plot_id" to PK,
                    "exp_id" to "study_db_id",
                    "unique_id" to "observation_unit_db_id",
                    //TODO Trevor, verify position_x/y are for primary./secondary
                    "primary_id" to "position_coordinate_x",
                    "secondary_id" to "position_coordinate_y",
                    "coordinates" to "geo_coordinates",)
        }

        inline fun mapColumns(func: (keys: String) -> Unit) = columnDefs.keys.forEach {
            func(it)
        }


        fun checkUnique(values: HashMap<String, String>): Boolean = withDatabase { db ->

            db.query(tableName,
                    select = arrayOf("observation_unit_db_id")).toTable().forEach {
                if (it["observation_unit_db_id"] in values.keys) return@withDatabase false
            }

            true

        } ?: false

        fun getAll(eid: Int): Array<ObservationUnitModel> = withDatabase { db ->

            arrayOf(*db.query(tableName,
                    where = "${StudyModel.FK} = ?",
                    whereArgs = arrayOf(eid.toString())).toTable()
                    .map { ObservationUnitModel(it) }
                    .toTypedArray())

        } ?: emptyArray<ObservationUnitModel>()

        fun getAll(): Array<ObservationUnitModel> = withDatabase { db ->

            arrayOf(*db.query(tableName).toTable()
                    .map { ObservationUnitModel(it) }
                    .toTypedArray())

        } ?: emptyArray<ObservationUnitModel>()
    }
}