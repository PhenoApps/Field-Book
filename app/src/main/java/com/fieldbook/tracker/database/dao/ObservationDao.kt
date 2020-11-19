package com.fieldbook.tracker.database.dao

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import com.fieldbook.tracker.brapi.Image
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.database.models.ObservationUnitModel
import com.fieldbook.tracker.database.models.ObservationVariableModel
import org.threeten.bp.format.DateTimeFormatter
import java.util.*
import com.fieldbook.tracker.brapi.Observation as BrapiObservation
import com.fieldbook.tracker.database.Migrator.Observation
import com.fieldbook.tracker.database.Migrator.ObservationUnit
import com.fieldbook.tracker.database.Migrator.ObservationVariable
import org.threeten.bp.OffsetDateTime
import kotlin.math.exp

class ObservationDao {

    companion object {

        fun getAll(): Array<ObservationModel>? = withDatabase { db ->

            db.query(Observation.tableName)
                .toTable()
                .map { ObservationModel(it) }
                .toTypedArray()

        } ?: emptyArray()

        /**
         * In this case parent is the variable name and trait is the format
         */
        fun insertUserTraits(rid: String, parent: String, trait: String,
                             userValue: String, person: String, location: String,
                             notes: String, exp_id: String, observationDbId: String?,
                             lastSyncedTime: OffsetDateTime?): Long = withDatabase { db ->

            val interalTraitId = ObservationVariableDao.getTraitId(parent);

            db.insert(Observation.tableName, null, contentValuesOf(
                    "observation_variable_name" to parent,
                    "observation_variable_field_book_format" to trait,
                    "value" to userValue,
                   // "observation_time_stamp" to model.observation_time_stamp,
                    "collector" to person,
                    "geoCoordinates" to location,
                    "study_db_id" to exp_id,
                    "last_synced_time" to lastSyncedTime,
                   // "additional_info" to model.additional_info,
                    ObservationUnit.FK to rid,
                    ObservationVariable.FK to interalTraitId
            ))

        } ?: -1L

        fun insertObservation(model: ObservationModel): Int = withDatabase { db ->

            db.insert(Observation.tableName, null, contentValuesOf(
                "observation_variable_name" to model.observation_variable_name,
                "observation_variable_field_book_format" to model.observation_variable_field_book_format,
                "value" to model.value,
                "observation_time_stamp" to model.observation_time_stamp,
                "collector" to model.collector,
                "geoCoordinates" to model.geo_coordinates,
                "study_db_id" to model.study_db_id,
                "last_synced_time" to model.last_synced_time,
                "additional_info" to model.additional_info,
                ObservationUnit.FK to model.observation_unit_id,
                ObservationVariable.FK to model.observation_variable_db_id
            )).toInt()

        } ?: -1

        /**
         * Should trait be observation_field_book_format?
         */
        fun getPlotPhotos(plot: String, trait: String): Array<String> = withDatabase { db ->

            db.query(Observation.tableName, arrayOf("value"),
                    where = "${ObservationUnit.FK} = ? AND observation_variable_name LIKE ?",
                    whereArgs = arrayOf(plot, trait)).toTable().map {
                it["value"] as String
            }.toTypedArray()

        } ?: arrayOf()

        fun getUserDetail(plotId: String): HashMap<String, String> = withDatabase { db ->

            hashMapOf(*db.query(Observation.tableName,
                arrayOf("observation_variable_name",
                        "observation_variable_field_book_format",
                        "value",
                        ObservationUnit.FK),
                where = "${ObservationUnit.FK} LIKE ?",
                whereArgs = arrayOf(plotId))
                .toTable().map { it["observation_variable_name"].toString() to it["value"].toString() }
                .toTypedArray())

        } ?: hashMapOf()

        /*
        plotId is actually uniqueName
        parent is trait/variable name
         */
        fun getObservation(plotId: String, parent: String): BrapiObservation? = withDatabase { db ->

            BrapiObservation().apply {

                db.query(Observation.tableName,
                        arrayOf(Observation.PK, ObservationUnit.FK, "last_synced_time"),
                        where = "observation_variable_name LIKE ? AND ${ObservationUnit.FK} LIKE ?",
                        whereArgs = arrayOf(parent, plotId)).toTable().forEach {

                    dbId = it[ObservationUnit.FK].toString()
                    setLastSyncedTime(it["last_synced_time"].toString())
                }
            }
        }

        fun getObservationByValue(plotId: String, parent: String, value: String): BrapiObservation? = withDatabase { db ->

            BrapiObservation().apply {
                db.query(Observation.tableName,
                        arrayOf("observation_db_id", "last_synced_time"),
                        where = "${ObservationUnit.FK} LIKE ? AND observation_variable_name LIKE ? AND value LIKE ?",
                        whereArgs = arrayOf(plotId, parent, value)).toFirst().let {
                    dbId = it["observation_db_id"].toString()
//                    lastSyncedTime = it["last_synced_time"].toString()
                }
            }
        }

        fun deleteTrait(rid: String, parent: String) = withDatabase { db ->
            db.delete(Observation.tableName,
                    "observation_unit_db_id LIKE ? AND observation_variable_name LIKE ?",
                    arrayOf(rid, parent))
        }

        fun deleteTraitByValue(rid: String, parent: String, value: String) = withDatabase { db ->

            db.delete(Observation.tableName,
                    "${ObservationUnit.FK} LIKE ? AND observation_variable_name LIKE ? AND value = ?",
                    arrayOf(rid, parent, value))
        }

        /**
         * Parameter is a list of BrAPI Observations,
         * any entry in the DB with given observation_db_id will be updated.
         */
        fun updateObservations(observations: List<BrapiObservation>) = withDatabase { db ->

            observations.forEach {

                db.update(Observation.tableName,
                        ContentValues().apply {
                            put("observation_db_id", it.dbId)
                            put("last_synced_time", it.lastSyncedTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ", Locale.getDefault())))
                        },
                        "${Observation.PK} = ?", arrayOf(it.fieldBookDbId))

            }
        }


        //TODO
        fun updateImage(image: Image, writeLastSyncedTime: Boolean) {

            withDatabase {

                it.update(Observation.tableName,

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

            db.query(Observation.tableName,
                    where = "${ObservationUnit.FK} = ? AND ${ObservationVariable.FK} = ?",
                    whereArgs = arrayOf(unit.observation_unit_db_id, variable.internal_id_observation_variable.toString()))
                    .toTable().size

        } ?: 0

        /**
         * Unit -> plot/unit internal id
         * variable name -> trait/variable readable name
         * variable -> trait/variable db id
         * format -> trait/variable observation_field_book_format
         */
        fun randomObservation(unit: String, uniqueName: String, format: String, variableName: String, variable: String): ObservationModel = ObservationModel(mapOf(
                "observation_variable_name" to variableName,
                "observation_variable_field_book_format" to format,
                "value" to UUID.randomUUID().toString(),
                "observation_time_stamp" to "2019-10-15 12:14:590-0400",
                "collector" to UUID.randomUUID().toString(),
                "geo_coordinates" to UUID.randomUUID().toString(),
                "observation_db_id" to uniqueName,
                "last_synced_time" to "2019-10-15 12:14:590-0400",
                "additional_info" to UUID.randomUUID().toString(),
                Migrator.Study.FK to UUID.randomUUID().toString(),
                ObservationUnit.FK to unit.toInt(),
                ObservationVariable.FK to variable.toInt()))

        /**
         * Inserts ten random observations per plot/trait pairs.
         */
        private fun insertRandomObservations() {

            ObservationUnitDao.getAll().sliceArray(0 until 10).forEachIndexed { index, unit ->

                ObservationVariableDao.getAllTraitObjects().forEachIndexed { index, traitObject ->

                    for (i in 0..10) {

                        val obs = randomObservation(
                                unit.internal_id_observation_unit.toString(),
                                unit.observation_unit_db_id,
                                traitObject.format,
                                traitObject.trait,
                                traitObject.id)

                        val newRowid = insertObservation(obs)

//                println(newRowid)
//                    println("Inserting $newRowid $rowid")
                    }
                }
            }
        }
    }
}