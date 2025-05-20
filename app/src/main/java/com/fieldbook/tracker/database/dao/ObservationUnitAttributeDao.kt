package com.fieldbook.tracker.database.dao

import android.util.Log
import com.fieldbook.tracker.database.*
import com.fieldbook.tracker.database.Migrator.ObservationUnitAttribute
import com.fieldbook.tracker.database.Migrator.ObservationUnitValue

class ObservationUnitAttributeDao {

    companion object {

        const val TAG = "OUADao"

        fun getAllNames(eid: Int): Array<String> = withDatabase { db ->

            val query = """
                SELECT DISTINCT A.${ObservationUnitAttribute.OBSERVATION_UNIT_ATTRIBUTE_NAME}
                FROM ${ObservationUnitAttribute.tableName} AS A
                JOIN ${ObservationUnitValue.tableName} AS V 
                    ON A.${ObservationUnitAttribute.INTERNAL_ID_OBSERVATION_UNIT_ATTRIBUTE} = V.${ObservationUnitValue.OBSERVATION_UNIT_ATTRIBUTE_DB_ID}
                WHERE V.${ObservationUnitValue.STUDY_ID} = ?
                ORDER BY A.observation_unit_attribute_name
            """.trimIndent()

            Log.d(TAG, "getAllNames: $query")

            db.rawQuery(query, arrayOf(eid.toString())).use { cursor ->

                val names = mutableListOf<String>()
                while (cursor.moveToNext()) {
                    val index = cursor.getColumnIndex(ObservationUnitAttribute.OBSERVATION_UNIT_ATTRIBUTE_NAME)
                    if (index == -1) continue
                    val name = cursor.getString(index)
                    if (name.isNotBlank() && name.isNotEmpty()) {
                        names.add(name)
                    }
                }
                names.toTypedArray()
            }

        } ?: emptyArray()

        fun getIdByName(name: String): Int = withDatabase { db ->
            db.query(ObservationUnitAttribute.tableName,
                where = "observation_unit_attribute_name = ?",
                whereArgs = arrayOf(name)).toFirst()[ObservationUnitAttribute.PK] as Int
        } ?: -1
    }
}