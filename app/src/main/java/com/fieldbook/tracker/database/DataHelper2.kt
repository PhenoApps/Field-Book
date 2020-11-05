package com.fieldbook.tracker.database

import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.core.database.getBlobOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import com.fieldbook.tracker.database.DataHelper.TRAITS
import com.fieldbook.tracker.database.models.*
import com.fieldbook.tracker.objects.RangeObject
import com.fieldbook.tracker.objects.TraitObject
import java.text.SimpleDateFormat
import java.util.*
import kotlin.system.measureTimeMillis

/**
* This is a utility file for working with sqlite databases. The utility region features code to migrate
 * an old schema to a new schema, along with various functions for querying the data.
 *
 * This file also contains queries related to dynamic tables created within Field Book.
 *
 * author: Chaney
*/

typealias Row = Map<String, Any?>
typealias Table = MutableList<Row>


/**
 * TODO: RDBMS Constraints, in general, for brapIds should we allow them to be null: no, they are 'blank'?
 * also, should we force them to be unique? We can automatically do the :no, they don't need to be unique
 *      TODO: Remove cascade delete constraints, write manual cascade delete functions
 */
//region Utility functions

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

    } finally {

        db.endTransaction()

    }

    null

}

/**
 * Simple wrapper function that has default arguments.
 */
fun SQLiteDatabase.query(table: String, select: Array<String>? = null, where: String? = null, whereArgs: Array<String>? = null, orderBy: String? = null): Cursor {

    return this.query(table, select, where, whereArgs, null, null, orderBy)

}

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

//                println("ids: $attrIds")

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

//                            println("$rowid Inserting ${attrValue.key} = ${attrValue.value} at ${attrIds[attrValue.key]}")
//                        }
                    }
                }

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
 * Uses the createTables and selectAll functions to migrate a schema.
 * db: the SQLite database to use.
 * from: the table name to migrate from
 * to: the table renaming
 * columnDefs: map entries where keys are column names and values are column affinities
 * pattern: map entries where keys are original column names and values are new schema names
 */
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

/**
 * Simple extension function to create a contentValue object from a table row.
 */
private fun Row.toContentValues(): ContentValues = contentValuesOf(

        *entries.map { it.key to it.value }.toTypedArray()

)

/**
 * TODO: Write a column parsing function that is used within toFirst, toTable, and toMigrate
 * Cursor extension function that retrieves the first row in the cursor.
 */
fun Cursor.toFirst(): Map<String, Any?> = if (moveToFirst()) {

    val row = mutableMapOf<String, Any?>()

    try {

        columnNames.forEach {
            val index = getColumnIndexOrThrow(it)
            if (index > -1) {
                row[it] = when (getType(index)) {
                    //null field type
                    0 -> null
                    //int field type
                    1 -> getIntOrNull(index)
                    //string field type
                    //blob field type 3
                    else -> {
                        getStringOrNull(index) ?: getBlobOrNull(index).toString()
                    }
                }
            }

        }

    } catch (e: IllegalArgumentException) {

        e.printStackTrace()

    } catch (ie: IndexOutOfBoundsException) {

        ie.printStackTrace()
    }

    row

} else emptyMap()

/**
 * Similar to toFirst, but returns a list of rows instead of the first one.
 */
fun Cursor.toTable(): List<Map<String, Any?>> = if (moveToFirst()) {

    val table = mutableListOf<Map<String, Any?>>()

    try {

        do {

            val row = mutableMapOf<String, Any?>().withDefault { String() }

            columnNames.forEach {
                val index = getColumnIndex(it)
                if (index > -1) {
//                    <li>{@link #FIELD_TYPE_NULL}</li>
//                    *   <li>{@link #FIELD_TYPE_INTEGER}</li>
//                    *   <li>{@link #FIELD_TYPE_FLOAT}</li>
//                    *   <li>{@link #FIELD_TYPE_STRING}</li>
//                    *   <li>{@link #FIELD_TYPE_BLOB}</li>
//                    when (getType(index)) {
//                        0 -> println("null field type")
//                        1 -> println("int field type")
//                        2 -> println("string field type")
//                        3 -> println("blob field type")
//                    }
//                    println(getType(index))

                    row[it] = when (getType(index)) {
                        //null field type
                        0 -> null
                        //int field type
                        1 -> getIntOrNull(index)
                        //string field type
                        //blob field type 3
                        else -> {
                            getStringOrNull(index) ?: getBlobOrNull(index).toString()
                        }
//                        in IntegerColumns -> getIntOrNull(index)
//                        else -> {
//                            try {
//                                getStringOrNull(index)
//                            } catch (ie: IndexOutOfBoundsException) {
//                                ie.printStackTrace()
//                                String()
//                            }
//                        }
                    }
                }
            }

            table.add(row)

        } while (moveToNext())

    } catch (ie: IndexOutOfBoundsException) {
        ie.printStackTrace()
    } catch (e: IllegalArgumentException) {
        e.printStackTrace()
    } catch (noElem: NoSuchElementException) {
        noElem.printStackTrace()
    }

    table

} else mutableListOf()

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
                    row[colName] = when (getType(index)) {
                        0 -> null
                        1 -> getInt(index)
                        else -> getStringOrNull(index) ?: getBlobOrNull(index)
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

/**
 * Builds a create table statement based on given parameters:
 * name: name of table to be created
 * columnDefs: mapping between column name and affinity
 * compositeKey: simply the primary key to this table, e.g [internal_study_db_id INT PRIMARY KEY AUTOGENERATE]
 */
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

/**
 * A wrapper function to catch all backend exceptions.
 * This function should be used for all database calls.
 * This way there is a single point of failure if we get mutex access errors.
 */
inline fun <reified T> withDatabase(crossinline function: (SQLiteDatabase) -> T): T? = try {

    DataHelper.db?.let { database ->

        function(database)

    }

} catch (npe: NullPointerException) {
    //npe.printStackTrace()
    null
} catch (e: Exception) {
    e.printStackTrace()
    null
}

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

private val sTableNames = arrayOf(
        StudyModel.tableName,
        ObservationUnitModel.tableName,
        ObservationVariableModel.tableName,
        ObservationModel.tableName,
        ObservationVariableAttributeModel.tableName,
        ObservationVariableValueModel.tableName,
        ObservationUnitAttributeModel.tableName,
        ObservationUnitValueModel.tableName)

//endregion

//region Queries
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

//                println(newRowid)
//                    println("Inserting $newRowid $rowid")
            }
        }
    }
}

internal fun selectAllRange(): Array<Map<String, Any?>>? = withDatabase { db ->
    db.query(sObservationUnitPropertyViewName).toTable().toTypedArray()
}

fun getAllRangeId(): Array<String> = withDatabase { db ->
    val table = db.query(sObservationUnitPropertyViewName,
            select = arrayOf("id"),
            orderBy = "id").toTable()

    table.map { it["id"] as String }
            .toTypedArray()
} ?: emptyArray()

//TODO original code uses switchField if object is null
fun getRangeByIdAndPlot(firstName: String,
                        secondName: String,
                        uniqueName: String,
                        id: Int, pid: String): RangeObject? = withDatabase { db ->

    with(db.query(sObservationUnitPropertyViewName,
            select = arrayOf(firstName, secondName, uniqueName, "id").map { "`$it`" }.toTypedArray(),
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
                select = arrayOf("`$trait`"),
                where = "`$uniqueName` LIKE ?",
                whereArgs = arrayOf(plotId)).toTable().map {
            it[trait] as String
        }.toTypedArray()
    }

} ?: emptyArray()

fun getRangeColumnNames(): Array<String?> = withDatabase { db ->

    val cursor = db.rawQuery("SELECT * FROM $sObservationUnitPropertyViewName limit 1", null)
    //Cursor cursor = db.rawQuery("SELECT * from range limit 1", null);
    //Cursor cursor = db.rawQuery("SELECT * from range limit 1", null);
    var data: Array<String?>? = null

    if (cursor.moveToFirst()) {
        val i = cursor.columnCount - 1
        data = arrayOfNulls(i)
        var k = 0
        for (j in 0 until cursor.columnCount) {
            if (cursor.getColumnName(j) != "id") {
                data[k] = cursor.getColumnName(j).replace("//", "/")
                k += 1
            }
        }
    }

    if (!cursor.isClosed) {
        cursor.close()
    }

    data

} ?: emptyArray()

fun getRangeColumns(): Array<String?> = withDatabase { db ->

    val cursor = db.rawQuery("SELECT * from ${sObservationUnitPropertyViewName} limit 1", null)

    var data: Array<String?>? = null

    if (cursor.moveToFirst()) {
        val i = cursor.columnCount - 1
        data = arrayOfNulls(i)
        var k = 0
        for (j in 0 until cursor.columnCount) {
            if (cursor.getColumnName(j) != "id") {
                data[k] = cursor.getColumnName(j)
                k += 1
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

fun getVisibleTrait(): Array<ObservationVariableModel> = withDatabase { db ->

    val rows = db.query(sVisibleObservationVariableViewName,
            orderBy = "position").toTable()

    val variables: Array<ObservationVariableModel?> = arrayOfNulls(rows.size)

    rows.forEachIndexed { index, map ->

        variables[index] = ObservationVariableModel(map)

    }

    variables.mapNotNull { it }.toTypedArray()

} ?: emptyArray()

fun getFormat(): Array<String> = withDatabase { db ->

    db.query(sVisibleObservationVariableViewName,
            arrayOf(ObservationVariableModel.PK,
                    "observation_variable_field_book_format",
                    "position")).toTable().mapNotNull { row ->
        row["observation_variable_field_book_format"] as String
    }.toTypedArray()

} ?: emptyArray()

fun getExportDbData(uniqueName: String, fieldList: Array<String?>, traits: Array<String>): Cursor? = withDatabase { db ->

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
        SELECT ${fieldList.joinToString { "props.`$it` AS `$it`" }} ${if (fieldList.isNotEmpty()) "," else " "} 
            ${varSelectFields.joinToString { "vars.`$it` AS `$it`" }} ${if (varSelectFields.isNotEmpty()) "," else " "}
            ${obsSelectFields.joinToString { "obs.`$it` AS `$it`" }}
        FROM ${ObservationModel.tableName} AS obs, 
             $sObservationUnitPropertyViewName AS props, 
             ${ObservationVariableModel.tableName} AS vars
        WHERE obs.${ObservationUnitModel.FK} = props.`$uniqueName`
            AND obs.observation_variable_name = vars.observation_variable_name
            AND obs.observation_variable_field_book_format = vars.observation_variable_field_book_format
            AND obs.value IS NOT NULL 
            AND vars.observation_variable_name IN ${traits.map { "\"${it.replace("'", "''")}\"" }.joinToString(",", "(", ")")}
        
    """.trimIndent()

//        println(query)
        val traitRequiredFields = arrayOf("trait", "userValue", "timeTaken", "person", "location", "rep")

        val table = db.rawQuery(query, null).toTable()

        table.forEach { row ->
            cursor.addRow(fieldList.map { row[it] } + traitRequiredFields.map {
                when (it) {
                    "trait" -> row["observation_variable_name"]
                    "userValue" -> row["value"]
                    "timeTaken" -> row["observation_time_stamp"]
                    "person" -> row["collector"]
                    "location" -> row["geoCoordinates"]
                    else -> 2 //TODO: Trevor what do do with rep
                }
            })
        }
    }
}

fun convertDatabaseToTable(uniqueName: String, col: Array<String?>, traits: Array<String>): Cursor? = withDatabase { db ->

    val query: String
    val rangeArgs = arrayOfNulls<String>(col.size)
    val traitArgs = arrayOfNulls<String>(traits.size)
    var joinArgs = ""

    for (i in col.indices) {
        rangeArgs[i] = "prop.`${col[i]}`"
    }

    for (i in traits.indices) {
        traitArgs[i] = "m" + i + ".value AS '" + traits[i] + "'"
        joinArgs = (joinArgs + "LEFT JOIN observations m" + i + " ON prop.`$uniqueName`"
                + " = m" + i + ".observation_unit_id AND m" + i + ".observation_variable_name = '" + traits[i] + "' ")
    }

    query = "SELECT " + rangeArgs.joinToString(",") + " , " + traitArgs.joinToString(",") +
            " FROM $sObservationUnitPropertyViewName AS prop " + joinArgs + " GROUP BY prop.`$uniqueName` ORDER BY prop.id"

    println(query)

    db.rawQuery(query, null)
}

/**
 * Transpose obs. unit. attribute/values into a view based on the selected study.
 * On further testing, creating a view here is substantially faster but queries on the
 * view are ~4x the runtime as using a table.
 */
fun switchField(exp_id: Int) = withDatabase { db ->

    val headers: Array<String> = db.query(ObservationUnitAttributeModel.tableName,
            arrayOf("observation_unit_attribute_name"),
            where = "${StudyModel.FK} = ?",
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
        SELECT units.${ObservationUnitModel.PK} AS id $selectStatement
        FROM ${ObservationUnitModel.tableName} AS units
        LEFT JOIN ${ObservationUnitValueModel.tableName} AS vals ON units.${ObservationUnitModel.PK} = vals.${ObservationUnitModel.FK}
        LEFT JOIN ${ObservationUnitAttributeModel.tableName} AS attr on vals.${ObservationUnitAttributeModel.FK} = attr.${ObservationUnitAttributeModel.PK}
        WHERE units.${StudyModel.FK} = $exp_id
        GROUP BY units.${ObservationUnitModel.PK}""".trimMargin()

    println("$exp_id $query")

    println("New switch field time: ${
        measureTimeMillis {
            db.execSQL(query)
        }.toLong()
    }")
}

//endregion

fun deleteField(exp_id: Int) = withDatabase { db ->

    val deleteQuery = "DELETE FROM ${StudyModel.tableName} WHERE ${StudyModel.PK} = $exp_id"

//    val deleteQuery = sTableNames.joinToString(";") {
//        "DELETE FROM $it WHERE internal_study_db_id = $exp_id"
//    }

//    println(deleteQuery)

    db.execSQL(deleteQuery)

}

