package com.fieldbook.tracker.database

import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.content.contentValuesOf
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import com.fieldbook.tracker.brapi.BrAPIService
import com.fieldbook.tracker.brapi.Image
import com.fieldbook.tracker.brapi.Observation
import com.fieldbook.tracker.database.DataHelper.TRAITS
import com.fieldbook.tracker.database.models.*
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.RangeObject
import com.fieldbook.tracker.objects.TraitObject
import io.swagger.client.model.ObservationUnit
import io.swagger.client.model.ObservationVariable
import org.threeten.bp.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.*
import kotlin.NoSuchElementException
import kotlin.math.abs
import kotlin.math.exp

/**
* All database related functions are here
*/

typealias Row = Map<String, Any?>
typealias Table = MutableList<Row>


/**
 * TODO: RDBMS Constraints, in general, for brapIds should we allow them to be null: no, they are 'blank'?
 * also, should we force them to be unique? We can automatically do the :no, they don't need to be unique
 */

/**
 * Function called to drop entire refactored database schema.
 * Is also called before schema is created.
 */
private fun deleteTables(): Int = withDatabase { db ->

    sTableNames.forEach {

        dropTableStatement(it)

    }

    dropViewStatement(sVisibleObservationVariableViewName)
    dropViewStatement(sObservationUnitPropertyViewName)

    1

} ?: -1

/**
 * Used to select all rows from previous database schema.
 * Used in conjunction with toMigratedTable which applies the naming pattern.
 */
fun selectAll(name: String, pattern: Map<String, String>, function: (Table) -> Unit) {

    DataHelper.db?.let { db ->
        function(db.query(name, null,
                null,
                null,
                null,
                null,
                null,
                null).use { it.toMigratedTable(pattern) })

    }
}

/**
 * Creates Tables for new schema.
 * 1. deletes any old table that exists referencing new schema rows. e.g StudyModel
 * 2. Builds and runs a create table statement based on the table class.
 * 3. Queries the original db for exp_id rows
 * 4. Populates table based on previous query by translating
 *      column headers using a migration pattern mapping.
 */

//TODO reorganize try catches
fun createTables(traits: ArrayList<TraitObject>) {

    if (deleteTables() == 1) {

        withDatabase { db ->

            try {

                db.beginTransaction()

                with(StudyModel) {
                    migrateTo(db, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                with(ObservationUnitModel) {
                    migrateTo(db, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                with(ObservationUnitAttributeModel) {
                    migrateTo(db, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                with(ObservationUnitValueModel) {
                    migrateTo(db, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                with(ObservationVariableModel) {
                    migrateTo(db, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                with(ObservationVariableAttributeModel) {
                    db.execSQL(createTableStatement(tableName, columnDefs))
                }

                with(ObservationVariableValueModel) {
                    db.execSQL(createTableStatement(tableName, columnDefs))
                }

                //insert all default columns as observation variable attribute rows
                val attrIds = mutableMapOf<String, String>()

                ObservationVariableAttributeModel.defaultColumnDefs.asSequence().forEach { entry ->

                    val attrId = db.insert(ObservationVariableAttributeModel.tableName, null, contentValuesOf(
                            "observation_variable_attribute_name" to entry.key,
                    ))

                    //associate default column names with rowids of the attribute inserted
                    attrIds[entry.key] = attrId.toString()
                }

                println("ids: $attrIds")

                //iterate over all traits, insert observation variable values using the above mapping
                //old schema has extra columns in the trait table which are now bridged with attr/vals in the new schema
                traits.forEachIndexed { index, trait ->
                    //iterate trhough mapping of the old columns that are now attr/vals
                    mapOf(
                            "validValuesMin" to trait.minimum as String,
                            "validValuesMax" to trait.maximum as String,
                            "category" to trait.categories as String,
                    ).asSequence().forEach { attrValue ->

                        //TODO: commenting this out would create a sparse table from the unused attribute values
//                        if (attrValue.value.isNotEmpty()) {

                            val rowid = db.insert(ObservationVariableValueModel.tableName, null, contentValuesOf(

                                    ObservationVariableModel.FK to trait.id,
                                    ObservationVariableAttributeModel.FK to attrIds[attrValue.key],
                                    "observation_variable_attribute_value" to attrValue.value,
                            ))

                            println("$rowid Inserting ${attrValue.key} = ${attrValue.value} at ${attrIds[attrValue.key]}")
//                        }
                    }
                }

//                db.query(ObservationVariableValueModel.tableName).toTable().forEach {
//                    println(it)
//                }

                with(ObservationModel) {
                    migrateTo(db, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                db.execSQL(sVisibleObservationVariableView)

                db.setTransactionSuccessful()

            } catch (e: Exception) {

                e.printStackTrace()

            } finally {

                db.endTransaction()

            }
        }
    }
}

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
        StudyModel.FK to UUID.randomUUID().toString(),
        ObservationUnitModel.FK to unit.toInt(),
        ObservationVariableModel.FK to variable.toInt()))

/**
 * Inserts ten random observations per plot/trait pairs.
 */
private fun insertRandomObservations() {

    ObservationUnitModel.getAll().sliceArray(0 until 10).forEachIndexed { index, unit ->

        ObservationVariableModel.getAllTraitObjects().forEachIndexed { index, traitObject ->

            for (i in 0..10) {

                val obs = randomObservation(
                        unit.internal_id_observation_unit.toString(),
                        unit.observation_unit_db_id,
                        traitObject.format,
                        traitObject.trait,
                        traitObject.id)

                val newRowid = ObservationModel.insertObservation(obs)

                println(newRowid)
//                    println("Inserting $newRowid $rowid")
            }
        }
    }
}

private fun migrateTo(db: SQLiteDatabase, from: String, to: String, columnDefs: Map<String, String>, pattern: Map<String, String>) {

    val table = createTableStatement(to, columnDefs)

//    println(table)

    db.execSQL(table)

    selectAll(from, pattern) { models ->

        models.forEach {
//            println(it)
            try {
                db.insertWithOnConflict(to, null, it.toContentValues(), SQLiteDatabase.CONFLICT_IGNORE)
            } catch (constraint: SQLiteConstraintException) {
                //constraint.printStackTrace()
            } catch (exp: Exception) {
                exp.printStackTrace()
            }
        }
    }
}

private fun Row.toContentValues(): ContentValues = contentValuesOf(

        *entries.map { it.key to it.value }.toTypedArray()

)


fun Cursor.toFirst(): Map<String, Any?> = if (moveToFirst()) {

    val row = mutableMapOf<String, Any?>()

    try {

        columnNames.forEach {
            val index = getColumnIndexOrThrow(it)
            val colVal = getString(index) ?: ""
            row[it] = when (it) {
                in IntegerColumns -> getInt(index)
                else -> getStringOrNull(index)
            }

        }

    } catch (e: IllegalArgumentException) {

        e.printStackTrace()

    }

    row

} else emptyMap()

fun Cursor.toTable(): List<Map<String, Any?>> = if (moveToFirst()) {

    val table = mutableListOf<Map<String, Any?>>()

    try {

        do {

            val row = mutableMapOf<String, Any?>().withDefault { String() }

            columnNames.forEach {
                val index = getColumnIndexOrThrow(it)

                row[it] = when (it) {
                    in IntegerColumns -> getInt(index)
                    else -> getStringOrNull(index)
                }
            }

            table.add(row)

        } while (moveToNext())

    } catch (e: IllegalArgumentException) {
        e.printStackTrace()
    } catch (noElem: NoSuchElementException) {
        noElem.printStackTrace()
    }

    table

} else mutableListOf()

internal val IntegerColumns = arrayOf(StudyModel.PK, StudyModel.FK,
        ObservationUnitModel.PK, ObservationUnitAttributeModel.PK, ObservationUnitAttributeModel.FK,
        ObservationModel.PK, ObservationModel.FK,
        ObservationVariableAttributeModel.PK, ObservationVariableAttributeModel.FK,
        ObservationVariableModel.PK, ObservationVariableModel.FK,
        ObservationVariableValueModel.PK, ObservationVariableValueModel.FK, "position")

/**
 * Extension function that converts a cursor into a map with new column names using the pattern parameter.
 */
fun Cursor.toMigratedTable(pattern: Map<String, String>): MutableList<Map<String, Any?>> = if (moveToFirst()) {

    val table = mutableListOf<Map<String, Any?>>()

    try {

        do {

            val row = mutableMapOf<String, Any?>()

            columnNames.forEach {
                val index = getColumnIndexOrThrow(it)
                pattern.getOrElse(it) { null }?.let { colName ->
                    row[colName] = when (colName) {
                        in IntegerColumns -> getInt(index)
                        else -> getStringOrNull(index)
                    }
                }
            }

            table.add(row)

        } while (moveToNext())

    } catch (e: IllegalArgumentException) {
        e.printStackTrace()
    }

    table

} else mutableListOf()

private fun dropTableStatement(name: String) = """
    DROP TABLE IF EXISTS $name
""".trimIndent()

private fun dropViewStatement(name: String) = """
    DROP VIEW IF EXISTS $name
""".trimIndent()

private fun createTableStatement(name: String,
                                 columnDefs: Map<String, String>,
                                 compositeKey: String? = null,
                                 selectStatement: String? = null) = """
    CREATE TABLE IF NOT EXISTS $name ${
    columnDefs
            .map { col -> "${col.key} ${col.value}" }
            .joinToString(",", "(", compositeKey ?: "")
});
""".trimIndent()

inline fun <reified T> withDatabase(crossinline function: (SQLiteDatabase) -> T): T? = try {

    DataHelper.db?.let { database ->

        function(database)

    }

} catch (npe: NullPointerException) {
    npe.printStackTrace()
    null
} catch (e: Exception) {
    e.printStackTrace()
    null
}

/**
 * Use these within transactions.
 */
private val TRAITS = "traits"
private val USER_TRAITS = "user_traits"
private val USER_TRAITS_COLS = arrayOf("rid", "parent", "trait", "userValue", "timeTaken", "person", "location", "rep", "notes", "exp_id", "observation_db_id", "last_synced_time")

val sVisibleObservationVariableViewName = "VisibleObservationVariable"
val sVisibleObservationVariableView = """
    CREATE VIEW IF NOT EXISTS $sVisibleObservationVariableViewName 
    AS SELECT ${ObservationVariableModel.PK}, observation_variable_name, observation_variable_field_book_format, observation_variable_details, default_value, position
    FROM ${ObservationVariableModel.tableName} WHERE visible LIKE "true" ORDER BY position
""".trimIndent()

val sObservationUnitPropertyViewName = "ObservationUnitProperty"
val sImageObservationViewName = "ImageObservation"
val sImageObservationView = """
    CREATE VIEW IF NOT EXISTS $sImageObservationViewName     
    AS SELECT props.id, unit.observation_unit_db_id,
    study.study_alias,
    obs.${ObservationModel.PK}, obs.observation_time_stamp, obs.value, obs.observation_db_id, obs.last_synced_time, obs.collector,
    vars.external_db_id, vars.observation_variable_name, vars.observation_variable_details
    FROM ${ObservationModel.tableName} AS obs
    JOIN ${ObservationUnitModel.tableName} AS unit ON obs.${ObservationUnitModel.FK} = unit.${ObservationUnitModel.PK}
    JOIN $sObservationUnitPropertyViewName AS props ON obs.${ObservationUnitModel.FK} = unit.${ObservationUnitModel.PK}
    JOIN ${ObservationVariableModel.tableName} AS vars ON obs.${ObservationVariableModel.FK} = vars.${ObservationVariableModel.PK} 
    JOIN ${StudyModel.tableName} AS study ON obs.${StudyModel.FK} = study.${StudyModel.PK} 
    WHERE vars.observation_variable_field_book_format = 'photo'
""".trimIndent()

//private fun createImageObservationView(hostUrl: String): String = """
//    CREATE VIEW IF NOT EXISTS $sImageObservationViewName
//    AS SELECT props.id, unit.observation_unit_name,
//    vars.external_db_id, vars.trait,
//    obs.time_taken, obs.value,
//    exp_id.exp_alias,
//    user_traits.id, user_traits.observation_db_id, user_traits.last_synced_time, user_traits.person,
//    traits.details
//    FROM ${ObservationModel.tableName} AS obs
//    JOIN ${ObservationUnitModel.tableName} AS unit ON obs.${ObservationUnitModel.FK} = unit.${ObservationUnitModel.PK}
//    JOIN $sObservationUnitPropertyViewName AS props ON obs.${ObservationUnitModel.FK} = unit.${ObservationUnitModel.PK}
//    JOIN ${ObservationVariableModel.tableName} AS vars ON obs.${ObservationVariableModel.FK} = vars.${ObservationVariableModel.PK}
//    JOIN ${StudyModel.tableName} AS study ON obs.${StudyModel.FK} = study.${StudyModel.PK}
//    WHERE study.study_source IS NOT NULL
//    AND vars.trait_data_source = $hostUrl
//    AND obs.value <> ''
//    AND vars.trait_data_source IS NOT NULL
//    AND vars.observation_variable_field_book_format = 'photo'
//""".trimIndent()

val sUpdateTraitVisibility = """
    UPDATE $TRAITS SET isVisible = ? WHERE trait like ?
""".trimIndent()

val sInsertUserTrait = """
    INSERT INTO $USER_TRAITS ${USER_TRAITS_COLS.joinToString(",", "(", ")")}
    VALUES ${(USER_TRAITS_COLS.indices).joinToString(",", "(", ")") { "?" }}"
""".trimIndent()

private val sTableNames = arrayOf(
        StudyModel.tableName,
        ObservationUnitModel.tableName,
        ObservationVariableModel.tableName,
        ObservationModel.tableName,
        ObservationVariableAttributeModel.tableName,
        ObservationVariableValueModel.tableName,
        ObservationUnitAttributeModel.tableName,
        ObservationUnitValueModel.tableName)

//region Queries

//test function
internal fun selectAllRange(): Array<Map<String, Any?>>? = withDatabase { db ->
    db.query(sObservationUnitPropertyViewName).toTable().toTypedArray()
}

fun getAllRangeId(): Array<String> = withDatabase { db ->
    db.query(sObservationUnitPropertyViewName,
            orderBy = "id").toTable()
            .map { it["id"] as String }
            .toTypedArray()
} ?: emptyArray()

//TODO original code uses switchField if object is null
fun getRangeByIdAndPlot(firstName: String,
                        secondName: String,
                        uniqueName: String,
                        id: Int, pid: String): RangeObject? = withDatabase { db ->

    with(db.query(sObservationUnitPropertyViewName,
            select = arrayOf(firstName, secondName, uniqueName, "id"),
            where = "id = ? AND plot_id = ?",
            whereArgs = arrayOf(id.toString(), pid)
    ).toFirst()) {
        RangeObject().apply {
            range = this@with[firstName] as? String ?: ""
            plot = this@with[secondName] as? String ?: ""
            plot_id = this@with[uniqueName] as? String ?: ""
        }
    }

}

fun getRangeFromId(firstName: String, uniqueName: String, plot_id: String): String = withDatabase { db ->

    (db.query(sObservationUnitPropertyViewName,
            select = arrayOf(firstName),
            where = "$uniqueName LIKE ?",
            whereArgs = arrayOf(plot_id)).toFirst()[firstName] as? String) ?: ""

} ?: String()

fun getDropDownRange(uniqueName: String, trait: String, plotId: String): Array<String> = withDatabase { db ->

    if (trait.isBlank()) emptyArray()
    else {
        db.query(sObservationUnitPropertyViewName,
                select = arrayOf(trait),
                where = "$uniqueName LIKE ?",
                whereArgs = arrayOf(plotId)).toTable().map {
            it[trait] as String
        }.toTypedArray()
    }

} ?: emptyArray()

fun getRangeColumnNames(): Array<String> = withDatabase { db ->

    db.query(sObservationUnitPropertyViewName).columnNames
            .filter { it != "id" }
            .toTypedArray()

} ?: emptyArray()

fun getRangeColumns(): Array<String> = withDatabase { db ->

    db.query(sObservationUnitPropertyViewName).toFirst().keys
            .filter { it != "id" }.toTypedArray()

} ?: emptyArray<String>()

fun getVisibleTrait(): Array<ObservationVariableModel> = withDatabase { db ->

    val rows = db.query(sVisibleObservationVariableViewName,
            orderBy = "position").toTable()

    val variables: Array<ObservationVariableModel?> = arrayOfNulls(rows.size)

    rows.forEachIndexed { index, map ->

        variables[index] = ObservationVariableModel(map)

    }

    variables.mapNotNull { it }.toTypedArray()

} ?: emptyArray()

fun getExportDBData(fieldList: Array<String>, traits: Array<String>) {

}

fun getFormat(): Array<String> = withDatabase { db ->

    db.query(sVisibleObservationVariableViewName,
            arrayOf(ObservationVariableModel.PK,
                    "observation_variable_field_book_format",
                    "position")).toTable().mapNotNull { row ->
        row["observation_variable_field_book_format"] as String
    }.toTypedArray()

} ?: emptyArray()

//    private fun arrayToString(table: String, s: Array<String>): String? {
//        var value = ""
//        for (i in s.indices) {
//            value += if (table.length > 0) table + "." + DataHelper.TICK + s[i] + DataHelper.TICK else s[i]
//            if (i < s.size - 1) value += ","
//        }
//        return value
//    }
fun getExportDbData(uniqueName: String, fieldList: Array<String>, traits: Array<String>): Cursor? = withDatabase { db ->

    val traitRequiredFields = arrayOf("trait", "userValue", "timeTaken", "person", "location", "rep")
    val requiredFields = fieldList + traitRequiredFields
    MatrixCursor(requiredFields).also { cursor ->
//        "select " + fields + ", traits.trait, user_traits.userValue, " +
//                "user_traits.timeTaken, user_traits.person, user_traits.location, user_traits.rep" +
//                " from user_traits, range, traits where " +
//                "user_traits.rid = range." + TICK + ep.getString("ImportUniqueName", "") + TICK +
//                " and user_traits.parent = traits.trait and " +
//                "user_traits.trait = traits.format and user_traits.userValue is not null and " + activeTraits;

        val varSelectFields = arrayOf("observation_variable_name")
        val obsSelectFields = arrayOf("value", "observation_time_stamp", "collector", "geoCoordinates")
        val outputFields = fieldList + varSelectFields + obsSelectFields
        val query = """
        SELECT ${fieldList.joinToString { "props.`$it` AS `$it`" }}, 
            ${varSelectFields.joinToString { "vars.`$it` AS `$it`" }}, 
            ${obsSelectFields.joinToString { "obs.`$it` AS `$it`" }}
        FROM ${ObservationModel.tableName} AS obs, 
             $sObservationUnitPropertyViewName AS props, 
             ${ObservationVariableModel.tableName} AS vars
        WHERE obs.${ObservationUnitModel.FK} = props.`$uniqueName`
            AND obs.observation_variable_name = vars.observation_variable_name
            AND obs.observation_variable_field_book_format = vars.observation_variable_field_book_format
            AND obs.value IS NOT NULL 
            AND vars.observation_variable_name IN ${traits.map { "'$it'" }.joinToString(",", "(", ")")}
        
    """.trimIndent()


        //TODO: query returns an empty table



        println(query)
        val traitRequiredFields = arrayOf("trait", "userValue", "timeTaken", "person", "location", "rep")

        val table = db.rawQuery(query, null).toTable()

        table.forEach { row ->
            cursor.addRow(fieldList.map { row[it] } + traitRequiredFields.map { when(it) {
                "trait" -> row["observation_variable_name"]
                "userValue" -> row["value"]
                "timeTaken" -> row["observation_time_stamp"]
                "person" -> row["collector"]
                "location" -> row["geoCoordinates"]
                else -> 1 //TODO: what do do with rep
            } })
        }
    }
}

fun convertDatabaseToTable(uniqueName: String, col: Array<String>, traits: Array<String>): Cursor? = withDatabase { db ->

//    plot_id,row,column,plot,tray_row,tray_id,seed_id,seed_name,pedigree,plot_id,trait,userValue,timeTaken,person,location,rep

    val selectFields = col.map { "prop.'$it'" } + traits.mapIndexed { index, it -> "obs$index.value AS '$it'" }

    val query = """
        SELECT ${selectFields.joinToString(",")}
        FROM $sObservationUnitPropertyViewName AS prop
            ${
        traits.mapIndexed { index, it ->
            "LEFT JOIN observations AS obs$index ON prop.'$uniqueName' = obs$index.${ObservationUnitModel.FK} " +
                    "AND obs$index.observation_variable_name = '$it'"
        }.joinToString("\n")}
        GROUP BY prop.'$uniqueName'
        ORDER BY prop.id
    """.trimIndent()

    println(query)

    val outputFields = col + traits
    MatrixCursor(outputFields).also { cursor ->
        db.rawQuery(query, null).toTable().forEach { row ->
            cursor.addRow(outputFields.map { row[it] })
        }
    }

}

//todo returns study model not field object
//    fun getFieldObject(studyDbId: Int): StudyModel? = withDatabase { db ->
//
//        //Old version returns a count as well.
//        //order by exp_id
//        val model: StudyModel by db.query(StudyModel.tableName,
//                StudyModel.columnDefs.keys.toTypedArray(),
//                "study_db_id = ?", arrayOf("$studyDbId"))
//                .toTable().first()
//
//        model
//
//    }

//    fun getTraitExists(id: Int, parent: String, trait: String): Boolean = withDatabase { db ->
//
//
//    }

/**
 * Transpose obs. unit. attribute/values into a view based on the selected study.
 * On further testing, creating a view here is substantially faster but queries on the
 * view are ~4x the runtime as using a table.
 */
//TODO: check where switchField is used
fun switchField(exp_id: Int) = withDatabase { db ->

    val headers: Array<Any?> = db.query(ObservationUnitAttributeModel.tableName,
            arrayOf("observation_unit_attribute_name"),
            where = "${StudyModel.FK} = ?",
            whereArgs = arrayOf("$exp_id"))
            .toTable()
            .map { it["observation_unit_attribute_name"] }
            .mapNotNull { it }
            .distinct()
            .toTypedArray()

    //create a select statement based on the saved plot attribute names
    val select = headers.map { col ->

        "MAX(CASE WHEN attr.observation_unit_attribute_name = '$col' THEN vals.observation_unit_value_name ELSE NULL END) AS '$col'"

    }

    //delete the old table
    db.execSQL("DROP TABLE IF EXISTS $sObservationUnitPropertyViewName")

    /**
     * Creating a view here is faster, but
     * using a table gives better performance for getRangeByIdAndPlot query
     */
    val query = """
        CREATE TABLE IF NOT EXISTS $sObservationUnitPropertyViewName AS 
        SELECT units.${ObservationUnitModel.PK} AS id, ${select.joinToString(", ")}
        FROM ${ObservationUnitModel.tableName} AS units
        JOIN ${ObservationUnitValueModel.tableName} AS vals ON units.${ObservationUnitModel.PK} = vals.${ObservationUnitModel.FK}
        JOIN ${ObservationUnitAttributeModel.tableName} AS attr on vals.${ObservationUnitAttributeModel.FK} = attr.${ObservationUnitAttributeModel.PK}
        WHERE units.${StudyModel.FK} = $exp_id
        GROUP BY id""".trimMargin()


    db.execSQL(query)



//    println(query)

}

//endregion

fun deleteField(exp_id: Int) = withDatabase { db ->

    val deleteQuery = "DELETE FROM ${StudyModel.tableName} WHERE ${StudyModel.PK} = $exp_id"

//    val deleteQuery = sTableNames.joinToString(";") {
//        "DELETE FROM $it WHERE internal_study_db_id = $exp_id"
//    }

    println(deleteQuery)

    db.execSQL(deleteQuery)

}

fun getTime(): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZZZZZ",
        Locale.getDefault()).format(Calendar.getInstance().time)

//endregion
inline fun <reified T> transaction(crossinline function: (SQLiteDatabase) -> T): T? = withDatabase { db ->

    try {

        db.beginTransaction()

        val result = function(db)

        db.setTransactionSuccessful()

        return@withDatabase result

    } catch (e: Exception) {

        e.printStackTrace()

        db.endTransaction()

    } finally {

        db.endTransaction()

    }

    null

}

fun SQLiteDatabase.query(table: String, select: Array<String>? = null, where: String? = null, whereArgs: Array<String>? = null, orderBy: String? = null): Cursor {

    return this.query(table, select, where, whereArgs, null, null, orderBy)

}

