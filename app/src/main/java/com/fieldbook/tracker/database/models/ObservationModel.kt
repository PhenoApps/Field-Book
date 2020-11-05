package com.fieldbook.tracker.database.models

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import com.fieldbook.tracker.brapi.Image
import com.fieldbook.tracker.brapi.Observation
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.models.ObservationModel.Companion.FK
import com.fieldbook.tracker.database.models.ObservationUnitModel.Companion.FK
import io.swagger.client.model.ObservationUnit
import io.swagger.client.model.Study
import org.threeten.bp.format.DateTimeFormatter
import java.util.*

data class ObservationModel(val map: Row) {
        val internal_id_observation: Int by map
        val observation_unit_id: String by map
        val observation_variable_db_id: Int by map
        val observation_variable_field_book_format: String? by map
        val observation_variable_name: String? by map
        val value: String? by map
        val observation_time_stamp: String? by map
        val collector: String? by map
        val geo_coordinates: String? by map
        val study_db_id: String? by map
        val last_synced_time: String by map
        val additional_info: String? by map
    companion object {
        const val PK = "internal_id_observation"
        const val FK = "observation_id"
        val migrateFromTableName = "user_traits"
        val tableName = "observations"
        val columnDefs by lazy {
            mapOf(PK to "INTEGER PRIMARY KEY AUTOINCREMENT",
                    ObservationUnitModel.FK to "TEXT REFERENCES ${ObservationUnitModel.tableName}(${ObservationUnitModel.PK})",
                    StudyModel.FK to "INT REFERENCES ${StudyModel.tableName}(${StudyModel.PK}) ON DELETE CASCADE",
                    ObservationVariableModel.FK to "INT REFERENCES ${ObservationVariableModel.tableName}(${ObservationVariableModel.PK})",
                    "observation_variable_name" to "TEXT",
                    "observation_variable_field_book_format" to "TEXT",
                    "value" to "TEXT",
                    "observation_time_stamp" to "TEXT",
                    "collector" to "TEXT",
                    "geoCoordinates" to "TEXT",
                    "observation_db_id" to "TEXT",
                    "last_synced_time" to "TEXT",
                    "additional_info" to "TEXT")
        }
        val migratePattern by lazy {
            mapOf("id" to PK,
                    "rid" to ObservationUnitModel.FK,
                    "exp_id" to StudyModel.FK,
                    "parent" to "observation_variable_name",
                    "trait" to "observation_variable_field_book_format",
                    "userValue" to "value",
                    "timeTaken" to "observation_time_stamp",
                    "person" to "collector",
                    "location" to "geoCoordinates",
                    "observation_db_id" to "observation_db_id",
                    "last_synced_time" to "last_synced_time")
        }

        fun getAll(): Array<ObservationModel>? = withDatabase { db ->

            db.query(tableName)
                .toTable()
                .map { ObservationModel(it) }
                .toTypedArray()

        } ?: emptyArray()

        fun insertObservation(model: ObservationModel): Int = withDatabase { db ->

            db.insert(tableName, null, contentValuesOf(
                "observation_variable_name" to model.observation_variable_name,
                "observation_variable_field_book_format" to model.observation_variable_field_book_format,
                "value" to model.value,
                "observation_time_stamp" to model.observation_time_stamp,
                "collector" to model.collector,
                "geoCoordinates" to model.geo_coordinates,
                "study_db_id" to model.study_db_id,
                "last_synced_time" to model.last_synced_time,
                "additional_info" to model.additional_info,
                ObservationUnitModel.FK to model.observation_unit_id,
                ObservationVariableModel.FK to model.observation_variable_db_id
            )).toInt()

        } ?: -1

        /**
         * Should trait be observation_field_book_format?
         */
        fun getPlotPhotos(plot: String, trait: String): Array<String> = withDatabase { db ->

            db.query(tableName, arrayOf("value"),
                    where = "${ObservationUnitModel.FK} = ? AND observation_variable_name LIKE ?",
                    whereArgs = arrayOf(plot, trait)).toTable().mapNotNull {
                it["value"] as String
            }.toTypedArray()

        } ?: arrayOf()

        fun getUserDetail(plotId: String): Map<String, String> = withDatabase { db ->

            mapOf(*db.query(ObservationModel.tableName,
                arrayOf("observation_variable_name",
                        "observation_variable_field_book_format",
                        "value",
                        ObservationUnitModel.FK),
                where = "${ObservationUnitModel.FK} LIKE ?",
                whereArgs = arrayOf(plotId))
                .toTable().map { it["observation_variable_name"].toString() to it["value"].toString() }
                .toTypedArray())

        } ?: emptyMap()

        /*
        plotId is actually uniqueName
        parent is trait/variable name
         */
        fun getObservation(plotId: String, parent: String): Observation? = withDatabase { db ->

            Observation().apply {

                db.query(tableName,
                        arrayOf(PK, ObservationUnitModel.FK, "last_synced_time"),
                        where = "observation_variable_name LIKE ? AND ${ObservationUnitModel.FK} LIKE ?",
                        whereArgs = arrayOf(parent, plotId)).toTable().forEach {

                    dbId = it[ObservationUnitModel.FK].toString()
                    setLastSyncedTime(it["last_synced_time"].toString())
                }
            }
        }

        fun getObservationByValue(plotId: String, parent: String, value: String): Observation? = withDatabase { db ->

            Observation().apply {
                db.query(ObservationModel.tableName,
                        arrayOf("observation_db_id", "last_synced_time"),
                        where = "${ObservationUnitModel.FK} LIKE ? AND observation_variable_name LIKE ? AND value LIKE ?",
                        whereArgs = arrayOf(plotId, parent, value)).toFirst().let {
                    dbId = it["observation_db_id"].toString()
//                    lastSyncedTime = it["last_synced_time"].toString()
                }
            }
        }

        fun deleteTrait(rid: String, parent: String) = withDatabase { db ->
            db.delete(ObservationModel.tableName,
                    "observation_unit_db_id LIKE ? AND observation_variable_name LIKE ?",
                    arrayOf(rid, parent))
        }

        fun deleteTraitByValue(rid: String, parent: String, value: String) = withDatabase { db ->

            db.delete(ObservationModel.tableName,
                    "${ObservationUnitModel.FK} LIKE ? AND observation_variable_name LIKE ? AND value = ?",
                    arrayOf(rid, parent, value))
        }

        /**
         * Parameter is a list of BrAPI Observations,
         * any entry in the DB with given observation_db_id will be updated.
         */
        fun updateObservations(observations: List<Observation>) = withDatabase { db ->

            observations.forEach {

                db.update(ObservationModel.tableName,
                        ContentValues().apply {
                            put("observation_db_id", it.dbId)
                            put("last_synced_time", it.lastSyncedTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.getDefault())))
                        },
                        "$PK = ?", arrayOf(it.fieldBookDbId))

            }
        }


        fun updateImage(image: Image, writeLastSyncedTime: Boolean) {

            withDatabase {

                it.update(ObservationModel.tableName,
                        ContentValues().apply {
                            put("observation_db_id", image.dbId)
                            if (writeLastSyncedTime) {
                                put("last_synced_time", image.lastSyncedTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssZ", Locale.getDefault())))
                            }
                        },
                        "_id = ?", arrayOf(image.fieldBookDbId))

            }

        }

        /**
         * "rep" is a deprecated column that was used to save count of the number of observations
         * taken on a unit/variable pair.
         * This query replaces "rep" by finding the number of observations for a unit/variable
         */
        fun getRep(unit: ObservationUnitModel, variable: ObservationVariableModel): Int = withDatabase { db ->

            db.query(ObservationModel.tableName,
                    where = "${ObservationUnitModel.FK} = ? AND ${ObservationVariableModel.FK} = ?",
                    whereArgs = arrayOf(unit.observation_unit_db_id, variable.internal_id_observation_variable.toString()))
                    .toTable().size

        } ?: 0
    }
}