package com.fieldbook.tracker.database.dao

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.util.Log
import androidx.core.database.getStringOrNull
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.Migrator.Companion.sObservationUnitPropertyViewName
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
import com.fieldbook.tracker.utilities.export.ValueProcessorFormatAdapter

class ObservationUnitPropertyDao {

    companion object {

        fun getObservationUnitPropertyByUniqueId(uniqueName: String, column: String, uniqueId: String): String = withDatabase { db ->
            db.query(sObservationUnitPropertyViewName, select = arrayOf(column), where = "`${uniqueName}` = ?", whereArgs = arrayOf(uniqueId))
                    .toFirst()[column].toString()
        }?: ""

        fun getAllRangeId(context: Context, studyId: Int): Array<Int> {

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

            RangeObject().apply {

                val model = db.query(sObservationUnitPropertyViewName,
                    where = "id = ?",
                    whereArgs = arrayOf(id.toString())).toFirst()

                primaryId = model[firstName].toString()

                secondaryId = model[secondName].toString()

                uniqueId = model[uniqueName].toString()
            }

        } ?: RangeObject().apply {
            primaryId = ""
            secondaryId = ""
            uniqueId = ""
        }

        /**
         * Returns all string values for a given column in the observation unit property table.
         */
        fun getObservationUnitPropertyValues(uniqueName: String, column: String, plotId: String): String = withDatabase { db ->

            val sanitizedUniqueName = "`${DataHelper.replaceIdentifiers(uniqueName)}`"
            val sanitizedColumn = "`${DataHelper.replaceIdentifiers(column)}`"
            db.rawQuery(
                """
                    SELECT $sanitizedColumn 
                    FROM $sObservationUnitPropertyViewName 
                    WHERE $sanitizedUniqueName = ? 
                    LIMIT 1
                """.trimIndent(),
                arrayOf(plotId)
            ).use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else String()
            }
        } ?: String()

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
            context: Context,
            studyId: Int,
            fieldList: Array<String?>,
            traits: ArrayList<TraitObject>,
            processor: ValueProcessorFormatAdapter
        ): Cursor? = withDatabase { db ->

            val traitRequiredFields =
                arrayOf("trait", "userValue", "timeTaken", "person", "location", "rep")
            val requiredFields = fieldList + traitRequiredFields
            MatrixCursor(requiredFields).also { cursor ->
                val placeholders = traits.joinToString(", ") { "?" }
                val traitNames = traits.map { DataHelper.replaceIdentifiers(it.name) }.toTypedArray()

                val unitSelectAttributes = fieldList.joinToString(", ") { attributeName ->
                    "MAX(CASE WHEN attr.observation_unit_attribute_name = '$attributeName' THEN vals.observation_unit_value_name ELSE NULL END) AS \"$attributeName\""
                }

                val obsSelectAttributes =
                    arrayOf("value", "observation_time_stamp", "collector", "geo_coordinates", "rep")

                val varSelectAttributes =
                    arrayOf("observation_variable_name", "observation_variable_field_book_format")

                val sortOrderClause = getSortOrderClause(context, studyId.toString())
                val query = """
                    SELECT $unitSelectAttributes, ${obsSelectAttributes.joinToString { "obs.`$it` AS `$it`" }}, ${varSelectAttributes.joinToString { "vars.`$it` AS `$it`" }}
                    FROM observations AS obs
                    LEFT JOIN observation_units AS units ON units.observation_unit_db_id = obs.observation_unit_id
                    LEFT JOIN observation_units_values AS vals ON units.internal_id_observation_unit = vals.observation_unit_id
                    LEFT JOIN observation_units_attributes AS attr ON vals.observation_unit_attribute_db_id = attr.internal_id_observation_unit_attribute
                    LEFT JOIN observation_variables AS vars ON vars.${ObservationVariable.PK} = obs.${ObservationVariable.FK}
                    WHERE obs.study_id = ?
                      AND vars.observation_variable_name IN ($placeholders)
                    GROUP BY obs.internal_id_observation
                    $sortOrderClause
                """.trimIndent()

                Log.d("getExportDbData", "Final Query: $query")
                val table = db.rawQuery(query, arrayOf(studyId.toString()) + traitNames).toTable()

                table.forEach { row ->
                    cursor.addRow(fieldList.map { row[it] } + traitRequiredFields.map {
                        when (it) {
                            "trait" -> row["observation_variable_name"]
                            "userValue" -> {
                                val value = row["value"]
                                val format = row["observation_variable_field_book_format"]
                                processor.processValue(value.toString(), format.toString())
                            }
                            "timeTaken" -> row["observation_time_stamp"]
                            "person" -> row["collector"]
                            "location" -> row["geo_coordinates"]
                            "rep" -> row["rep"]
                            else -> String()
                        }
                    })
                }
            }
        }


        /**
         * Same as database export function above, but filters all all attribute columns other than the unique id
         * (The other attributes need to be retrieved for sorting, but can then be discarded)
         */
        fun getExportDbDataShort(
            context: Context,
            studyId: Int,
            fieldList: Array<String?>,
            uniqueName: String,
            traits: ArrayList<TraitObject>,
            processor: ValueProcessorFormatAdapter
        ): Cursor? {
            // Get the full data set
            getExportDbData(context, studyId, fieldList, traits, processor)?.use { fullCursor ->
                val traitRequiredFields = arrayOf("trait", "userValue", "timeTaken", "person", "location", "rep")
                val requiredColumns = mutableListOf(uniqueName).apply {
                    addAll(traitRequiredFields)
                }.toTypedArray()

                // Create and return a cursor that only includes the required columns
                val matrixCursor = MatrixCursor(requiredColumns)

                while (fullCursor.moveToNext()) {

                    val row = arrayListOf<String?>()

                    fullCursor.columnNames.forEachIndexed { index, col ->

                        try {

                            if (col in requiredColumns) {

                                row.add(fullCursor.getStringOrNull(index))

                            }

                        } catch (e: Exception) {

                            e.printStackTrace()

                        }
                    }

                    matrixCursor.addRow(row)

                }

                // Return a cursor that only includes the required columns
                return matrixCursor
            }
            // Return null if the original data retrieval fails
            return null
        }

        /**
         * This will print all observation data (if a trait is not observed it is null/empty string) and attributes for all observation units in a study.
         * The maxStatements parameter builds aggregate case statements for each variable, this way the output has column names that are one-to-one with variable names.
         * Where the column observation_variable_name value will be the observation value.
         * All observation_units are captured, even if they did not make an observation.
         * Inputs:
         * @param studyId the field/study id
         * @param traits the list of traits to print, either all traits or just the active ones
         * @return a cursor that is used in CSVWriter and closed elsewhere
         */
        fun getExportTableData(
            context: Context,
            studyId: Int,
            traits: ArrayList<TraitObject>,
            processor: ValueProcessorFormatAdapter
        ): Cursor? = withDatabase { db ->
            val headers = ObservationUnitAttributeDao.getAllNames(studyId)

            val selectAttributes = headers.joinToString(", ") { attributeName ->
                "MAX(CASE WHEN attr.observation_unit_attribute_name = '$attributeName' THEN vals.observation_unit_value_name ELSE NULL END) AS \"$attributeName\""
            }

            val traitNames = traits.map { DataHelper.replaceIdentifiers(it.name) }

            val selectObservations = traits.joinToString(", ") { trait ->
                val traitName = DataHelper.replaceIdentifiers(trait.name)
                "MAX(CASE WHEN vars.internal_id_observation_variable='${trait.id}' THEN obs.value ELSE NULL END) AS \"$traitName\""
            }

            val combinedSelection = listOf(selectAttributes, selectObservations).filter { it.isNotEmpty() }.joinToString(", ")

            val orderByClause = getSortOrderClause(context, studyId.toString())
            //                SELECT units.internal_id_observation_unit AS id, $combinedSelection
            val query = """
                SELECT $combinedSelection, observation_variable_field_book_format
                FROM observation_units AS units
                LEFT JOIN observation_units_values AS vals ON units.internal_id_observation_unit = vals.observation_unit_id
                LEFT JOIN observation_units_attributes AS attr ON vals.observation_unit_attribute_db_id = attr.internal_id_observation_unit_attribute
                LEFT JOIN observations AS obs ON units.observation_unit_db_id = obs.observation_unit_id AND obs.study_id = $studyId
                LEFT JOIN observation_variables AS vars ON vars.${ObservationVariable.PK} = obs.${ObservationVariable.FK}
                WHERE units.study_id = $studyId
                GROUP BY units.internal_id_observation_unit
                $orderByClause
            """.trimIndent()

            Log.d("getExportTableData", "Executing query: $query")
            val cursor = db.rawQuery(query, null)

            val matrixCursor = MatrixCursor(cursor.columnNames).also {
                while (cursor.moveToNext()) {
                    val row = mutableListOf<String?>()
                    for (i in 0 until cursor.columnCount) {
                        val columnName = cursor.getColumnName(i)
                        val value = cursor.getStringOrNull(i)
                        if (value != null && columnName in traitNames) {
                            // Process trait values using the processor
                            // must get obs trait by name here since the table query has multiple traits per row
                            val trait = ObservationVariableDao.getTraitByName(columnName)
                            if (trait == null) {
                                Log.w("getExportTableData", "Trait not found for column: $columnName")
                                row.add(value)
                                continue
                            }
                            row.add(processor.processValue(value, trait.format))
                        } else {
                            row.add(value)
                        }
                    }
                    it.addRow(row.toTypedArray())
                }
            }

            cursor.close()
            matrixCursor
        }

        /**
         * Same as table export function above, but filters all all attribute columns other than the unique id
         * (The other attributes need to be retrieved for sorting, but can then be discarded)
         */

        fun getExportTableDataShort(
            context: Context,
            studyId: Int,
            uniqueName: String,
            traits: ArrayList<TraitObject>,
            processor: ValueProcessorFormatAdapter
        ): Cursor? {

            getExportTableData(context, studyId, traits, processor)?.use { cursor ->

                val requiredTraits = traits.map { it.name }.toTypedArray()
                val requiredColumns = arrayOf(uniqueName) + requiredTraits
                val matrixCursor = MatrixCursor(requiredColumns)

                //subtract one for the additional field book format for the value processor
                val traitStartIndex = cursor.columnCount - requiredTraits.size - 1

                while (cursor.moveToNext()) {

                    val rowData = mutableListOf<String?>()

                    val uniqueIndex = cursor.getColumnIndex(uniqueName)

                    //add the unique id
                    rowData.add(cursor.getString(uniqueIndex))

                    //skip ahead to the traits and add all trait values
                    requiredTraits.forEachIndexed { index, s ->

                        try {

                            val obsValue = cursor.getStringOrNull(index + traitStartIndex)

                            rowData.add(obsValue)

                        } catch (e: Exception) {

                            e.printStackTrace()

                        }

                    }

                    matrixCursor.addRow(rowData.toTypedArray())
                }

                return matrixCursor

            }

            return null
        }

        /**
         * Same as above but filters by obs unit
         */
        fun convertDatabaseToTable(
            studyId: Int,
            uniqueName: String,
            unit: String,
            col: Array<String?>,
            traits: Array<TraitObject>
        ): Cursor? = withDatabase { db ->

            val sanitizeTraits = traits.map { DataHelper.replaceIdentifiers(it.name) }

            val select = col.joinToString(",") { "props.'${DataHelper.replaceIdentifiers(it)}'" }

            val maxStatements = arrayListOf<String>()
            sanitizeTraits.forEachIndexed { index, it ->
                maxStatements.add(
                    "MAX (CASE WHEN vars.internal_id_observation_variable = ${traits[index].id} THEN o.value ELSE NULL END) AS '$it'"
                )
            }

            if (select.isEmpty() && maxStatements.isEmpty()) return@withDatabase null

            val query = """
                SELECT ${if (select.isNotEmpty()) select else "props.id"}
                ${if (maxStatements.isNotEmpty()) maxStatements.joinToString(",\n", ",") else String()}
                FROM ObservationUnitProperty as props
                LEFT JOIN observations o ON props.`${uniqueName}` = o.observation_unit_id AND o.${Study.FK} = $studyId
                LEFT JOIN ${ObservationVariable.tableName} AS vars ON vars.${ObservationVariable.PK} = o.${ObservationVariable.FK}
                WHERE props.`${uniqueName}` = "$unit"
                GROUP BY props.id
            """.trimIndent()

            Log.d("convertDatabaseToTable", "Executing query: $query")

            db.rawQuery(query, null)
        }

        fun getSortedObservationUnitData(context: Context, studyId: Int): Cursor? = withDatabase { db ->
            val headers = ObservationUnitAttributeDao.getAllNames(studyId).filter { it != "geo_coordinates" }

            val selectStatement = headers.joinToString(", ") { col ->
                "MAX(CASE WHEN attr.observation_unit_attribute_name = \"$col\" THEN vals.observation_unit_value_name ELSE NULL END) AS \"$col\""
            }

            val orderByClause = getSortOrderClause(context, studyId.toString())

            val query = """
                SELECT ${if (selectStatement.isNotEmpty()) "$selectStatement, " else ""} units.internal_id_observation_unit AS id, units.geo_coordinates
                FROM ${ObservationUnit.tableName} AS units
                LEFT JOIN ${ObservationUnitValue.tableName} AS vals ON units.internal_id_observation_unit = vals.observation_unit_id
                LEFT JOIN ${ObservationUnitAttribute.tableName} AS attr ON vals.observation_unit_attribute_db_id = attr.internal_id_observation_unit_attribute
                WHERE units.study_id = $studyId
                GROUP BY units.internal_id_observation_unit
                $orderByClause
            """.trimIndent()

//            Log.d("getSortedObsUnitData", "Executing dynamic query: $query")
            db.rawQuery(query, null)
        }

        private fun getSortOrderClause(context: Context, studyId: String): String? = withDatabase { db ->
            val sortOrder = if (PreferenceManager.getDefaultSharedPreferences(context)
                    .getBoolean("${GeneralKeys.SORT_ORDER}.$studyId", true)) "ASC" else "DESC"

            var sortCols = "" // Adjusted to start as an empty string
            val sortName = db.query(
                Study.tableName,
                select = arrayOf("study_sort_name"),
                where = "${Study.PK} = ?",
                whereArgs = arrayOf(studyId)
            ).toFirst()["study_sort_name"]?.toString()

            if (!sortName.isNullOrEmpty() && sortName != "null") {
                sortCols = sortName.split(',')
                    .joinToString(",") { col -> "cast(`$col` as integer), `$col`" } + (if (sortCols.isNotEmpty()) ", " else "")
            }

            if (sortCols.isNotEmpty()) "ORDER BY $sortCols COLLATE NOCASE $sortOrder" else ""
        }
    }
}
