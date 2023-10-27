package com.fieldbook.tracker.database.dao

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.contentValuesOf
import com.fieldbook.tracker.brapi.model.FieldBookImage
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.Migrator.*
import com.fieldbook.tracker.database.Migrator.Companion.sLocalImageObservationsViewName
import com.fieldbook.tracker.database.Migrator.Companion.sNonImageObservationsViewName
import com.fieldbook.tracker.database.Migrator.Companion.sRemoteImageObservationsViewName
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import org.threeten.bp.OffsetDateTime
import com.fieldbook.tracker.brapi.model.Observation as BrapiObservation

class ObservationDao {

    companion object {

        fun getAll(): Array<ObservationModel> = withDatabase { db ->

            db.query(Observation.tableName)
                .toTable()
                .map { ObservationModel(it) }
                .toTypedArray()

        } ?: emptyArray()

        fun getAll(db: SQLiteDatabase): Array<ObservationModel> = db.query(Observation.tableName)
            .toTable()
            .map { ObservationModel(it) }
            .toTypedArray()

        fun getAll(studyId: String): Array<ObservationModel> = withDatabase { db ->

            db.query(Observation.tableName, where = "${Study.FK} = ?", whereArgs = arrayOf(studyId))
                .toTable()
                .map { ObservationModel(it) }
                .toTypedArray()

        } ?: emptyArray()

        fun getAll(studyId: String, obsUnit: String): Array<ObservationModel> = withDatabase { db ->

            db.query(Observation.tableName, where = "${Study.FK} = ? AND ${ObservationUnit.FK} = ?",
                whereArgs = arrayOf(studyId, obsUnit))
                .toTable()
                .map { ObservationModel(it) }
                .toTypedArray()

        } ?: emptyArray()

        fun getAll(studyId: String, obsUnit: String, traitName: String): Array<ObservationModel> = withDatabase { db ->

            db.query(Observation.tableName, where = "${Study.FK} = ? AND ${ObservationUnit.FK} = ? AND observation_variable_name = ?",
                whereArgs = arrayOf(studyId, obsUnit, traitName))
                .toTable()
                .map { ObservationModel(it) }
                .sortedBy { it.rep.toInt() }
                .toTypedArray()

        } ?: emptyArray()

        fun getAllRepeatedValues(studyId: String, obsUnit: String, traitName: String) = getAll(studyId, obsUnit, traitName)

        /**
         * Finds the minimum repeated value. With the new repeated value feature, users could potentially delete rep 1, which would break the default implementation
         * because by default we always update rep = 1
         */
        fun getDefaultRepeatedValue(studyId: String, obsUnit: String, traitName: String) = getAllRepeatedValues(studyId, obsUnit, traitName).minByOrNull { it.rep.toInt() }?.rep ?: "1"

        fun getNextRepeatedValue(studyId: String, obsUnit: String, traitName: String) = (getAllRepeatedValues(studyId, obsUnit, traitName).maxByOrNull { it.rep.toInt() }?.rep?.toInt() ?: 0) + 1

        //false warning, cursor is closed in toTable
        @SuppressLint("Recycle")
        fun getHostImageObservations(ctx: Context, hostUrl: String, missingPhoto: Bitmap): List<FieldBookImage> = withDatabase { db ->

            db.rawQuery("""
                SELECT DISTINCT props.observationUnitDbId AS uniqueName,
                       props.observationUnitName AS firstName,
                obs.${Observation.PK} AS id, 
                obs.value AS value, 
                obs.observation_time_stamp,
                obs.observation_unit_id,
                obs.observation_db_id,
                obs.last_synced_time,
                obs.collector,
                obs.rep,
                
                study.${Study.PK} AS ${Study.FK},
                study.study_alias,
                
                vars.external_db_id AS external_db_id,
                vars.observation_variable_name as observation_variable_name,
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
                
        """.trimIndent(), arrayOf(hostUrl)).toTable().mapNotNull { row ->
                if (getStringVal(row, "observation_variable_name") != null)
                    FieldBookImage(ctx, getStringVal(row, "value"), getStringVal(row, "observation_variable_name"), missingPhoto).apply {
                    rep = getStringVal(row, "rep")
                    unitDbId = getStringVal(row, "uniqueName")
                    setDescriptiveOntologyTerms(listOf(getStringVal(row, "external_db_id")))
                    setDescription(getStringVal(row, "observation_variable_details"))
                    setTimestamp(getStringVal(row, "observation_time_stamp"))
                    fieldBookDbId = getStringVal(row, "id")
                    dbId = getStringVal(row, "observation_db_id")
                    setLastSyncedTime(getStringVal(row, "last_synced_time"))
                } else null
            }

        } ?: emptyList()

        /**
         * TODO: this can be replaced with a view
         * important note:  observationUnitDbId and observationUnitName are unit attributes that
         * are required to have for brapi fields; otherwise, this query will fail.
         */
        @SuppressLint("Recycle")
        fun getObservations(hostUrl: String): List<com.fieldbook.tracker.brapi.model.Observation> = withDatabase { db ->
            db.rawQuery("""
                SELECT DISTINCT props.observationUnitDbId AS uniqueName,
                    props.observationUnitName AS firstName,
                    obs.${Observation.PK} AS id, 
                    obs.value AS value, 
                    obs.observation_time_stamp,
                    obs.observation_unit_id,
                    obs.observation_db_id,
                    obs.last_synced_time,
                    obs.collector,
                    obs.rep,
                    study.${Study.PK} AS ${Study.FK},
                    study.study_alias, 
                    vars.external_db_id AS external_db_id,
                    vars.observation_variable_name as observation_variable_name, 
                    vars.observation_variable_details,
                    vars.observation_variable_field_book_format as observation_variable_field_book_format
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
                    .map { row -> com.fieldbook.tracker.brapi.model.Observation().apply {
                        rep = getStringVal(row, "rep")
                        unitDbId = getStringVal(row, "uniqueName")
                        variableDbId = getStringVal(row, "external_db_id")
                        value = CategoryJsonUtil.processValue(row)
                        variableName = getStringVal(row, "observation_variable_name")
                        fieldBookDbId = getStringVal(row, "id")
                        dbId = getStringVal(row, "observation_db_id")
                        setTimestamp(getStringVal(row, "observation_time_stamp"))
                        setLastSyncedTime(getStringVal(row, "last_synced_time"))
                        collector = getStringVal(row, "collector")
                        //study alias is the actual brapi private key
                        studyId = getStringVal(row, "study_alias")
                    } }

        } ?: emptyList()

        private fun getStringVal(row: Map<String, Any?>?, column: String?) : String? {
            if(row != null && column != null){
                if (row[column] != null){
                    return row[column].toString()
                }
            }
            return null
        }

        fun getWrongSourceImageObservations(ctx: Context, hostUrl: String, missingPhoto: Bitmap): List<FieldBookImage> = withDatabase { db ->

            db.query(sRemoteImageObservationsViewName, where = "trait_data_source <> ?", whereArgs = arrayOf(hostUrl)).toTable()
                    .mapNotNull { row -> if (getStringVal(row, "observation_variable_name") != null)
                        FieldBookImage(ctx, getStringVal(row, "value"), getStringVal(row, "observation_variable_name"), missingPhoto).apply {
                        this.fieldBookDbId = getStringVal(row, "id")
                        } else null
                    }

        } ?: emptyList()

        /**
         * Original query joins observations that are remote but do not match the hostUrl and are not images.
         */
        fun getWrongSourceObservations(hostUrl: String): List<com.fieldbook.tracker.brapi.model.Observation> = withDatabase { db ->

            db.query(sNonImageObservationsViewName,
                    where = "trait_data_source <> ? AND trait_data_source <> 'local' AND trait_data_source IS NOT NULL",
                    whereArgs = arrayOf(hostUrl)).toTable()
                    .map { row -> com.fieldbook.tracker.brapi.model.Observation().apply {
                        this.fieldBookDbId = getStringVal(row, "id")
                        this.value = CategoryJsonUtil.processValue(row)
                    } }

        } ?: emptyList()

        fun getUserTraitImageObservations(ctx: Context, expId: String, missingPhoto: Bitmap): List<FieldBookImage> = withDatabase { db ->

            db.query(sLocalImageObservationsViewName, where = "${Study.FK} = ?", whereArgs = arrayOf(expId)).toTable()
                    .mapNotNull { row ->
                        if (getStringVal(row, "observation_variable_name") != null)
                            FieldBookImage(ctx,
                                getStringVal(row, "value"),
                                getStringVal(row, "observation_variable_name"),
                                missingPhoto).apply {
                            this.fieldBookDbId = getStringVal(row, "id")
                        } else null
                    }

        } ?: emptyList()

        fun getUserTraitObservations(expId: String): List<com.fieldbook.tracker.brapi.model.Observation> = withDatabase { db ->

            db.query(sNonImageObservationsViewName,
                    // TODO change study_db_id to match ${Study.FK} in db
                    where = "study_db_id = ? AND (trait_data_source = 'local' OR trait_data_source IS NULL)", whereArgs = arrayOf(expId)).toTable()
                    .map { row -> com.fieldbook.tracker.brapi.model.Observation().apply {
                        this.fieldBookDbId = getStringVal(row, "id")
                        this.value = CategoryJsonUtil.processValue(row)
                    } }

        } ?: emptyList()

        /**
         * This function is used in the Collect activity.
         * Brapi observations have an extra lastTimeSynced field that is compared with the observed time stamp.
         * If the observation is synced or edited the value is replaced with NA and a warning is shown.
         */
        fun isBrapiSynced(exp_id: String, rid: String, parent: String, rep: String): Boolean = withDatabase {

            getObservation(exp_id, rid, parent, rep)?.let { observation ->

                observation.status in arrayOf(
                    com.fieldbook.tracker.brapi.model.BrapiObservation.Status.SYNCED,
                    com.fieldbook.tracker.brapi.model.BrapiObservation.Status.EDITED)

            }

        } ?: false

        /**
         * In this case parent is the variable name and trait is the format
         */
        fun insertObservation(rid: String, parent: String, trait: String,
                              userValue: String, person: String, location: String,
                              notes: String, studyId: String, observationDbId: String?,
                              lastSyncedTime: OffsetDateTime?,
                              rep: String? = (getRep(studyId, rid, parent) + 1).toString()): Long = withDatabase { db ->

            val traitObj = ObservationVariableDao.getTraitByName(parent)
            val internalTraitId = if (traitObj == null) {-1} else {traitObj.id}

            val timestamp = try {
                OffsetDateTime.now().format(internalTimeFormatter)
            } catch (e: Exception) { //ZoneRulesException
                String()
            }

            db.insert(Observation.tableName, null, contentValuesOf(
                    "observation_variable_name" to parent,
                    "observation_db_id" to observationDbId,
                    "observation_variable_field_book_format" to trait,
                    "value" to userValue,
                    "observation_time_stamp" to timestamp,
                    "collector" to person,
                    "geoCoordinates" to location,
                    "last_synced_time" to lastSyncedTime?.format(internalTimeFormatter),
                    "rep" to rep,
                    "notes" to notes,
                    Study.FK to studyId.toInt(),
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

        fun insertObservation(expId:Int, model: BrapiObservation): Int = withDatabase { db ->
            //         * @param exp_id the field identifier
            //         * @param plotId the unique name of the currently selected field
            //         * @param parent the variable name of the observation
//            val obs = getObservation("$expId", model.unitDbId,model.variableName)
//            println("**************************")
//            println("FAIL Season: ${obs?.season}")
//            println("FAIL studyId: ${obs?.studyId}")
//            println("FAIL Value: ${obs?.value}")
//            println("FAIL unitId: ${obs?.unitDbId}")
//            println("FAIL VariableDbId: ${obs?.variableDbId}")
//            println("FAIL DbId: ${obs?.dbId}")
//            println("FAIL FieldbookDbId: ${obs?.fieldbookDbId}")
//            println("FAIL VariableName: ${obs?.variableName}")
//            println("**************************")

            if(getObservation("$expId", model.unitDbId,model.variableName, "1")?.dbId != null)  {

//                println("**************************")
//                println("FAIL Value: ${obs?.value}")
//                println("FAIL VariableDbId: ${obs?.variableDbId}")
//                println("FAIL VariableName: ${obs?.variableName}")
//                println("FAIL UnitDbId: ${obs?.unitDbId}")
//                println("**************************")

                println("DbId: ${getObservation("$expId", model.unitDbId,model.variableName, "1")?.dbId}")
                -1
            }
            else {
                val varRowId =  db.insert(Observation.tableName, null, contentValuesOf(
                    "observation_variable_name" to model.variableName,
//                "observation_variable_field_book_format" to model.observation_variable_field_book_format,
                    "observation_variable_field_book_format" to null,
                    "value" to model.value,
                    "observation_time_stamp" to model.timestamp,
                    "collector" to model.collector,
//                "geoCoordinates" to model.geo_coordinates,
                    "geoCoordinates" to null,
                    "last_synced_time" to model.lastSyncedTime,
//                "additional_info" to model.additional_info,
                    "additional_info" to null,
//                Study.FK to model.studyId,
                    "observation_db_id" to model.dbId,
                    Study.FK to expId,
                    ObservationUnit.FK to model.unitDbId,
                    ObservationVariable.FK to model.variableDbId
                )).toInt()
                varRowId
            }

        } ?: -1

        /**
         * Should trait be observation_field_book_format?
         */
        fun getPlotPhotos(expId: String, plot: String, trait: String): ArrayList<Uri> = withDatabase { db ->

            ArrayList(db.query(Observation.tableName, arrayOf("value"),
                    where = "${Study.FK} = ? AND ${ObservationUnit.FK} = ? AND observation_variable_name LIKE ?",
                    whereArgs = arrayOf(expId, plot, trait)).toTable().map {
                Uri.parse(it["value"] as String)
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
                    .toTable().map { (it["observation_variable_name"] as? String ?: "") to it["value"].toString() }
                    .toTypedArray())

        } ?: hashMapOf()

        /**
         * Should be used for observations imported via BrAPI.
         * This function builds a BrAPI observation that has a specific last synced time field.
         *
         * @param exp_id the field identifier
         * @param plotId the unique name of the currently selected field
         * @param parent the variable name of the observation
         */
        fun getObservation(exp_id: String, plotId: String, parent: String, rep: String): BrapiObservation? = withDatabase { db ->

            BrapiObservation().apply {

                db.query(Observation.tableName,
                        arrayOf(Observation.PK, ObservationUnit.FK, "observation_db_id", "observation_time_stamp", "last_synced_time"),
                        where = "${Study.FK} = ? AND observation_variable_name LIKE ? AND ${ObservationUnit.FK} LIKE ? AND rep LIKE ?",
                        whereArgs = arrayOf(exp_id, parent, plotId, rep)).toTable().forEach {

                    dbId = getStringVal(it, "observation_db_id")
                    unitDbId = getStringVal(it, ObservationUnit.FK)
                    setRep((it["rep"] ?: "1").toString())
                    setTimestamp(getStringVal(it, "observation_time_stamp"))
                    setLastSyncedTime(getStringVal(it,"last_synced_time"))
                }
            }
        }

        fun getObservationByValue(expId: String, plotId: String, parent: String, value: String): BrapiObservation? = withDatabase { db ->

            BrapiObservation().apply {
                db.query(Observation.tableName,
                        arrayOf("observation_db_id", "last_synced_time"),
                        where = "${Study.FK} = ? AND ${ObservationUnit.FK} LIKE ? AND observation_variable_name LIKE ? AND value LIKE ?",
                        whereArgs = arrayOf(expId, plotId, parent, value)).toFirst().let {
                    dbId = getStringVal(it, "observation_db_id")
                    setLastSyncedTime(getStringVal(it,"last_synced_time"))
                }
            }
        }

        /**
         * Pattern match to find observation values that contain a Uri
         */
        fun getObservationByValue(value: String): ObservationModel? = withDatabase { db ->

            ObservationModel(db.query(Observation.tableName,
                where = "value LIKE ?",
                whereArgs = arrayOf("%$value")).toFirst())
        }

        /**
         * Deletes all observations for a given variable on a plot.
         * @param id: the study id
         * @param rid: the unique plot name
         * @param parent: the observation variable (trait) name
         * @param rep: the repeated obs. index
         */
        fun deleteTrait(id: String, rid: String, parent: String, rep: String) = withDatabase { db ->
            db.delete(Observation.tableName,
                    "${Study.FK} = ? AND ${ObservationUnit.FK} LIKE ? AND observation_variable_name LIKE ? AND rep LIKE ?",
                    arrayOf(id, rid, parent, rep))
        }

        fun deleteTraitByValue(expId: String, rid: String, parent: String, value: String) = withDatabase { db ->

            db.delete(Observation.tableName,
                    "${Study.FK} = ? AND ${ObservationUnit.FK} LIKE ? AND observation_variable_name LIKE ? AND value = ?",
                    arrayOf(expId, rid, parent, value))
        }

        fun updateObservationModels(observations: List<ObservationModel>) = withDatabase { db ->

            observations.forEach {

                db.update(Observation.tableName,
                    ContentValues().apply {
                        put(Observation.PK, it.internal_id_observation)
                        put("value", it.value)
                    },
                    "${Observation.PK} = ?", arrayOf(it.internal_id_observation.toString()))

            }
        }

        fun updateObservationModels(db: SQLiteDatabase, observations: List<ObservationModel>) {

            observations.forEach {

                db.update(Observation.tableName,
                    ContentValues().apply {
                        put(Observation.PK, it.internal_id_observation)
                        put("value", it.value)
                    },
                    "${Observation.PK} = ?", arrayOf(it.internal_id_observation.toString()))

            }
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
                            put("last_synced_time", it.lastSyncedTime.format(internalTimeFormatter))
                        },
                        "${Observation.PK} = ?", arrayOf(it.fieldBookDbId))

            }
        }

        fun updateObservation(observation: ObservationModel) = withDatabase { db ->

            db.update(Observation.tableName,
                contentValuesOf(*observation.map.map { it.key to it.value }.toTypedArray()),
                "internal_id_observation = ?",
                arrayOf(observation.internal_id_observation.toString())
                )
        }

        //TODO
        fun updateImage(image: FieldBookImage, writeLastSyncedTime: Boolean) = withDatabase {

            it.update(Observation.tableName,

                    ContentValues().apply {
                        put("observation_db_id", image.dbId)
                        if (writeLastSyncedTime) {
                            put("last_synced_time", image.lastSyncedTime.format(internalTimeFormatter))
                        }
                    },
                    Observation.PK + " = ?", arrayOf(image.fieldBookDbId))

        }



        /**
         * Returns the integer value of rep for the latest repeated value.
         * Rep always starts at 1.
         * In this case parent is the variable name and rid is the observation unit id
         */
        fun getRep(studyId: String, rid: String, parent: String): Int = withDatabase { db ->

            db.query(Observation.tableName,
                    where = "${Study.FK} = ? AND ${ObservationUnit.FK} = ? AND observation_variable_name = ?",
                    whereArgs = arrayOf(studyId, rid, parent))
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