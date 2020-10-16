package com.fieldbook.tracker.database.models

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.objects.FieldObject
import java.util.*
import kotlin.math.exp

/**
 * Entity definition parameters are exceptions to AOSP coding standards
 * to reflect column names in SQLite table.
 */
data class StudyModel(val map: Map<String, Any?>) {

        val study_db_id: String? by map //brapId
        val study_name: String? by map
        val study_alias: String? by map //stored if user edits study_name
        val study_unique_id_name: String? by map //user assigned
        val study_primary_id_name: String? by map //user assigned maybe coordinate_x_type ?
        val study_secondary_id_name: String? by map //user assigned
        val experimental_design: String? by map
        val common_crop_name: String? by map
        val study_sort_name: String? by map //selected column (plot prop or trait?) to sort field by when moving through entries
        val date_import: String? by map
        val date_export: String? by map
        val study_source: StudySource? by map //denotes whether field was imported via brapi or locally
        val additional_info: String? by map //catch all, dont need with attr/value tables, turn into a query when making BrapiStudyDetail
        val location_db_id: String? by map //brapId?
        val location_name: String? by map
        val observation_levels: String? by map
        val season: String? by map
        val start_date: String? by map
        val study_code: String? by map
        val study_description: String? by map
        val study_type: String? by map
        val trial_db_id: String? by map //brapId?
        val trial_name: String? by map
        val internal_id_study: Int by map //pk

    companion object {
        const val PK: String = "internal_id_study"
        const val FK = "study_db_id"
        enum class StudySource {
            BRAPI, LOCAL;
            override fun toString(): String {
                return when (this) {
                    BRAPI -> "BRAPI"
                    else -> "LOCAL"
                }
            }
        }
        val migrateFromTableName = "exp_id"
        val tableName = "studies"
        val migratePattern by lazy {
            mapOf("exp_id" to PK,
                    "exp_name" to "study_name",
                    "exp_alias" to "study_alias",
                    "unique_id" to "study_unique_id_name",
                    "primary_id" to "study_primary_id_name",
                    "secondary_id" to "study_secondary_id_name",
                    "exp_layout" to "experimental_design",
                    "exp_species" to "common_crop_name",
                    "exp_sort" to "study_sort_name",
                    "date_import" to "date_import",
                    "date_export" to "date_export",
                    "exp_source" to "study_source",)
        }
        val columnDefs by lazy {
            mapOf("study_db_id" to "Text",
                    "study_name" to "Text",
                    "study_alias" to "Text",
                    "study_unique_id_name" to "Text",
                    "study_primary_id_name" to "Text",
                    "study_secondary_id_name" to "Text",
                    "experimental_design" to "Text",
                    "common_crop_name" to "Text",
                    "study_sort_name" to "Text",
                    "date_import" to "Text",
                    "date_export" to "Text",
                    "study_source" to "Text",
                    "additional_info" to "Text",
                    "location_db_id" to "Text",
                    "location_name" to "Text",
                    "observation_levels" to "Text",
                    "seasons" to "Text",
                    "start_date" to "Text",
                    "study_code" to "Text",
                    "study_description" to "Text",
                    "study_type" to "Text",
                    "trial_db_id" to "Text",
                    "trial_name" to "Text",
                    PK to "INTEGER PRIMARY KEY AUTOINCREMENT")
        }

        fun getAllFieldObjects(): Array<StudyModel> = withDatabase { db ->

            //TODO what about study_db_id?
            db.query(StudyModel.tableName,
                    orderBy = PK)
                    .toTable().map {
                        StudyModel(it)
                    }.toTypedArray()

        } ?: arrayOf()

        //TODO query missing count/date_edit
        fun getFieldObject(exp_id: Int): FieldObject? = withDatabase { db ->

            db.query(StudyModel.tableName,
                    select = arrayOf(PK,
                            "study_name",
                            "study_unique_id_name",
                            "study_primary_id_name",
                            "study_secondary_id_name",
                            "date_import",
                            "date_export",
                            "study_source"),
                    where = "$PK = ?",
                    whereArgs = arrayOf(exp_id.toString()),
                    orderBy = PK).use { cursor ->

                if (cursor.moveToFirst()) {

                    FieldObject().also {
                        it.exp_id = cursor.getInt(cursor.getColumnIndexOrThrow(PK))
                        it.exp_name = cursor.getString(cursor.getColumnIndexOrThrow("study_name"))
                        it.unique_id = cursor.getString(cursor.getColumnIndexOrThrow("study_unique_id_name"))
                        it.primary_id = cursor.getString(cursor.getColumnIndexOrThrow("study_primary_id_name"))
                        it.secondary_id = cursor.getString(cursor.getColumnIndexOrThrow("study_secondary_id_name"))
                        it.date_import = cursor.getString(cursor.getColumnIndexOrThrow("date_import"))
                        it.date_export = cursor.getString(cursor.getColumnIndexOrThrow("date_export"))
                        it.exp_source = cursor.getString(cursor.getColumnIndexOrThrow("study_source"))
                    }

                } else null
            }
        }

        fun createField(e: FieldObject, columns: List<String>): Int = transaction { db ->

            when (val sid = checkFieldName(e.exp_name)) {

                -1 -> {

                    val rowid = db.insert(tableName, null, ContentValues().apply {

                        put(PK, e.exp_id)
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
                    })

                    rowid.toInt()
                }

                else -> sid
            }

        } ?: -1

        fun createFieldData(unique: String, primary: String, secondary: String, exp_id: Int, columns: List<String>, data: List<String>) = transaction { db ->

            val rowid = db.insert(ObservationUnitModel.tableName, null, contentValuesOf(
                    FK to exp_id,
                    "observation_unit_db_id" to data[columns.indexOf(unique)],
                    "position_coordinate_x" to data[columns.indexOf(primary)],
                    "position_coordinate_y" to data[columns.indexOf(secondary)]))

            columns.forEachIndexed { index, it ->

                val attrId = ObservationUnitAttributeModel.getIdByName(it)

                db.insert(ObservationUnitValueModel.tableName, null, contentValuesOf(
                        FK to rowid,
                        ObservationUnitAttributeModel.FK to attrId,
                        "observation_units_value_name" to data[index]
                ))
            }
        }

        private fun updateImportDate(db: SQLiteDatabase, exp_id: Int) = transaction { db ->

            db.update(StudyModel.tableName, ContentValues().apply {
                //put("count", db.query(ObservationUnitModel.tableName).count)
                put("date_import", getTime())
            }, "$PK = ?", arrayOf("$exp_id"))
        }

        //TODO Trevor date_edit is not a column in study table
        private fun modifyDate(db: SQLiteDatabase, exp_id: Int) {

            db.query(StudyModel.tableName).toTable().forEach {

                with(db.query(ObservationModel.tableName, arrayOf("observation_time_stamp"),
                        where = "$FK = ?",
                        whereArgs = arrayOf("$exp_id"),
                        orderBy = "datetime(substr(observation_time_stamp, 1, 19)) DESC").toFirst()) {

//                    db.update(StudyModel.tableName, ContentValues().apply {
//                        put("date_edit", this@with["observation_time_stamp"].toString())
//                    }, "$PK = ?", arrayOf("$exp_id"))
                }
            }
        }

        private fun updateExportDate(db: SQLiteDatabase, exp_id: Int) {

            db.update(StudyModel.tableName, ContentValues().apply {
                put("date_export", getTime())
            }, "$PK = ?", arrayOf("$exp_id"))
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

            db.query(StudyModel.tableName,
                    arrayOf(PK),
                    where = "study_name = ?",
                    whereArgs = arrayOf(name)).toFirst()[PK] as? Int ?: -1

        } ?: -1

        private fun String?.toStudySource() = when {
            "BRAPI" in (this?.toUpperCase(Locale.ROOT)) ?: "" -> StudyModel.Companion.StudySource.BRAPI
            else -> StudyModel.Companion.StudySource.LOCAL
        }
    }
}
