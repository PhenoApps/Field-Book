package com.fieldbook.tracker.database

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.core.database.getBlobOrNull
import androidx.core.database.getIntOrNull
import androidx.core.database.getStringOrNull
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
    } finally {

        close()
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



