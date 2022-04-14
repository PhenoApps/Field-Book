package com.fieldbook.tracker.database.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.core.content.contentValuesOf
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.Migrator.Companion.sObservationUnitPropertyViewName
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.database.Migrator.Observation
import com.fieldbook.tracker.database.Migrator.ObservationUnit
import com.fieldbook.tracker.database.Migrator.ObservationUnitAttribute
import com.fieldbook.tracker.database.Migrator.ObservationUnitValue
import com.fieldbook.tracker.database.Migrator.Study


class StudyDao {

    companion object {

        /**
         * Transpose obs. unit. attribute/values into a view based on the selected study.
         * On further testing, creating a view here is substantially faster but queries on the
         * view are ~4x the runtime as using a table.
         */
        fun switchField(exp_id: Int) = withDatabase { db ->

            val headers = ObservationUnitAttributeDao.getAllNames(exp_id)

            //create a select statement based on the saved plot attribute names
            val select = headers.map { col ->

                "MAX(CASE WHEN attr.observation_unit_attribute_name = \"$col\" THEN vals.observation_unit_value_name ELSE NULL END) AS \"$col\""

            }

            //combine the case statements with commas
            val selectStatement = if (select.isNotEmpty()) {
                ", " + select.joinToString(", ")
            } else ""

            /**
             * Creating a view here is faster, but
             * using a table gives better performance for getRangeByIdAndPlot query
             */
            val query = """
                CREATE TABLE IF NOT EXISTS $sObservationUnitPropertyViewName AS 
                SELECT units.${ObservationUnit.PK} AS id $selectStatement
                FROM ${ObservationUnit.tableName} AS units
                LEFT JOIN ${ObservationUnitValue.tableName} AS vals ON units.${ObservationUnit.PK} = vals.${ObservationUnit.FK}
                LEFT JOIN ${ObservationUnitAttribute.tableName} AS attr on vals.${ObservationUnitAttribute.FK} = attr.${ObservationUnitAttribute.PK}
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
                db.delete(ObservationUnitAttribute.tableName, "${Study.FK} = ?", arrayOf(exp_id.toString()))
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

            it.exp_id = this[Study.PK] as Int
            it.exp_name = this["study_name"].toString()
            it.unique_id = this["study_unique_id_name"].toString()
            it.primary_id = this["study_primary_id_name"].toString()
            it.secondary_id = this["study_secondary_id_name"].toString()
            it.date_import = this["date_import"].toString()
            it.exp_sort = this["study_sort_name"].toString()
            it.date_edit = when (val date = this["date_edit"]?.toString()) {
                null, "null" -> ""
                else -> date
            }
            it.date_export = when (val date = this["date_export"]?.toString()) {
                null, "null" -> ""
                else -> date
            }
            this["study_source"]?.let { source ->
                it.exp_source = source.toString()
            }
            it.count = this["count"].toString()
        }

        fun getAllFieldObjects(): ArrayList<FieldObject> = withDatabase { db ->

            val studies = ArrayList<FieldObject>()

            db.query(Study.tableName).toTable().forEach { model ->

                studies.add(model.toFieldObject())

            }

            studies

        } ?: ArrayList()

        //TODO query missing count/date_edit
        fun getFieldObject(exp_id: Int): FieldObject? = withDatabase { db ->

            db.query(Study.tableName,
                    select = arrayOf(Study.PK,
                            "study_name",
                            "study_unique_id_name",
                            "study_primary_id_name",
                            "study_secondary_id_name",
                            "date_import",
                            "date_edit",
                            "date_export",
                            "study_source",
                            "study_sort_name"),
                    where = "${Study.PK} = ?",
                    whereArgs = arrayOf(exp_id.toString()),
                    orderBy = Study.PK).toFirst().toFieldObject()
        }

        /**
         * This function uses a field object to create a exp/study row in the database.
         * Columns are new observation unit attribute names that are inserted as well.
         */
        fun createField(e: FieldObject, timestamp: String, columns: List<String>): Int = withDatabase { db ->

            when (val sid = checkFieldName(e.exp_name)) {

                -1 -> {

                    db.beginTransaction()

                    //insert new study row into table
                    val rowid = db.insert(Study.tableName, null, ContentValues().apply {

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
                        put("study_source", e.exp_source)
                        put("count", e.count)
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
        fun createFieldData(exp_id: Int, columns: List<String>, data: List<String>) = withDatabase { db ->

            val names = getNames(exp_id)!!

            //TODO: indexOf can return -1 which leads to array out of bounds exception
            //input data corresponds to original database column names
            val uniqueIndex = columns.indexOf(names.unique)
            val primaryIndex = columns.indexOf(names.primary)
            val secondaryIndex = columns.indexOf(names.secondary)

            //TODO remove when we handle primary/secondary ids better
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
                    Study.FK to exp_id,
                    "observation_unit_db_id" to actualData[uniqueIndex],
                    "primary_id" to if (primaryIndex < 0) "NA" else actualData[primaryIndex],
                    "secondary_id" to if (secondaryIndex < 0) "NA" else actualData[secondaryIndex],
                     "geo_coordinates" to geoCoordinates))

            columns.forEachIndexed { index, it ->

                val attrId = ObservationUnitAttributeDao.getIdByName(it)

                db.insert(ObservationUnitValue.tableName, null, contentValuesOf(
                        Study.FK to exp_id,
                        ObservationUnit.FK to rowid,
                        ObservationUnitAttribute.FK to attrId,
                        "observation_unit_value_name" to actualData[index]
                ))
            }

            if (primaryIndex < 0) {

                val attrId = ObservationUnitAttributeDao.getIdByName("Row")

                db.insert(ObservationUnitValue.tableName, null, contentValuesOf(
                    Study.FK to exp_id,
                    ObservationUnit.FK to rowid,
                    ObservationUnitAttribute.FK to attrId,
                    "observation_unit_value_name" to "NA"
                ))
            }

            if (secondaryIndex < 0) {

                val attrId = ObservationUnitAttributeDao.getIdByName("Column")

                db.insert(ObservationUnitValue.tableName, null, contentValuesOf(
                    Study.FK to exp_id,
                    ObservationUnit.FK to rowid,
                    ObservationUnitAttribute.FK to attrId,
                    "observation_unit_value_name" to "NA"
                ))
            }
        }

        private fun updateImportDate(db: SQLiteDatabase, exp_id: Int) {

            db.update(Study.tableName, ContentValues().apply {
                put("count", getCount(exp_id))
                put("date_import", getTime())
            }, "${Study.PK} = ?", arrayOf("$exp_id"))
        }

        private fun modifyDate(db: SQLiteDatabase, exp_id: Int) {

            db.query(Study.tableName,
                where = "${Study.PK} = ?",
                whereArgs = arrayOf(exp_id.toString())).toFirst().let { study ->

                if (study[Study.PK] == exp_id) {

                    val studyKey = study[Study.PK]

                    with(db.query(Observation.tableName, arrayOf("observation_time_stamp"),
                            where = "${Study.FK} = ?",
                            whereArgs = arrayOf("$studyKey"),
                            orderBy = "datetime(substr(observation_time_stamp, 1, 19)) DESC").toFirst()) {

                        db.update(Study.tableName, ContentValues().apply {
                            put("date_edit", this@with["observation_time_stamp"].toString())
                        }, "${Study.PK} = ?", arrayOf("$studyKey"))
                    }

                }
            }
        }

        private fun updateExportDate(db: SQLiteDatabase, exp_id: Int) {

            db.update(Study.tableName, ContentValues().apply {
                put("date_export", getTime())
            }, "${Study.PK} = ?", arrayOf("$exp_id"))
        }

        /**
         * Updates the date_import field and count column of the study table.
         * imp: boolean flag to get import date of observation units and count
         *
         */
        fun updateStudyTable(updateImportDate: Boolean = false,
                             modifyDate: Boolean = false,
                             updateExportDate: Boolean = false, exp_id: Int) = withDatabase { db ->

            if (updateImportDate) updateImportDate(db, exp_id)

            if (modifyDate) modifyDate(db, exp_id)

            if (updateExportDate) updateExportDate(db, exp_id)
        }

        fun updateStudySort(sort: String?, exp_id: Int) = withDatabase { db ->
            var contentVals = ContentValues();
            if(sort == null) {
                contentVals.putNull("study_sort_name")
            } else {
                contentVals.put("study_sort_name", sort)
            }
            db.update(Study.tableName, contentVals, "${Study.PK} = ?", arrayOf("$exp_id"))
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

        fun getCount(studyId: Int): Int = withDatabase {

            ObservationUnitDao.getAll(studyId).size

        } ?: 0
    }
}
