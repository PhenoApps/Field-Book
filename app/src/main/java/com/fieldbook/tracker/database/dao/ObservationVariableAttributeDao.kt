package com.fieldbook.tracker.database.dao

import androidx.core.content.contentValuesOf
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.Migrator.ObservationVariableAttribute

class ObservationVariableAttributeDao {

    companion object {

        fun getAttributeNameById(id: Int) = withDatabase { db ->

            db.query(ObservationVariableAttribute.tableName,
                    where = "${ObservationVariableAttribute.PK} = ?",
                    whereArgs = arrayOf("$id")).toFirst()["observation_variable_attribute_name"]

        }

        fun getAttributeIdByName(name: String): Int = withDatabase { db ->

            db.beginTransaction()

            var id = -1

            try {

                id = db.query(ObservationVariableAttribute.tableName,
                    where = "observation_variable_attribute_name LIKE ?",
                    whereArgs = arrayOf(name)).toFirst().getOrDefault(ObservationVariableAttribute.PK, -1) as Int

                if (id == -1) {

                    db.insert(ObservationVariableAttribute.tableName, null, contentValuesOf(
                        "observation_variable_attribute_name" to name
                    ))

                    id = db.query(ObservationVariableAttribute.tableName,
                        where = "observation_variable_attribute_name LIKE ?",
                        whereArgs = arrayOf(name)).toFirst().getOrDefault(ObservationVariableAttribute.PK, -1) as Int
                }

                db.setTransactionSuccessful()

            } catch (e: Exception) {

                e.printStackTrace()

            } finally {

                db.endTransaction()

            }

            id

        } ?: -1
    }
}