package com.fieldbook.tracker.database.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.core.content.contentValuesOf
import com.fieldbook.tracker.database.Migrator.Companion.sObservationUnitPropertyViewName
import com.fieldbook.tracker.database.Migrator.Observation
import com.fieldbook.tracker.database.Migrator.ObservationUnit
import com.fieldbook.tracker.database.Migrator.ObservationUnitAttribute
import com.fieldbook.tracker.database.Migrator.ObservationUnitValue
import com.fieldbook.tracker.database.Migrator.Study
import com.fieldbook.tracker.database.getTime
import com.fieldbook.tracker.database.models.StudyModel
import com.fieldbook.tracker.database.query
import com.fieldbook.tracker.database.toFirst
import com.fieldbook.tracker.database.withDatabase
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.ImportFormat
import com.fieldbook.tracker.utilities.CategoryJsonUtil


class StudyDao {

    companion object {

        fun getPossibleUniqueAttributes(studyId: Int): List<String> = withDatabase { db ->
            val query = """
                SELECT observation_unit_attribute_name 
                FROM observation_units_attributes 
                WHERE internal_id_observation_unit_attribute IN (
                    SELECT observation_unit_attribute_db_id
                    FROM observation_units_values
                    WHERE study_id = ?
                    GROUP BY observation_unit_attribute_db_id
                    HAVING COUNT(DISTINCT observation_unit_value_name) = COUNT(observation_unit_value_name)
                )
            """

            Log.d("StudyDao", "Running query: $query")

            db.rawQuery(query, arrayOf(studyId.toString())).use { cursor ->
                val attributes = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let {
                         attributes.add(it)
                         Log.d("StudyDao", "Found unique attribute: $it")
                    }
                }
                attributes
            }
        } ?: emptyList()

        /**
        * Updates the observation unit search attribute for a study record.
        * This attribute is used to identify entries in the barcode search, by default it's the same as the unique_id
        */

        fun updateSearchAttribute(studyId: Int, newSearchAttribute: String) = withDatabase { db ->
            db.update(Study.tableName,
                contentValuesOf("observation_unit_search_attribute" to newSearchAttribute),
                "${Study.PK} = $studyId",
                null
            )
        }

        /**
        * Updates the observation unit search attribute for all studies that have this attribute
        * @return The number of studies that were updated
        */
        fun updateSearchAttributeForAllFields(newSearchAttribute: String): Int = withDatabase { db ->
            // First check which studies have this attribute
            val studiesWithAttribute = mutableListOf<Int>()

            val query = """
                SELECT DISTINCT study_id 
                FROM observation_units_attributes 
                WHERE observation_unit_attribute_name = ?
            """

            Log.d("StudyDao", "Finding studies with attribute: $newSearchAttribute")

            db.rawQuery(query, arrayOf(newSearchAttribute)).use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getInt(0).let { studyId ->
                        studiesWithAttribute.add(studyId)
                        Log.d("StudyDao", "Found study with matching attribute: $studyId")
                    }
                }
            }

            // Now update each study that has this attribute
            var updatedCount = 0
            if (studiesWithAttribute.isNotEmpty()) {
                for (studyId in studiesWithAttribute) {
                    // Fix: Add null safety with the Elvis operator (?:)
                    val result = updateSearchAttribute(studyId, newSearchAttribute)
                    if ((result ?: 0) > 0) updatedCount++
                }
            }

            Log.d("StudyDao", "Updated search attribute for $updatedCount studies")
            updatedCount
        } ?: 0

        private fun fixPlotAttributes(db: SQLiteDatabase) {

            db.rawQuery("PRAGMA foreign_keys=OFF;", null).close()

            try {

                db.execSQL("""
                insert or replace into observation_units_attributes (internal_id_observation_unit_attribute, observation_unit_attribute_name, study_id)
                select attribute_id as internal_id_observation_unit_attribute, attribute_name as observation_unit_attribute_name, exp_id as study_id
                from plot_attributes as p
            """.trimIndent())

            } catch (e: Exception) {

                e.printStackTrace()

            }

            db.rawQuery("PRAGMA foreign_keys=ON;", null).close()
        }

        /**
         * Transpose obs. unit. attribute/values into a view based on the selected study.
         * On further testing, creating a view here is substantially faster but queries on the
         * view are ~4x the runtime as using a table.
         */
        fun switchField(exp_id: Int) = withDatabase { db ->

            val headers = ObservationUnitAttributeDao.getAllNames(exp_id).filter { it != "geo_coordinates" }

            fixPlotAttributes(db)

            //create a select statement based on the saved plot attribute names
            val select = headers.map { col ->

                "MAX(CASE WHEN attr.observation_unit_attribute_name = \"$col\" THEN vals.observation_unit_value_name ELSE NULL END) AS \"$col\""

            }

            //combine the case statements with commas
            val selectStatement = if (select.isNotEmpty()) {
                select.joinToString(", ") + ", "
            } else ""

            /**
             * Creating a view here is faster, but
             * using a table gives better performance for getRangeByIdAndPlot query
             */
            val query = """
            CREATE TABLE IF NOT EXISTS $sObservationUnitPropertyViewName AS 
            SELECT $selectStatement units.${ObservationUnit.PK} AS id, units.`geo_coordinates` as "geo_coordinates"
            FROM ${ObservationUnit.tableName} AS units
            LEFT JOIN ${ObservationUnitValue.tableName} AS vals ON units.${ObservationUnit.PK} = vals.${ObservationUnit.FK}
            LEFT JOIN ${ObservationUnitAttribute.tableName} AS attr on vals.${ObservationUnitAttribute.FK} = attr.${ObservationUnitAttribute.PK}
            LEFT JOIN plot_attributes as a on vals.observation_unit_attribute_db_id = a.attribute_id
            WHERE units.${Study.FK} = $exp_id
            GROUP BY units.${ObservationUnit.PK}
        """.trimMargin()

            db.execSQL(query)

//            println("$exp_id $query")
//
//            println("New switch field time: ${
//                measureTimeMillis {
//                    db.execSQL(query)
//                }.toLong()
//            }")

        }

        fun deleteField(exp_id: Int) = withDatabase { db ->

            try {

                db.rawQuery("PRAGMA foreign_keys=OFF", null)
                db.delete(ObservationUnit.tableName, "${Study.FK} = ?", arrayOf(exp_id.toString()))
                db.delete(ObservationUnitValue.tableName, "${Study.FK} = ?", arrayOf(exp_id.toString()))
                //db.delete(ObservationUnitAttribute.tableName, "${Study.FK} = ?", arrayOf(exp_id.toString()))
                db.update(Observation.tableName, contentValuesOf(Study.FK to Integer.parseInt("-$exp_id")), "${Study.FK} = ?", arrayOf(exp_id.toString()))
                db.delete(Study.tableName, "${Study.PK} = ?", arrayOf(exp_id.toString()))
                db.rawQuery("PRAGMA foreign_keys=ON", null)

            } catch (e: SQLiteException) {

                e.printStackTrace()

                Log.d("StudyDao", "error during field deletion")

            }

        }

        /**
         * Function that queries the field/study table for the imported unique/primary/secondary names.
         */
        fun getNames(exp_id: Int): FieldPreferenceNames? = withDatabase { db ->

            val fieldNames = db.query(Study.tableName,
                    select = arrayOf("study_primary_id_name", "study_secondary_id_name", "study_unique_id_name"),
                    where = "${Study.PK} = ?",
                    whereArgs = arrayOf(exp_id.toString()))
                    .toFirst()

            FieldPreferenceNames(
                    unique = fieldNames["study_unique_id_name"].toString(),
                    primary = fieldNames["study_primary_id_name"].toString(),
                    secondary = fieldNames["study_secondary_id_name"].toString())

        }

//        fun getAllStudyModels(): Array<StudyModel> = withDatabase { db ->
//
//            db.query(Study.tableName,
//                    orderBy = Study.PK)
//                    .toTable().map {
//                        StudyModel(it)
//                    }.toTypedArray()
//
//        } ?: arrayOf()

        private fun Map<String, Any?>.toFieldObject() = FieldObject().also {

            it.exp_id = this[Study.PK].toString().toInt()
            it.study_db_id = this["study_db_id"].toString()
            it.exp_name = this["study_name"].toString()
            it.exp_alias = this["study_alias"].toString()
            it.unique_id = this["study_unique_id_name"].toString()
            it.primary_id = this["study_primary_id_name"].toString()
            it.secondary_id = this["study_secondary_id_name"].toString()
            it.date_import = this["date_import"].toString()
            it.exp_sort = this["study_sort_name"]?.toString()
            it.date_edit = when (val date = this["date_edit"]?.toString()) {
                null, "null" -> ""
                else -> date
            }
            it.date_export = when (val date = this["date_export"]?.toString()) {
                null, "null" -> ""
                else -> date
            }
            it.date_sync = when (val date = this["date_sync"]?.toString()) {
                null, "null" -> ""
                else -> date
            }
            it.import_format = ImportFormat.fromString(this["import_format"]?.toString()) ?: ImportFormat.CSV
            it.exp_source = this["study_source"]?.toString()
            it.count = this["count"].toString()
            it.observation_level = when (val observationLevel = this["observation_levels"]?.toString()) {
                null, "null" -> ""
                else -> observationLevel
            }
            it.attribute_count = this["attribute_count"]?.toString()
            it.trait_count = this["trait_count"]?.toString()
            it.observation_count = this["observation_count"]?.toString()
            it.trial_name = this["trial_name"]?.toString()
            it.search_attribute = this["observation_unit_search_attribute"]?.toString()
            it.groupName = this["group_name"]?.toString()
        }

        fun getAllFieldObjects(sortOrder: String): ArrayList<FieldObject> = withDatabase { db ->

            val studies = ArrayList<FieldObject>()
            val isDateSort = sortOrder.startsWith("date_")

            val query = """
                SELECT 
                    Studies.*,
                    (SELECT COUNT(*) FROM observation_units_attributes WHERE study_id = Studies.${Study.PK}) AS attribute_count,
                    (SELECT COUNT(DISTINCT observation_variable_name) FROM observations WHERE study_id = Studies.${Study.PK} AND observation_variable_db_id > 0) AS trait_count,
                    (SELECT COUNT(*) FROM observations WHERE study_id = Studies.${Study.PK} AND observation_variable_db_id > 0) AS observation_count
                FROM ${Study.tableName} AS Studies
                ORDER BY ${if (sortOrder == "visible") "position" else sortOrder} COLLATE NOCASE ${if (isDateSort) "DESC" else "ASC"}
            """
            db.rawQuery(query, null).use { cursor ->
                while (cursor.moveToNext()) {
                    val model = cursor.columnNames.associateWith { columnName ->
                        val columnIndex = cursor.getColumnIndex(columnName)
                        if (columnIndex != -1) cursor.getString(columnIndex) else null
                    }
                    studies.add(model.toFieldObject())
                }
            }
            ArrayList(studies)

        } ?: ArrayList()


        fun getFieldObject(exp_id: Int, sortOrder: String = "position"): FieldObject? = withDatabase { db ->
            val query = """
                SELECT 
                    ${Study.PK},
                    study_db_id,
                    study_name,
                    study_alias,
                    study_unique_id_name,
                    study_primary_id_name,
                    study_secondary_id_name,
                    observation_levels,
                    date_import,
                    date_edit,
                    date_export,
                    date_sync,
                    import_format,
                    study_source,
                    study_sort_name,
                    trial_name,
                    count,
                    observation_unit_search_attribute,
                    (SELECT COUNT(*) FROM observation_units_attributes WHERE study_id = Studies.${Study.PK}) AS attribute_count,
                    (SELECT COUNT(DISTINCT observation_variable_name) FROM observations WHERE study_id = Studies.${Study.PK} AND observation_variable_db_id > 0) AS trait_count,
                    (SELECT COUNT(*) FROM observations WHERE study_id = Studies.${Study.PK} AND observation_variable_db_id > 0) AS observation_count
                FROM ${Study.tableName} AS Studies
                WHERE ${Study.PK} = ?
                """
//            Log.d("StudyDao", "Query is "+query)
            val fieldData = db.rawQuery(query, arrayOf(exp_id.toString())).use { cursor ->
                if (cursor.moveToFirst()) {
                    val map = cursor.columnNames.associateWith { columnName ->
                        val columnIndex = cursor.getColumnIndex(columnName)
                        if (columnIndex != -1) cursor.getString(columnIndex) else null
                    }
                    map.toFieldObject().apply {
                        // Set the trait details
                        this.traitDetails = getTraitDetailsForStudy(exp_id, sortOrder)
                    }
                } else {
                    null
                }
            }
            fieldData
        }


        /**
         * This function returns trait details for a given study in the form
         * data class TraitDetail(
         *         val traitName: String,
         *         val format: String,
         *         val categories: String,
         *         val count: Int,
         *         val observations: List<String>
         *         val completeness: Float
         * )
         */

        fun getTraitDetailsForStudy(studyId: Int, sortOrder: String = "position"): List<FieldObject.TraitDetail> {
            return withDatabase { db ->
                val traitDetails = mutableListOf<FieldObject.TraitDetail>()

                val cursor = db.rawQuery("""
                    SELECT o.observation_variable_name, o.observation_variable_field_book_format, COUNT(*) as count, GROUP_CONCAT(o.value, '|') as observations,
                    (SELECT COUNT(DISTINCT observation_unit_id) FROM observations WHERE study_id = ? AND observation_variable_name = o.observation_variable_name) AS distinct_obs_units,
                    (SELECT COUNT(*) FROM observation_units WHERE study_id = ?) AS total_obs_units,
                    (SELECT v.observation_variable_attribute_value 
                     FROM observation_variable_values v
                     JOIN observation_variable_attributes a ON v.observation_variable_attribute_db_id = a.internal_id_observation_variable_attribute
                     WHERE v.observation_variable_db_id = o.observation_variable_db_id AND a.observation_variable_attribute_name = 'category') AS categories
                    FROM observations o
                    JOIN observation_variables ov ON o.observation_variable_db_id = ov.internal_id_observation_variable
                    WHERE o.study_id = ? AND o.observation_variable_db_id > 0
                    GROUP BY o.observation_variable_name, o.observation_variable_field_book_format
                    ORDER BY ov.${if (sortOrder == "visible") "position" else sortOrder} COLLATE NOCASE ASC
                """, arrayOf(studyId.toString(), studyId.toString(), studyId.toString()))

                if (cursor.moveToFirst()) {
                    do {
                        val traitName = cursor.getString(cursor.getColumnIndexOrThrow("observation_variable_name"))
                        val format = cursor.getString(cursor.getColumnIndexOrThrow("observation_variable_field_book_format"))
                        val count = cursor.getInt(cursor.getColumnIndexOrThrow("count"))
                        val observationsString = cursor.getString(cursor.getColumnIndexOrThrow("observations"))
                        val rawObservations = observationsString?.split("|") ?: emptyList()
                        val observations = rawObservations.map { obs ->
                            CategoryJsonUtil.processValue(
                                buildMap {
                                    put("observation_variable_field_book_format", format)
                                    put("value", obs)
                                }
                            )
                        }
                        val distinctObsUnits = cursor.getInt(cursor.getColumnIndexOrThrow("distinct_obs_units"))
                        val totalObsUnits = cursor.getInt(cursor.getColumnIndexOrThrow("total_obs_units"))
                        val completeness = distinctObsUnits.toFloat() / totalObsUnits.toFloat()
                        val categories = cursor.getString(cursor.getColumnIndexOrThrow("categories"))

                        traitDetails.add(FieldObject.TraitDetail(traitName, format, categories, count, observations, completeness))
                    } while (cursor.moveToNext())
                }

                cursor.close()
                traitDetails
            } ?: emptyList()
        }


        /**
         * This function uses a field object to create a exp/study row in the database.
         * Columns are new observation unit attribute names that are inserted as well.
         */
        fun createField(e: FieldObject, timestamp: String, columns: List<String>, fromBrapi: Boolean): Int = withDatabase { db ->

            when (val sid = if (fromBrapi) checkBrapiStudyUnique(
                e.observation_level,
                e.study_db_id
            ) else checkFieldNameAndObsLvl(e.exp_name, e.observation_level)) {

                -1 -> {

                    db.beginTransaction()

                    //insert new study row into table
                    val rowid = db.insert(Study.tableName, null, ContentValues().apply {
                        put("study_db_id", e.study_db_id)
                        put("study_name", e.exp_name)
                        put("study_alias", e.exp_alias)
                        put("study_unique_id_name", e.unique_id)
                        put("study_primary_id_name", e.primary_id)
                        put("study_secondary_id_name", e.secondary_id)
                        put("experimental_design", e.exp_layout)
                        put("common_crop_name", e.exp_species)
                        put("study_sort_name", e.exp_sort)
                        put("date_import", timestamp)
                        put("date_export", e.date_export)
                        put("date_edit", e.date_edit)
                        put("date_sync", e.date_sync)
                        put("import_format", e.import_format?.toString() ?: "")
                        put("study_source", e.exp_source)
                        put("count", e.count)
                        put("observation_levels", e.observation_level)
                        put("trial_name", e.trial_name)
                    }).toInt()

                    try {
                        //TODO remove when we handle primary/secondary ids better
                        val actualColumns = columns.toMutableList()
                        //insert observation unit attributes using columns parameter
                        if (e.primary_id !in columns) actualColumns += e.primary_id
                        if (e.secondary_id !in columns) actualColumns += e.secondary_id
                        actualColumns.forEach {
                            db.insert(ObservationUnitAttribute.tableName, null, contentValuesOf(
                                    "observation_unit_attribute_name" to it,
                                    Study.FK to rowid
                            ))
                        }

                        db.setTransactionSuccessful()

                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {

                        db.endTransaction()
                    }

                    rowid

                }

                else -> sid
            }

        } ?: -1

        data class FieldPreferenceNames(val unique: String, val primary: String, val secondary: String)

        /**
         * This function should always be called within a transaction.
         */
        fun createFieldData(studyId: Int, columns: List<String>, data: List<String>) = withDatabase { db ->

            val names = getNames(studyId)!!

            //input data corresponds to original database column names
            val uniqueIndex = columns.indexOf(names.unique)
            val primaryIndex = columns.indexOf(names.primary)
            val secondaryIndex = columns.indexOf(names.secondary)

            //check if data size matches the columns size, on mismatch fill with dummy data
            //mainly fixes issues with BrAPI when xtype/ytype and row/col values are not given
            val actualData = if (data.size != columns.size) {
                val tempData = data.toMutableList()
                for (i in 0..(columns.size - data.size))
                    tempData += "NA"
                tempData
            } else data

            val geoCoordinatesColumnName = "geo_coordinates"
            var geoCoordinates = ""
            val geoCoordinatesIndex: Int
            if (geoCoordinatesColumnName in columns) {
                geoCoordinatesIndex = columns.indexOf(geoCoordinatesColumnName)
                if (geoCoordinatesIndex > -1 && geoCoordinatesIndex < data.size) {
                    geoCoordinates = data[geoCoordinatesIndex]
                }
            }

            val rowid = db.insert(ObservationUnit.tableName, null, contentValuesOf(
                Study.FK to studyId,
                "observation_unit_db_id" to actualData[uniqueIndex],
                "geo_coordinates" to geoCoordinates
            ))

            columns.forEachIndexed { index, it ->

                if (it != "geo_coordinates") {
                    val attrId = ObservationUnitAttributeDao.getIdByName(it)

                    db.insert(ObservationUnitValue.tableName, null, contentValuesOf(
                        Study.FK to studyId,
                        ObservationUnit.FK to rowid,
                        ObservationUnitAttribute.FK to attrId,
                        "observation_unit_value_name" to actualData[index]
                    ))
                }
            }
        }

        fun updateImportDate(studyId: Int) = withDatabase { db ->

            db.update(Study.tableName, ContentValues().apply {
                put("count", getCount(studyId))
                put("date_import", getTime())
            }, "${Study.PK} = ?", arrayOf("$studyId"))
        }

        fun updateEditDate(studyId: Int) = withDatabase { db ->

            db.query(
                Study.tableName,
                where = "${Study.PK} = ?",
                whereArgs = arrayOf(studyId.toString())
            ).toFirst().let { study ->

                if (study[Study.PK] == studyId) {

                    val studyKey = study[Study.PK]

                    with(
                        db.query(
                            Observation.tableName, arrayOf("observation_time_stamp"),
                            where = "${Study.FK} = ?",
                            whereArgs = arrayOf("$studyKey"),
                            orderBy = "datetime(substr(observation_time_stamp, 1, 19)) DESC"
                        ).toFirst()
                    ) {

                        db.update(Study.tableName, ContentValues().apply {
                            put("date_edit", this@with["observation_time_stamp"].toString())
                        }, "${Study.PK} = ?", arrayOf("$studyKey"))
                    }

                }
            }
        }

        fun updateExportDate(studyId: Int) = withDatabase { db ->

            db.update(Study.tableName, ContentValues().apply {
                put("date_export", getTime())
            }, "${Study.PK} = ?", arrayOf("$studyId"))
        }

        fun updateSyncDate(studyId: Int) = withDatabase { db ->
            db.update(Study.tableName, ContentValues().apply {
                put("date_sync", getTime())
            }, "${Study.PK} = ?", arrayOf("$studyId"))
        }

        fun updateStudyAlias(studyId: Int, newName: String) = withDatabase { db ->
            val contentValues = ContentValues()
            contentValues.put("study_alias", newName)
            db.update(Study.tableName, contentValues, "${Study.PK} = ?", arrayOf("$studyId"))
        }

        fun updateStudySort(sort: String?, studyId: Int) = withDatabase { db ->
            val contentVals = ContentValues()
            if (sort == null) {
                contentVals.putNull("study_sort_name")
            } else {
                contentVals.put("study_sort_name", sort)
            }
            db.update(Study.tableName, contentVals, "${Study.PK} = ?", arrayOf("$studyId"))
        }

        /**
         * Search for the first studies row that matches the name parameter.
         * Default return value is -1
         */
        fun checkFieldName(name: String): Int = withDatabase { db ->

            db.query(Study.tableName,
                    arrayOf(Study.PK),
                    where = "study_name = ?",
                    whereArgs = arrayOf(name)).toFirst()[Study.PK] as? Int ?: -1

        } ?: -1

        /**
         * Search for the first studies row that matches the name and observationLevel parameter.
         * Default return value is -1
         */
        fun checkFieldNameAndObsLvl(name: String, observationLevel: String?): Int = withDatabase { db ->
            db.query(
                Study.tableName,
                arrayOf(Study.PK),
                where = "study_name = ? AND observation_levels = ?",
                whereArgs = arrayOf(name, observationLevel ?: "")
            ).toFirst()[Study.PK] as? Int ?: -1

        } ?: -1

        fun checkBrapiStudyUnique(observationLevel: String?, brapiId: String?): Int = withDatabase { db ->
            db.query(
                Study.tableName,
                arrayOf(Study.PK),
                where = "observation_levels = ? AND study_db_id = ?",
                whereArgs = arrayOf(observationLevel ?: "", brapiId ?: "")
            ).toFirst()[Study.PK] as? Int ?: -1

        } ?: -1

        fun getCount(studyId: Int): Int = withDatabase {

            ObservationUnitDao.getAll(studyId).size

        } ?: 0

        fun getById(id: String) = withDatabase { db ->

            StudyModel(
                db.query(
                    Study.tableName,
                    where = "internal_id_study = ?",
                    whereArgs = arrayOf(id)
                ).toFirst()
            )
        }

        /**
         * Returns distinct group_names from the studies table
         */
        fun getDistinctGroups(): List<String> = withDatabase { db ->
            val groupNames = mutableListOf<String>()
            val query = "SELECT DISTINCT group_name FROM studies WHERE group_name IS NOT NULL AND group_name <> '' ORDER BY group_name ASC"

            db.rawQuery(query, null).use { cursor ->
                while (cursor.moveToNext()) {
                    cursor.getString(0)?.let { groupNames.add(it) }
                }
            }
            groupNames
        } ?: emptyList()

        /**
         * Updates the group_name for a study
         */
        fun updateFieldGroup(studyId: Int, groupName: String?) = withDatabase { db ->
            val contentValues = ContentValues()
            if (groupName == null) {
                contentValues.putNull("group_name")
            } else {
                contentValues.put("group_name", groupName)
            }
            db.update(Study.tableName, contentValues, "${Study.PK} = ?", arrayOf("$studyId"))
        }
    }
}
