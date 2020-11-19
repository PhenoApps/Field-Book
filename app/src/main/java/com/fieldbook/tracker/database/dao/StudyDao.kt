package com.fieldbook.tracker.database.dao

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.Migrator.Companion.sObservationUnitPropertyViewName
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.database.Migrator.Observation
import com.fieldbook.tracker.database.Migrator.ObservationUnit
import com.fieldbook.tracker.database.Migrator.ObservationUnitAttribute
import com.fieldbook.tracker.database.Migrator.ObservationUnitValue
import com.fieldbook.tracker.database.Migrator.Study
import com.fieldbook.tracker.database.models.StudyModel
import kotlin.system.measureTimeMillis

class StudyDao {

    companion object {

        /**
         * Transpose obs. unit. attribute/values into a view based on the selected study.
         * On further testing, creating a view here is substantially faster but queries on the
         * view are ~4x the runtime as using a table.
         */
        fun switchField(exp_id: Int) = withDatabase { db ->

            val headers: Array<String> = db.query(ObservationUnitAttribute.tableName,
                    arrayOf("observation_unit_attribute_name"),
                    where = "${Study.FK} = ?",
                    whereArgs = arrayOf("$exp_id"))
                    .toTable()
                    .map { it["observation_unit_attribute_name"] }
                    .mapNotNull { it.toString() }
                    .filter { it.isNotBlank() && it.isNotEmpty() }
                    .toTypedArray()

            //create a select statement based on the saved plot attribute names
            val select = headers.map { col ->

                "MAX(CASE WHEN attr.observation_unit_attribute_name = \"$col\" THEN vals.observation_unit_value_name ELSE NULL END) AS `$col`"

            }

            //combine the case statements with commas
            val selectStatement = if (select.isNotEmpty()) {
                ", " + select.joinToString(", ")
            } else ""

            //delete the old table
            db.execSQL("DROP TABLE IF EXISTS $sObservationUnitPropertyViewName")

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
        GROUP BY units.${ObservationUnit.PK}""".trimMargin()

            println("$exp_id $query")

            println("New switch field time: ${
                measureTimeMillis {
                    db.execSQL(query)
                }.toLong()
            }")
        }

        fun deleteField(exp_id: Int) = withDatabase { db ->

            val deleteQuery = "DELETE FROM ${Study.tableName} WHERE ${Study.PK} = $exp_id"

//    val deleteQuery = sTableNames.joinToString(";") {
//        "DELETE FROM $it WHERE internal_study_db_id = $exp_id"
//    }

//    println(deleteQuery)

            db.execSQL(deleteQuery)

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

        fun getAllStudyModels(): Array<StudyModel> = withDatabase { db ->

            db.query(Study.tableName,
                    orderBy = Study.PK)
                    .toTable().map {
                        StudyModel(it)
                    }.toTypedArray()

        } ?: arrayOf()

        fun getAllFieldObjects(): ArrayList<FieldObject> = withDatabase { db ->

            val studies = ArrayList<FieldObject>()

            db.query(Study.tableName).toTable().forEach { model ->

                studies.add(FieldObject().also {
                    it.exp_id = model[Study.PK] as Int
                    it.exp_name = model["study_name"].toString()
                    it.unique_id = model["study_unique_id_name"].toString()
                    it.primary_id = model["study_primary_id_name"].toString()
                    it.secondary_id = model["study_secondary_id_name"].toString()
                    it.date_import = model["date_import"].toString()
                    it.date_export = model["date_export"].toString()
                    it.exp_source = model["study_source"].toString()
                    it.count = model["count"].toString()
                })

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
                            "date_export",
                            "study_source"),
                    where = "${Study.PK} = ?",
                    whereArgs = arrayOf(exp_id.toString()),
                    orderBy = Study.PK).use { cursor ->

                if (cursor.moveToFirst()) {

                    FieldObject().also {
                        it.exp_id = cursor.getInt(cursor.getColumnIndexOrThrow(Study.PK))
                        it.exp_name = cursor.getString(cursor.getColumnIndexOrThrow("study_name"))
                        it.unique_id = cursor.getString(cursor.getColumnIndexOrThrow("study_unique_id_name"))
                        it.primary_id = cursor.getString(cursor.getColumnIndexOrThrow("study_primary_id_name"))
                        it.secondary_id = cursor.getString(cursor.getColumnIndexOrThrow("study_secondary_id_name"))
                        it.date_import = cursor.getString(cursor.getColumnIndexOrThrow("date_import"))
                        it.date_export = cursor.getString(cursor.getColumnIndexOrThrow("date_export"))
                        it.exp_source = cursor.getString(cursor.getColumnIndexOrThrow("study_source"))
                        it.count = cursor.count.toString()
                    }

                } else null
            }
        }

        /**
         * This function uses a field object to create a exp/study row in the database.
         * Columns are new observation unit attribute names that are inserted as well.
         */
        fun createField(e: FieldObject, columns: List<String>): Int = transaction { db ->

            when (val sid = checkFieldName(e.exp_name)) {

                -1 -> {

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
                        put("date_import", e.date_import)
                        put("study_source", e.exp_source)
                    }).toInt()

                    //insert observation unit attributes using columns parameter
                    columns.forEach {
                        db.insert(ObservationUnitAttribute.tableName, null, contentValuesOf(
                                "observation_unit_attribute_name" to it,
                                Study.FK to rowid
                        ))
                    }

                    rowid

                }

                else -> sid
            }

        } ?: -1

        data class FieldPreferenceNames(val unique: String, val primary: String, val secondary: String)

        fun createFieldData(exp_id: Int, columns: List<String>, data: List<String>) = transaction { db ->

            val names = getNames(exp_id)!!

            //input data corresponds to original database column names
            val uniqueIndex = columns.indexOf(names.unique)
            val primaryIndex = columns.indexOf(names.primary)
            val secondaryIndex = columns.indexOf(names.secondary)

            val rowid = db.insert(ObservationUnit.tableName, null, contentValuesOf(
                    Study.FK to exp_id,
                    "observation_unit_db_id" to data[uniqueIndex],
                    "primary_id" to data[primaryIndex],
                    "secondary_id" to data[secondaryIndex]))

            columns.forEachIndexed { index, it ->

                val attrId = ObservationUnitAttributeDao.getIdByName(it)

                db.insert(ObservationUnitValue.tableName, null, contentValuesOf(
                        Study.FK to rowid,
                        ObservationUnitAttribute.FK to attrId,
                        "observation_unit_value_name" to data[index]
                ))
            }
        }

        private fun updateImportDate(db: SQLiteDatabase, exp_id: Int) = transaction { db ->

            db.update(Study.tableName, ContentValues().apply {
                put("count", db.query(ObservationUnit.tableName,
                        where = "${Study.FK} = ?",
                        whereArgs = arrayOf(exp_id.toString())).count)
                put("date_import", getTime())
            }, "${Study.PK} = ?", arrayOf("$exp_id"))
        }

        //TODO Trevor date_edit is not a column in study table
        private fun modifyDate(db: SQLiteDatabase, exp_id: Int) {

            db.query(Study.tableName).toTable().forEach {

                with(db.query(Observation.tableName, arrayOf("observation_time_stamp"),
                        where = "${Study.FK} = ?",
                        whereArgs = arrayOf("$exp_id"),
                        orderBy = "datetime(substr(observation_time_stamp, 1, 19)) DESC").toFirst()) {

//                    db.update(StudyModel.tableName, ContentValues().apply {
//                        put("date_edit", this@with["observation_time_stamp"].toString())
//                    }, "$PK = ?", arrayOf("$exp_id"))
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
         */
        fun updateStudyTable(updateImportDate: Boolean = false,
                             modifyDate: Boolean = false,
                             updateExportDate: Boolean = false, exp_id: Int) = withDatabase { db ->

            if (updateImportDate) updateImportDate(db, exp_id)

            if (modifyDate) modifyDate(db, exp_id)

            if (updateExportDate) updateExportDate(db, exp_id)
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

        fun getCount(studyId: Int): Int = withDatabase { db ->

            ObservationUnitDao.getAll(studyId).size

        } ?: 0
    }
}
