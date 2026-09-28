package com.fieldbook.tracker.database.dao

import android.content.ContentValues
import androidx.core.content.contentValuesOf
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.Migrator.ObservationVariable
import com.fieldbook.tracker.database.Migrator.ObservationVariableValue

class ObservationVariableValueDao {

    companion object {

        fun getVariableValues(id: Int) = withDatabase { db ->

            return@withDatabase db.query(ObservationVariableValue.tableName,
                    where = "${ObservationVariable.FK} = ?",
                    whereArgs = arrayOf("$id")).toTable()

        }

        fun update(varId: String, attrId: String, value: String) = withDatabase { db ->

            db.update(ObservationVariableValue.tableName, ContentValues().apply {
                put("observation_variable_attribute_value", value)

            }, "${ObservationVariable.FK} = ? AND ${Migrator.ObservationVariableAttribute.FK} = ?", arrayOf(varId, attrId))

        }

        fun insertAttributeValue(attrId: String, value: String, id: String) = withDatabase { db ->

            db.insert(ObservationVariableValue.tableName, null, contentValuesOf(

                ObservationVariable.FK to id,
                Migrator.ObservationVariableAttribute.FK to attrId,
                "observation_variable_attribute_value" to value

            ))
        }

        /**
         * Number of traits whose attribute [attributeName] has the value [value].
         */
        fun countByAttributeValue(attributeName: String, value: String): Int = withDatabase { db ->
            db.rawQuery(
                """
                SELECT COUNT(DISTINCT v.${ObservationVariable.FK})
                FROM ${ObservationVariableValue.tableName} v
                JOIN ${Migrator.ObservationVariableAttribute.tableName} a
                    ON v.${Migrator.ObservationVariableAttribute.FK} = a.${Migrator.ObservationVariableAttribute.PK}
                WHERE a.observation_variable_attribute_name = ?
                    AND v.observation_variable_attribute_value = ?
                """.trimIndent(),
                arrayOf(attributeName, value)
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getInt(0) else 0 }
        } ?: 0

        /**
         * Clears attribute [attributeName] on every trait where it has the value [value].
         */
        fun clearAttributeValue(attributeName: String, value: String) = withDatabase { db ->
            db.execSQL(
                """
                UPDATE ${ObservationVariableValue.tableName}
                SET observation_variable_attribute_value = ''
                WHERE observation_variable_attribute_value = ?
                    AND ${Migrator.ObservationVariableAttribute.FK} IN (
                        SELECT ${Migrator.ObservationVariableAttribute.PK}
                        FROM ${Migrator.ObservationVariableAttribute.tableName}
                        WHERE observation_variable_attribute_name = ?
                    )
                """.trimIndent(),
                arrayOf(value, attributeName)
            )
        }

        fun insert(min: String, max: String, categories: String, closeKeyboardOnOpen: String, cropImage: String, useDayOfYear: String, displayValue: String, resourceFile: String, id: String) = withDatabase { db ->

            //iterate through mapping of the old columns that are now attr/vals
            mapOf(
                    "validValuesMin" to min,
                    "validValuesMax" to max,
                    "category" to categories,
                    "closeKeyboardOnOpen" to closeKeyboardOnOpen,
                    "cropImage" to cropImage,
                    "useDayOfYear" to useDayOfYear,
                    "displayValue" to displayValue,
                    "resourceFile" to resourceFile
            ).asSequence().forEach { attrValue ->

                insertAttributeValue(attrValue.key, attrValue.value, id)

            }
        }
    }
}