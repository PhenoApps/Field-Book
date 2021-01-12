package com.fieldbook.tracker.database.dao

import android.annotation.SuppressLint
import android.content.ContentValues
import android.graphics.Bitmap
import androidx.core.content.contentValuesOf
import com.fieldbook.tracker.brapi.Image
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.Migrator.*
import com.fieldbook.tracker.database.Migrator.Companion.sLocalImageObservationsViewName
import com.fieldbook.tracker.database.Migrator.Companion.sNonImageObservationsViewName
import com.fieldbook.tracker.database.Migrator.Companion.sRemoteImageObservationsViewName
import com.fieldbook.tracker.database.models.ObservationModel
import org.threeten.bp.OffsetDateTime
import org.threeten.bp.format.DateTimeFormatter
import java.util.*
import com.fieldbook.tracker.brapi.Observation as BrapiObservation

class ObservationDao {

    companion object {

//        fun getAll(): Array<ObservationModel> = withDatabase { db ->
//
//            db.query(Observation.tableName)
//                .toTable()
//                .map { ObservationModel(it) }
//                .toTypedArray()
//
//        } ?: emptyArray()

        //false warning, cursor is closed in toTable
        @SuppressLint("Recycle")
        fun getHostImageObservations(hostUrl: String, missingPhoto: Bitmap): List<Image> = withDatabase { db ->

            db.rawQuery("""
                SELECT props.observationUnitDbId AS uniqueName,
                       props.observationUnitName AS firstName,
                obs.${Observation.PK} AS id, 
                obs.value AS value, 
                obs.observation_time_stamp,
                obs.observation_unit_id,
                obs.observation_db_id,
                obs.last_synced_time,
                obs.collector,
                
                study.${Study.PK} AS ${Study.FK},
                study.study_alias,
                
                vars.external_db_id AS external_db_id,
                vars.observation_variable_name as observation_variable_name
                vars.observation_variable_details
                
            FROM ${Observation.tableName} AS obs
            JOIN ${Migrator.sObservationUnitPropertyViewName} AS props ON obs.observation_unit_id = props.observationUnitDbId
            JOIN ${ObservationVariable.tableName} AS vars ON obs.${ObservationVariable.FK} = vars.${ObservationVariable.PK} 
            JOIN ${Study.tableName} AS study ON obs.${Study.FK} = study.${Study.PK}
           
            WHERE study.study_source IS NOT NULL
                AND obs.value <> ''
                AND vars.trait_data_source = ?
                AND vars.trait_data_source IS NOT NULL
                AND vars.observation_variable_field_book_format = 'photo'
                
        """.trimIndent(), arrayOf(hostUrl)).toTable()
                    .map { row -> Image(row["value"].toString(), missingPhoto).apply {
                        unitDbId = row["uniqueName"].toString()
                        setDescriptiveOntologyTerms(listOf(row["firstName"].toString()))
                        setDescription(row["observation_variable_details"].toString())
                        setTimestamp(row["observation_time_stamp"].toString())
                        fieldBookDbId = row["id"].toString()
                        dbId = row["observation_db_id"].toString()
                        setLastSyncedTime(row["last_synced_time"].toString())
                    } }

        } ?: emptyList()

        /**
         * TODO: this can be replaced with a view
         * important note:  observationUnitDbId and observationUnitName are unit attributes that
         * are required to have for brapi fields; otherwise, this query will fail.
         */
        @SuppressLint("Recycle")
        fun getObservations(hostUrl: String): List<com.fieldbook.tracker.brapi.Observation> = withDatabase { db ->

            db.rawQuery("""
                SELECT props.observationUnitDbId AS uniqueName,
                    props.observationUnitName AS firstName,
                    obs.${Observation.PK} AS id, 
                    obs.value AS value, 
                    obs.observation_time_stamp,
                    obs.observation_unit_id,
                    obs.observation_db_id,
                    obs.last_synced_time,
                    obs.collector,
                
                study.${Study.PK} AS ${Study.FK},
                study.study_alias,
                
                vars.external_db_id AS external_db_id,
                vars.observation_variable_name as observation_variable_name
                vars.observation_variable_details
                
            FROM ${Observation.tableName} AS obs
            JOIN ${Migrator.sObservationUnitPropertyViewName} AS props ON obs.observation_unit_id = props.observationUnitDbId
            JOIN ${ObservationVariable.tableName} AS vars ON obs.${ObservationVariable.FK} = vars.${ObservationVariable.PK} 
            JOIN ${Study.tableName} AS study ON obs.${Study.FK} = study.${Study.PK}
           
            WHERE study.study_source IS NOT NULL
                AND obs.value <> ''
                AND vars.trait_data_source = ?
                AND vars.trait_data_source IS NOT NULL
                AND vars.observation_variable_field_book_format <> 'photo'
                
        """.trimIndent(), arrayOf(hostUrl)).toTable()
                    .map { row -> com.fieldbook.tracker.brapi.Observation().apply {
                        unitDbId = row["uniqueName"].toString()
                        variableDbId = row["external_db_id"].toString()
                        value = row["value"].toString()
                        variableName = row["observation_variable_name"].toString()
                        fieldBookDbId = row["id"].toString()
                        dbId = row["observation_db_id"].toString()
                        setTimestamp(row["observation_time_stamp"].toString())
                        setLastSyncedTime(row["last_synced_time"].toString())
                        setCollector(row["collector"].toString())
                        setStudyId(row[Study.FK].toString())
                    } }

        } ?: emptyList()

        fun getWrongSourceImageObservations(hostUrl: String, missingPhoto: Bitmap): List<Image> = withDatabase { db ->

            db.query(sRemoteImageObservationsViewName, where = "trait_data_source <> ?", whereArgs = arrayOf(hostUrl)).toTable()
                    .map { row -> Image(row["value"].toString(), missingPhoto).apply {
                        this.fieldBookDbId = row["id"].toString()
                    } }

        } ?: emptyList()

        /**
         * Original query joins observations that are remote but do not match the hostUrl and are not images.
         */
        fun getWrongSourceObservations(hostUrl: String): List<com.fieldbook.tracker.brapi.Observation> = withDatabase { db ->

            db.query(sNonImageObservationsViewName,
                    where = "trait_data_source <> ? AND trait_data_source <> 'local' AND trait_data_source IS NOT NULL",
                    whereArgs = arrayOf(hostUrl)).toTable()
                    .map { row -> com.fieldbook.tracker.brapi.Observation().apply {
                        this.fieldBookDbId = row["id"].toString()
                        this.value = row["value"].toString()
                    } }

        } ?: emptyList()

        fun getUserTraitImageObservations(expId: String, missingPhoto: Bitmap): List<Image> = withDatabase { db ->

            db.query(sLocalImageObservationsViewName, where = "${Study.FK} = ?", whereArgs = arrayOf(expId)).toTable()
                    .map { row -> Image(row["value"].toString(), missingPhoto).apply {
                        this.fieldBookDbId = row["id"].toString()
                    } }

        } ?: emptyList()

        fun getUserTraitObservations(expId: String): List<com.fieldbook.tracker.brapi.Observation> = withDatabase { db ->

            db.query(sNonImageObservationsViewName,
                    where = "${Study.FK} = ? AND (trait_data_source = 'local' OR trait_data_source IS NULL)", whereArgs = arrayOf(expId)).toTable()
                    .map { row -> com.fieldbook.tracker.brapi.Observation().apply {
                        this.fieldBookDbId = row["id"].toString()
                        this.value = row["value"].toString()
                    } }

        } ?: emptyList()

        fun isBrapiSynced(rid: String, parent: String): Boolean = withDatabase { db ->

            getObservation(rid, parent)?.let { observation ->

                observation.status in arrayOf(
                        com.fieldbook.tracker.brapi.BrapiObservation.Status.SYNCED,
                        com.fieldbook.tracker.brapi.BrapiObservation.Status.EDITED)


            }

            false

        } ?: false
        /**
         * In this case parent is the variable name and trait is the format
         */
        fun insertUserTraits(rid: String, parent: String, trait: String,
                             userValue: String, person: String, location: String,
                             notes: String, exp_id: String, observationDbId: String?,
                             lastSyncedTime: OffsetDateTime?): Long = withDatabase { db ->

            val rep = getRep(rid, parent) + 1

            val internalTraitId = ObservationVariableDao.getTraitId(parent)

            db.insert(Observation.tableName, null, contentValuesOf(
                    "observation_variable_name" to parent,
                    "observation_variable_field_book_format" to trait,
                    "value" to userValue,
                    "observation_time_stamp" to OffsetDateTime.now().format(internalTimeFormatter),
                    "collector" to person,
                    "geoCoordinates" to location,
                    "last_synced_time" to lastSyncedTime,
                    "rep" to rep,
                    "notes" to notes,
                    Study.FK to exp_id.toInt(),
                    // "additional_info" to model.additional_info,
                    ObservationUnit.FK to rid,
                    ObservationVariable.FK to internalTraitId
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
                    "last_synced_time" to model.last_synced_time,
                    "additional_info" to model.additional_info,
                    Study.FK to model.study_id,
                    ObservationUnit.FK to model.observation_unit_id,
                    ObservationVariable.FK to model.observation_variable_db_id
            )).toInt()

        } ?: -1

        /**
         * Should trait be observation_field_book_format?
         */
        fun getPlotPhotos(plot: String, trait: String): ArrayList<String> = withDatabase { db ->

            ArrayList(db.query(Observation.tableName, arrayOf("value"),
                    where = "${ObservationUnit.FK} = ? AND observation_variable_name LIKE ?",
                    whereArgs = arrayOf(plot, trait)).toTable().map {
                it["value"] as String
            })

        } ?: arrayListOf()

        fun getUserDetail(expId: String, plotId: String): HashMap<String, String> = withDatabase { db ->

            hashMapOf(*db.query(Observation.tableName,
                    arrayOf("observation_variable_name",
                            "observation_variable_field_book_format",
                            "value",
                            ObservationUnit.FK),
                    where = "${ObservationUnit.FK} LIKE ? AND ${Study.FK} LIKE ?",
                    whereArgs = arrayOf(plotId, expId))
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
                    "${ObservationUnit.FK} LIKE ? AND observation_variable_name LIKE ?",
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
         * In this case parent is the variable name and rid is the observation unit id
         */
        fun getRep(rid: String, parent: String): Int = withDatabase { db ->

            db.query(Observation.tableName,
                    where = "${ObservationUnit.FK} = ? AND observation_variable_name = ?",
                    whereArgs = arrayOf(rid, parent))
                    .toTable().size

        } ?: 0

        /**
         * Unit -> plot/unit internal id
         * variable name -> trait/variable readable name
         * variable -> trait/variable db id
         * format -> trait/variable observation_field_book_format
         */
//        fun randomObservation(unit: String, uniqueName: String, format: String, variableName: String, variable: String): ObservationModel = ObservationModel(mapOf(
//                "observation_variable_name" to variableName,
//                "observation_variable_field_book_format" to format,
//                "value" to UUID.randomUUID().toString(),
//                "observation_time_stamp" to "2019-10-15 12:14:590-0400",
//                "collector" to UUID.randomUUID().toString(),
//                "geo_coordinates" to UUID.randomUUID().toString(),
//                "observation_db_id" to uniqueName,
//                "last_synced_time" to "2019-10-15 12:14:590-0400",
//                "additional_info" to UUID.randomUUID().toString(),
//                Study.FK to UUID.randomUUID().toString(),
//                ObservationUnit.FK to unit.toInt(),
//                ObservationVariable.FK to variable.toInt()))

        /**
         * Inserts ten random observations per plot/trait pairs.
         */
//        private fun insertRandomObservations() {
//
//            ObservationUnitDao.getAll().sliceArray(0 until 10).forEach { unit ->
//
//                ObservationVariableDao.getAllTraitObjects().forEach { traitObject ->
//
//                    for (i in 0..10) {
//
//                        val obs = randomObservation(
//                                unit.internal_id_observation_unit.toString(),
//                                unit.observation_unit_db_id,
//                                traitObject.format,
//                                traitObject.trait,
//                                traitObject.id)
//
//                        insertObservation(obs)
//
////                println(newRowid)
////                    println("Inserting $newRowid $rowid")
//                    }
//                }
//            }
//        }
    }
}