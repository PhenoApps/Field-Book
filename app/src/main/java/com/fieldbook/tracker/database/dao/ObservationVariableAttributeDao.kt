package com.fieldbook.tracker.database.dao

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.core.content.contentValuesOf
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.Migrator.ObservationVariableAttribute
import androidx.core.database.sqlite.transaction

class ObservationVariableAttributeDao {

    companion object {

        fun getAttributeNameById(id: Int) = withDatabase { db ->

            db.query(ObservationVariableAttribute.tableName,
                    where = "${ObservationVariableAttribute.PK} = ?",
                    whereArgs = arrayOf("$id")).toFirst()["observation_variable_attribute_name"]

        }

        fun getAttributeIdByName(name: String): Int = withDatabase { db ->
            db.transaction {

                try {

                    val result = getAttributeIdByName(this, name)

                    result

                } catch (e: Exception) {

                    e.printStackTrace()

                    -1
                }
            }
        } ?: -1

        fun getAttributeIdByName(db: SQLiteDatabase, name: String): Int {
            var id = -1

            try {

                db.query(ObservationVariableAttribute.tableName,
                    where = "observation_variable_attribute_name LIKE ?",
                    whereArgs = arrayOf(name)).use { cursor ->

                    if (cursor.moveToFirst()) { // attribute exists

                        id = cursor.getInt(cursor.getColumnIndexOrThrow(ObservationVariableAttribute.PK))

                    } else { // insert attribute

                        val insertedId = db.insert(
                            ObservationVariableAttribute.tableName, null,
                            contentValuesOf("observation_variable_attribute_name" to name)
                        )

                        id = insertedId.toInt()
                    }
                }

            } catch (e: Exception) {

                e.printStackTrace()

            }

            return id
        }
    }
}