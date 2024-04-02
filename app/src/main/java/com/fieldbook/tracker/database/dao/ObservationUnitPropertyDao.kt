package com.fieldbook.tracker.database.dao

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.util.Log
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.Migrator.Companion.sObservationUnitPropertyViewName
import com.fieldbook.tracker.database.Migrator.Observation
import com.fieldbook.tracker.database.Migrator.ObservationUnit
import com.fieldbook.tracker.database.Migrator.ObservationUnitAttribute
import com.fieldbook.tracker.database.Migrator.ObservationUnitValue
import com.fieldbook.tracker.database.Migrator.ObservationVariable
import com.fieldbook.tracker.database.Migrator.Study
import com.fieldbook.tracker.database.query
import com.fieldbook.tracker.database.toFirst
import com.fieldbook.tracker.database.toTable
import com.fieldbook.tracker.database.withDatabase
import com.fieldbook.tracker.objects.RangeObject
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.CategoryJsonUtil

class ObservationUnitPropertyDao {

    companion object {

//        internal fun selectAllRange(): Array<Map<String, Any?>>? = withDatabase { db ->
//            db.query(sObservationUnitPropertyViewName).toTable().toTypedArray()
//        }

        fun getObservationUnitPropertyByPlotId(column: String, plot_id: String): String = withDatabase { db ->
            db.query("ObservationUnitProperty", select = arrayOf(column), where = "plot_id = ?", whereArgs = arrayOf(plot_id))
                    .toFirst()[column].toString()
        }?: ""

        fun getAllRangeId(context: Context): Array<Int> {
            val studyId = PreferenceManager.getDefaultSharedPreferences(context)
                .getInt(GeneralKeys.SELECTED_FIELD_ID, 0)

            getSortedObservationUnitData(context, studyId)?.use { cursor ->
                val ids = mutableListOf<Int>()
                while (cursor.moveToNext()) {
                    val idIndex = cursor.getColumnIndex("id")
                    if (idIndex != -1) {
                        ids.add(cursor.getInt(idIndex))
                    }
                }
                return ids.toTypedArray()
            }

            return emptyArray()
        }


        fun getRangeFromId(firstName: String, secondName: String, uniqueName: String, id: Int): RangeObject = withDatabase { db ->
//            data.range = cursor.getString(0);
//                data.plot = cursor.getString(1);
//                data.plot_id = cursor.getString(2);
            RangeObject().apply {

                val model = db.query(sObservationUnitPropertyViewName,
                    select = arrayOf(firstName, secondName, uniqueName, "id"),
                    where = "id = ?",
                    whereArgs = arrayOf(id.toString())).toFirst()

                range = model[firstName].toString()

                plot = model[secondName].toString()

                plot_id = model[uniqueName].toString()
            }

        } ?: RangeObject().apply {
            range = ""
            plot = ""
            plot_id = ""
        }

        /**
         * This function's parameter trait is not always a trait and can be an observation unit property column.
         * For example this is used when selecting the top-left drop down in the collect activity.
         */
        fun getDropDownRange(uniqueName: String, trait: String, plotId: String): Array<String>? = withDatabase { db ->

            //added sanitation to the uniqueName in case it has a space
            val unique = if ("`" in uniqueName) uniqueName else "`$uniqueName`"
            db.query(sObservationUnitPropertyViewName,
                    select = arrayOf(trait),
                    where = "$unique LIKE ?",
                    whereArgs = arrayOf(plotId)).toTable().map {
                it[trait].toString()
            }.toTypedArray()

        }

        fun getRangeColumnNames(): Array<String?> = withDatabase { db ->

            val cursor = db.rawQuery("SELECT * FROM $sObservationUnitPropertyViewName limit 1", null)
            //Cursor cursor = db.rawQuery("SELECT * from range limit 1", null);
            //Cursor cursor = db.rawQuery("SELECT * from range limit 1", null);
            var data: Array<String?>? = null

            if (cursor.moveToFirst()) {
                data = arrayOfNulls(cursor.columnCount - 1)
                var k = 0
                for (j in 0 until cursor.columnCount) {
                    if (cursor.getColumnName(j) != "id") {
                        data[k++] = cursor.getColumnName(j).replace("//", "/")
                    }
                }
            }

            if (!cursor.isClosed) {
                cursor.close()
            }

            data

        } ?: emptyArray()

        fun getRangeColumns(): Array<String?> = withDatabase { db ->

            val cursor = db.rawQuery("SELECT * from $sObservationUnitPropertyViewName limit 1", null)

            var data: Array<String?>? = null

            if (cursor.moveToFirst()) {
                data = arrayOfNulls(cursor.columnCount - 1)
                var k = 0
                for (j in 0 until cursor.columnCount) {
                    if (cursor.getColumnName(j) != "id") {
                        data[k++] = cursor.getColumnName(j)
                    }
                }
            }

            if (!cursor.isClosed) {
                cursor.close()
            }

            data
//    db.query(sObservationUnitPropertyViewName).toFirst().keys
//            .filter { it != "id" }.toTypedArray()

        } ?: emptyArray<String?>()

        /**
         * This function is used when database is checked on export.
         * The traits array is used to determine which traits are exported.
         * In the case of "all active traits" only the visible traits are given to this query.
         * The final AND clause checks if the query's observation_variable_name exists in the list of traits.
         * Database format prints off one observation per row s.a
         * "plot_id","column","plot","tray_row","tray_id","seed_id","seed_name","pedigree","trait","value","timestamp","person","location","number"
         * "13RPN00001","1","1","1","13RPN_TRAY001","12GHT00001B","Kharkof","Kharkof","height","3","2021-08-05 11:52:45.379-05:00"," ","","2"
         */

        fun getExportDbData(
            studyId: Int,
            fieldList: Array<String?>,
            traits: ArrayList<TraitObject>
        ): Cursor? = withDatabase { db ->

            val traitRequiredFields =
                arrayOf("trait", "userValue", "timeTaken", "person", "location", "rep")
            val requiredFields = fieldList + traitRequiredFields
            MatrixCursor(requiredFields).also { cursor ->
                val placeholders = traits.joinToString(", ") { "?" }
                val traitNames = traits.map { DataHelper.replaceIdentifiers(it.name) }.toTypedArray()

                val unitSelectAttributes = fieldList.map { attributeName ->
                    "MAX(CASE WHEN attr.observation_unit_attribute_name = '$attributeName' THEN vals.observation_unit_value_name ELSE NULL END) AS \"$attributeName\""
                }.joinToString(", ")

                val obsSelectAttributes =
                    arrayOf("observation_variable_name", "observation_variable_field_book_format", "value", "observation_time_stamp", "collector", "geoCoordinates", "rep")

                val query = """
                    SELECT $unitSelectAttributes, ${obsSelectAttributes.joinToString { "obs.`$it` AS `$it`" }}
                    FROM observations AS obs
                    LEFT JOIN observation_units AS units ON units.observation_unit_db_id = obs.observation_unit_id
                    LEFT JOIN observation_units_values AS vals ON units.internal_id_observation_unit = vals.observation_unit_id
                    LEFT JOIN observation_units_attributes AS attr ON vals.observation_unit_attribute_db_id = attr.internal_id_observation_unit_attribute
                    WHERE units.study_id = ?
                      AND obs.observation_variable_name IN ($placeholders)
                    GROUP BY obs.internal_id_observation
                """.trimIndent()

                Log.d("getExportDbData", "Final Query: $query")
                val table = db.rawQuery(query, arrayOf(studyId.toString()) + traitNames).toTable()

                table.forEach { row ->
                    cursor.addRow(fieldList.map { row[it] } + traitRequiredFields.map {
                        when (it) {
                            "trait" -> row["observation_variable_name"]
                            "userValue" -> CategoryJsonUtil.processValue(row)
                            "timeTaken" -> row["observation_time_stamp"]
                            "person" -> row["collector"]
                            "location" -> row["geoCoordinates"]
                            "rep" -> row["rep"]
                            else -> String()
                        }
                    })
                }
            }
        }

        /**
         * This will print all observation data (if a trait is not observed it is null/empty string) and attributes for all observation units in a study.
         * The maxStatements parameter builds aggregate case statements for each variable, this way the output has column names that are one-to-one with variable names.
         * Where the column observation_variable_name value will be the observation value.
         * All observation_units are captured, even if they did not make an observation.
         * Inputs:
         * @param expId the field/study id
         * @param traits the list of traits to print, either all traits or just the active ones
         * @return a cursor that is used in CSVWriter and closed elsewhere
         */

        fun getExportTableData(
            context: Context,
            expId: Int,
            traits: ArrayList<TraitObject>
        ): Cursor? = withDatabase { db ->
            val headers = ObservationUnitAttributeDao.getAllNames(expId)

            val selectAttributes = headers.map { attributeName ->
                "MAX(CASE WHEN attr.observation_unit_attribute_name = '$attributeName' THEN vals.observation_unit_value_name ELSE NULL END) AS \"$attributeName\""
            }.joinToString(", ")

            val selectObservations = traits.map { trait ->
                val traitName = DataHelper.replaceIdentifiers(trait.name)
                "MAX(CASE WHEN obs.observation_variable_name='$traitName' THEN obs.value ELSE NULL END) AS \"$traitName\""
            }.joinToString(", ")

            val combinedSelection = listOf(selectAttributes, selectObservations).filter { it.isNotEmpty() }.joinToString(", ")

            // Construct the query
            val sortOrderClause = getSortOrderClause(context, expId.toString())
            val query = """
                SELECT units.internal_id_observation_unit AS id, $combinedSelection
                FROM observation_units AS units
                LEFT JOIN observation_units_values AS vals ON units.internal_id_observation_unit = vals.observation_unit_id
                LEFT JOIN observation_units_attributes AS attr ON vals.observation_unit_attribute_db_id = attr.internal_id_observation_unit_attribute
                LEFT JOIN observations AS obs ON units.observation_unit_db_id = obs.observation_unit_id
                WHERE units.study_id = $expId
                GROUP BY units.internal_id_observation_unit
                ORDER BY $sortOrderClause
            """.trimIndent()

            Log.d("getExportTableData", "Executing query: $query")
            db.rawQuery(query, null)
        }

        /**
         * Same as above, but filters out all observation unit attributes other than the unique identifier chosen when the study was loaded
         * @param expId the field/study id
         * @param uniqueName the preference string that user chooses on field selection for unique plot names
         * @param traits the list of traits to print, either all traits or just the active ones
         * @return a cursor that is used in CSVWriter and closed elsewhere
         */

        fun getExportTableDataShort(
            context: Context,
            expId: Int,
            uniqueName: String,
            traits: ArrayList<TraitObject>
        ): Cursor? {
            getExportTableData(context, expId, traits)?.use { cursor ->
                val matrixCursor = MatrixCursor(cursor.columnNames)
                while (cursor.moveToNext()) {
                    val rowData = arrayOfNulls<String>(cursor.columnCount)
                    for (i in rowData.indices) {
                        if (cursor.getColumnName(i) == uniqueName || traits.any { trait -> trait.name == cursor.getColumnName(i) }) {
                            rowData[i] = cursor.getString(i)
                        }
                    }
                    matrixCursor.addRow(rowData)
                }
                return matrixCursor
            }
            return null
        }

        /**
         * Same as above but filters by obs unit and trait format
         */
        fun convertDatabaseToTable(
            expId: Int,
            uniqueName: String,
            unit: String,
            col: Array<String?>,
            traits: Array<TraitObject>
        ): Cursor? = withDatabase { db ->

            val sanitizeTraits = traits.map { DataHelper.replaceIdentifiers(it.name) }
            val sanitizeFormats = traits.map { DataHelper.replaceIdentifiers(it.format) }

            val select = col.joinToString(",") { "props.'${DataHelper.replaceIdentifiers(it)}'" }

            val maxStatements = arrayListOf<String>()
            sanitizeTraits.forEachIndexed { index, it ->
                maxStatements.add(
                    "MAX (CASE WHEN o.observation_variable_name='$it' AND o.observation_variable_field_book_format='${sanitizeFormats[index]}' THEN o.value ELSE NULL END) AS '$it'"
                )
            }

            val query = """
                SELECT $select,
                ${maxStatements.joinToString(",\n")}
                FROM ObservationUnitProperty as props
                LEFT JOIN observations o ON props.`${uniqueName}` = o.observation_unit_id AND o.${Study.FK} = $expId
                WHERE props.`${uniqueName}` = "$unit"
                GROUP BY props.id
            """.trimIndent()

            db.rawQuery(query, null)
        }

        fun getSortedObservationUnitData(context: Context, studyId: Int): Cursor? = withDatabase { db ->
            val headers = ObservationUnitAttributeDao.getAllNames(studyId).filter { it != "geo_coordinates" }

            val selectStatement = headers.map { col ->
                "MAX(CASE WHEN attr.observation_unit_attribute_name = \"$col\" THEN vals.observation_unit_value_name ELSE NULL END) AS \"$col\""
            }.joinToString(", ")

            val orderByClause = getSortOrderClause(context, studyId.toString())

            val query = """
                SELECT ${if (selectStatement.isNotEmpty()) "$selectStatement, " else ""} units.internal_id_observation_unit AS id, units.geo_coordinates
                FROM ${ObservationUnit.tableName} AS units
                LEFT JOIN ${ObservationUnitValue.tableName} AS vals ON units.internal_id_observation_unit = vals.observation_unit_id
                LEFT JOIN ${ObservationUnitAttribute.tableName} AS attr ON vals.observation_unit_attribute_db_id = attr.internal_id_observation_unit_attribute
                WHERE units.study_id = $studyId
                GROUP BY units.internal_id_observation_unit
                ORDER BY $orderByClause
            """.trimIndent()

            Log.d("getSortedObsUnitData", "Executing dynamic query: $query")
            db.rawQuery(query, null)
        }


        private fun getSortOrderClause(context: Context, studyId: String): String = withDatabase { db ->
            val sortOrder = if (PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean("${GeneralKeys.SORT_ORDER}.$studyId", true)) "ASC" else "DESC"

            var sortCols = "internal_id_observation_unit" // Default sort column
            try {
                val sortName = db.query(
                    Study.tableName,
                    select = arrayOf("study_sort_name"),
                    where = "${Study.PK} = ?",
                    whereArgs = arrayOf(studyId)
                ).toFirst()["study_sort_name"]?.toString()

                if (!sortName.isNullOrEmpty() && sortName != "null") {
                    sortCols = sortName.split(',')
                        .joinToString(",") { col -> "cast(`$col` as integer), `$col`" } + ", internal_id_observation_unit"
                }
            } catch (e: Exception) {
                Log.e("ObsUnitPropertyDao", "Error fetching sort order for study: $e")
            }

            "$sortCols $sortOrder"
        } ?: "internal_id_observation_unit ASC" // Provide a default non-null value


    }
}
