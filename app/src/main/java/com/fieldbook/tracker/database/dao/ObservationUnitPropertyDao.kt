package com.fieldbook.tracker.database.dao

import android.content.Context
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteDatabase
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
import com.fieldbook.tracker.traits.formats.presenters.UriPresenter
import com.fieldbook.tracker.utilities.export.ValueProcessorFormatAdapter
import com.fieldbook.tracker.utilities.sort.FieldSortSpec
import com.fieldbook.tracker.utilities.sort.FieldSorter

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
                arrayOf("trait", "userValue", "timeTaken", "person", "location", "rep", "photo_uri", "video_uri", "audio_uri")
            val requiredFields = fieldList + traitRequiredFields
            MatrixCursor(requiredFields).also { cursor ->
                val placeholders = traits.joinToString(", ") { it.id }

                //the sort columns are pivoted too, even when they aren't exported, so rows can be sorted by them
                val sortSpec = getSortSpec(context, db, studyId.toString())
                val unitAttributes = (fieldList.filterNotNull() + sortSpec.columns).distinct()

                val unitSelectAttributes = unitAttributes.joinToString(", ") { attributeName ->
                    "MAX(CASE WHEN attr.observation_unit_attribute_name = '$attributeName' THEN vals.observation_unit_value_name ELSE NULL END) AS \"$attributeName\""
                }

                val obsSelectAttributes =
                    arrayOf("value", "observation_time_stamp", "collector", "geo_coordinates", "rep", "photo_uri", "video_uri", "audio_uri")

                val varSelectAttributes =
                    arrayOf("observation_variable_name", "observation_variable_field_book_format")

                val query = """
                    SELECT $unitSelectAttributes, ${obsSelectAttributes.joinToString { "obs.`$it` AS `$it`" }}, ${varSelectAttributes.joinToString { "vars.`$it` AS `$it`" }}
                    FROM observations AS obs
                    LEFT JOIN observation_units AS units ON units.observation_unit_db_id = obs.observation_unit_id
                    LEFT JOIN observation_units_values AS vals ON units.internal_id_observation_unit = vals.observation_unit_id
                    LEFT JOIN observation_units_attributes AS attr ON vals.observation_unit_attribute_db_id = attr.internal_id_observation_unit_attribute
                    LEFT JOIN observation_variables AS vars ON vars.${ObservationVariable.PK} = obs.${ObservationVariable.FK}
                    WHERE obs.study_id = $studyId
                      AND vars.internal_id_observation_variable IN ($placeholders)
                    GROUP BY obs.internal_id_observation
                """.trimIndent()

                Log.d("getExportDbData", "Final Query: $query")
                val table = db.rawQuery(query, null).use { it.toTable() }
                val order = FieldSorter.order(table.size, sortSpec.keys) { row, key ->
                    table[row][sortSpec.keys[key].column]?.toString()
                }
                val attachedMediaUriPresenter = UriPresenter()
                order.map { table[it] }.forEach { row ->
                    cursor.addRow(fieldList.map { row[it] } + traitRequiredFields.map {
                        when (it) {
                            "trait" -> row["observation_variable_name"]
                            "userValue" -> {
                                val name = row["observation_variable_name"]
                                val trait = traits.find { t -> t.name == name }
                                val value = row["value"]
                                if (trait == null) {
                                    value
                                } else processor.processValue(value.toString(), trait)
                            }
                            "timeTaken" -> row["observation_time_stamp"]
                            "person" -> row["collector"]
                            "location" -> row["geo_coordinates"]
                            "rep" -> row["rep"]
                            "photo_uri" -> (row["photo_uri"] as? String)?.takeIf { it.isNotEmpty() }?.let { attachedMediaUriPresenter.represent(context, it) } ?: ""
                            "video_uri" -> (row["video_uri"] as? String)?.takeIf { it.isNotEmpty() }?.let { attachedMediaUriPresenter.represent(context, it) } ?: ""
                            "audio_uri" -> (row["audio_uri"] as? String)?.takeIf { it.isNotEmpty() }?.let { attachedMediaUriPresenter.represent(context, it) } ?: ""
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
                val traitRequiredFields = arrayOf("trait", "userValue", "timeTaken", "person", "location", "rep", "photo_uri", "video_uri", "audio_uri")
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

            val coreColumns = listOf("primary_id", "secondary_id", "observation_unit_db_id", "geo_coordinates")

            val selectAttributes = headers.joinToString(", ") { attributeName ->
                if (attributeName in coreColumns) {
                    "MAX(units.\"$attributeName\") AS \"$attributeName\""
                } else {
                    "MAX(CASE WHEN attr.observation_unit_attribute_name = '$attributeName' THEN vals.observation_unit_value_name ELSE NULL END) AS \"$attributeName\""
                }
            }

            val traitNames = traits.map { DataHelper.replaceIdentifiers(it.name) }

            val selectObservations = traits.joinToString(", ") { trait ->
                val traitName = DataHelper.replaceIdentifiers(trait.name)
                "MAX(CASE WHEN vars.internal_id_observation_variable='${trait.id}' THEN obs.value ELSE NULL END) AS \"$traitName\""
            }

            val combinedSelection = listOf(selectAttributes, selectObservations).filter { it.isNotEmpty() }.joinToString(", ")

            val sortSpec = getSortSpec(context, db, studyId.toString())
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
                ORDER BY units.internal_id_observation_unit
            """.trimIndent()

            Log.d("getExportTableData", "Executing query: $query")
            val cursor = db.rawQuery(query, null)

            val rows = mutableListOf<Array<String?>>()
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
                        row.add(processor.processValue(value, trait))
                    } else {
                        row.add(value)
                    }
                }
                rows.add(row.toTypedArray())
            }

            val matrixCursor = MatrixCursor(cursor.columnNames, rows.size).also { matrix ->
                sortRows(rows, cursor.columnNames, sortSpec).forEach { matrix.addRow(it) }
            }

            cursor.close()
            matrixCursor
        }

        /**
         * Lightweight variant of getExportTableData for use by DataGridActivity only.
         *
         * Skips the ValueProcessorFormatAdapter pass (which calls getTraitByName() per cell).
         * This eliminates O(rows × traits) extra DB queries that the export path requires for
         * value formatting, but which the DataGrid doesn't need.
         * Rows are still copied into a MatrixCursor, since the study's sort is applied in memory.
         *
         * The caller is responsible for closing the returned cursor.
         */
        fun getDataGridTableData(
            context: Context,
            studyId: Int,
            traits: ArrayList<TraitObject>,
            requiredAttributes: List<String> = emptyList()
        ): Cursor? = withDatabase { db ->
            // Use only the requested attributes (unique id + row header + extra headers).
            // Fall back to all attributes only if none were specified (e.g. legacy callers).
            // The study's sort column(s) are also pulled in even if not requested, since the
            // rows are sorted by their values after the query.
            val baseHeaders: List<String> = if (requiredAttributes.isNotEmpty()) requiredAttributes
                          else ObservationUnitAttributeDao.getAllNames(studyId).toList()
            val sortSpec = getSortSpec(context, db, studyId.toString())
            val headers: List<String> = (baseHeaders + sortSpec.columns).distinct()

            val coreColumns = listOf("primary_id", "secondary_id", "observation_unit_db_id", "geo_coordinates")

            val selectAttributes = headers.joinToString(", ") { attributeName ->
                if (attributeName in coreColumns) {
                    "MAX(units.\"$attributeName\") AS \"$attributeName\""
                } else {
                    "MAX(CASE WHEN attr.observation_unit_attribute_name = '$attributeName' THEN vals.observation_unit_value_name ELSE NULL END) AS \"$attributeName\""
                }
            }

            val selectObservations = traits.joinToString(", ") { trait ->
                val traitName = DataHelper.replaceIdentifiers(trait.name)
                "MAX(CASE WHEN vars.internal_id_observation_variable='${trait.id}' THEN obs.value ELSE NULL END) AS \"$traitName\""
            }

            val combinedSelection = listOf(selectAttributes, selectObservations)
                .filter { it.isNotEmpty() }
                .joinToString(", ")

            val query = """
                SELECT $combinedSelection
                FROM observation_units AS units
                LEFT JOIN observation_units_values AS vals ON units.internal_id_observation_unit = vals.observation_unit_id
                LEFT JOIN observation_units_attributes AS attr ON vals.observation_unit_attribute_db_id = attr.internal_id_observation_unit_attribute
                LEFT JOIN observations AS obs ON units.observation_unit_db_id = obs.observation_unit_id AND obs.study_id = ?
                LEFT JOIN observation_variables AS vars ON vars.${ObservationVariable.PK} = obs.${ObservationVariable.FK}
                WHERE units.study_id = ?
                GROUP BY units.internal_id_observation_unit
                ORDER BY units.internal_id_observation_unit
            """.trimIndent()

            db.rawQuery(query, arrayOf(studyId.toString(), studyId.toString())).toSortedMatrixCursor(sortSpec)
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

        /** The study's observation units in sort order, with an id column and a column for each sort attribute. */
        fun getSortedObservationUnitData(context: Context, studyId: Int): Cursor? = withDatabase { db ->
            val sortSpec = getSortSpec(context, db, studyId.toString())

            val selectStatement = selectAttributeColumns(sortSpec.columns.filter { it != "geo_coordinates" })

            val query = """
                SELECT ${if (selectStatement.isNotEmpty()) "$selectStatement, " else ""} units.internal_id_observation_unit AS id, units.geo_coordinates
                FROM ${ObservationUnit.tableName} AS units
                LEFT JOIN ${ObservationUnitValue.tableName} AS vals ON units.internal_id_observation_unit = vals.observation_unit_id
                LEFT JOIN ${ObservationUnitAttribute.tableName} AS attr ON vals.observation_unit_attribute_db_id = attr.internal_id_observation_unit_attribute
                WHERE units.study_id = $studyId
                GROUP BY units.internal_id_observation_unit
                ORDER BY units.internal_id_observation_unit
            """.trimIndent()

            Log.d("getSortedObsUnitData", "Executing dynamic query: $query")

            db.rawQuery(query, null).toSortedMatrixCursor(sortSpec)
        }

        /**
         * Every observation unit of a study in import order, with the value of each of [attributes] in the same order.
         * Used to preview a sort before it is saved.
         */
        fun getAttributeValues(studyId: Int, attributes: List<String>): List<Array<String?>> = withDatabase { db ->

            if (attributes.isEmpty()) return@withDatabase emptyList()

            val query = """
                SELECT ${selectAttributeColumns(attributes)}
                FROM ${ObservationUnit.tableName} AS units
                LEFT JOIN ${ObservationUnitValue.tableName} AS vals ON units.internal_id_observation_unit = vals.observation_unit_id
                LEFT JOIN ${ObservationUnitAttribute.tableName} AS attr ON vals.observation_unit_attribute_db_id = attr.internal_id_observation_unit_attribute
                WHERE units.study_id = ?
                GROUP BY units.internal_id_observation_unit
                ORDER BY units.internal_id_observation_unit
            """.trimIndent()

            db.rawQuery(query, arrayOf(studyId.toString())).use { cursor ->
                val rows = ArrayList<Array<String?>>(cursor.count)
                while (cursor.moveToNext()) {
                    rows.add(Array(attributes.size) { cursor.getStringOrNull(it) })
                }
                rows
            }
        } ?: emptyList()

        fun getSortSpec(context: Context, studyId: Int): FieldSortSpec = withDatabase { db ->
            getSortSpec(context, db, studyId.toString())
        } ?: FieldSortSpec()

        /**
         * The study's sort, from study_sort_name.
         * A legacy comma separated column list takes its direction from the per-field preference it was saved with.
         */
        private fun getSortSpec(context: Context, db: SQLiteDatabase, studyId: String): FieldSortSpec {
            val sortName = db.query(
                Study.tableName,
                select = arrayOf("study_sort_name"),
                where = "${Study.PK} = ?",
                whereArgs = arrayOf(studyId)
            ).use { it.toFirst()["study_sort_name"]?.toString() }

            val legacyAscending = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean("${GeneralKeys.SORT_ORDER}.$studyId", true)

            return FieldSortSpec.parse(sortName, legacyAscending)
        }

        /** Pivots each attribute into a column of the same name, for queries joining units to vals and attr. */
        private fun selectAttributeColumns(attributes: List<String>) = attributes.joinToString(", ") { name ->
            val literal = name.replace("'", "''")
            val alias = name.replace("\"", "\"\"")
            "MAX(CASE WHEN attr.observation_unit_attribute_name = '$literal' THEN vals.observation_unit_value_name ELSE NULL END) AS \"$alias\""
        }

        /** Reorders [rows] by [sortSpec], reading each key from the column of the same name. Keys without a column are skipped. */
        private fun sortRows(rows: List<Array<String?>>, columns: Array<String>, sortSpec: FieldSortSpec): List<Array<String?>> {
            val keys = sortSpec.keys.filter { it.column in columns }
            val keyColumns = keys.map { columns.indexOf(it.column) }
            return FieldSorter.order(rows.size, keys) { row, key -> rows[row][keyColumns[key]] }.map { rows[it] }
        }

        /**
         * Copies this cursor into a MatrixCursor in [sortSpec] order and closes it.
         * Rows are read sequentially, since jumping around a large SQLite cursor re-runs the query for each window it loads.
         */
        private fun Cursor.toSortedMatrixCursor(sortSpec: FieldSortSpec): MatrixCursor = use { cursor ->
            val columns = cursor.columnNames
            val rows = ArrayList<Array<String?>>(cursor.count)
            while (cursor.moveToNext()) {
                rows.add(Array(columns.size) { cursor.getStringOrNull(it) })
            }
            MatrixCursor(columns, rows.size).also { matrix ->
                sortRows(rows, columns, sortSpec).forEach { matrix.addRow(it) }
            }
        }
    }
}
