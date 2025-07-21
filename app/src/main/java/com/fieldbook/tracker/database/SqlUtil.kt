package com.fieldbook.tracker.database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.core.database.getBlobOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
import org.threeten.bp.format.DateTimeFormatter
import java.text.SimpleDateFormat
import java.util.*

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

/**
 * Wrapper that doesn't sanitize select arguments, for cases when nested queries or SQL internal functions are used.
 * Note: parameters should be sanitized manually when calling this function.
 */
fun SQLiteDatabase.queryForMax(table: String, select: Array<String>? = null, where: String? = null, whereArgs: Array<String>? = null, orderBy: String? = null): Cursor {

    return this.query(table, select, where, whereArgs, null, null, orderBy)

}

/**
 * Simple wrapper function that has default arguments.
 */
fun SQLiteDatabase.query(table: String, select: Array<String>? = null, where: String? = null, whereArgs: Array<String>? = null, orderBy: String? = null): Cursor {

    return this.query(table, select?.map { "`$it`" }?.toTypedArray(), where, whereArgs, null, null, orderBy)

}

/**
 * Simple extension function to create a contentValue object from a table row.
 */
fun Row.toContentValues(): ContentValues = contentValuesOf(

        *entries.map { it.key to it.value }.toTypedArray()

)

/**
 * TODO: Write a column parsing function that is used within toFirst, toTable, and toMigrate
 * Cursor extension function that retrieves the first row in the cursor.
 */
fun Cursor.toFirst(): Map<String, Any?> = if (moveToFirst()) {

    val row = mutableMapOf<String, Any?>()

    columnNames.forEachIndexed { index, _ ->
        row[getColumnName(index)] = when (getType(index)) {
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

    close()

    row

} else {

    close()

    emptyMap()
}

/**
 * Similar to toFirst, but returns a list of rows instead of the first one.
 */
fun Cursor.toTable(): List<Map<String, Any?>> = if (moveToFirst()) {

    val table = mutableListOf<Map<String, Any?>>()

    do {

        val row = mutableMapOf<String, Any?>().withDefault { String() }

        columnNames.forEachIndexed { index, _ ->
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

            row[getColumnName(index)] = when (getType(index)) {
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

        table.add(row)

    } while (moveToNext())

    close()

    table

} else {

    close()

    mutableListOf()
}

/**
 * Previously this function would try to capture all errors,
 * now we expect exceptions to be handled whenever using this function s.a activity code or BrapiService
 */
fun <T> withDatabase(function: (SQLiteDatabase) -> T): T? = if (DataHelper.db != null) function(DataHelper.db) else null

var internalTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZZZZZ")

var basicTimeFormatter = DateTimeFormatter.ofPattern("MM-dd-yy HH:mm:ss")



