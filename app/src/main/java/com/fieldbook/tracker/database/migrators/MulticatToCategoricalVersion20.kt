package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase
import com.fieldbook.tracker.database.Migrator.ObservationVariable
import com.fieldbook.tracker.database.Migrator.ObservationVariableAttribute
import com.fieldbook.tracker.database.Migrator.ObservationVariableValue
import com.fieldbook.tracker.database.ObservationVariableAttributeDetailsView
import com.fieldbook.tracker.database.dao.ObservationVariableAttributeDao

class MulticatToCategoricalVersion20 : FieldBookMigrator {

    companion object {
        const val TAG = "MulticatToCategoricalVersion20"
        const val VERSION = 20
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {
        val multicatTraitIds = mutableListOf<String>()

        // get all observation variables with format "multicat"
        db.rawQuery("""
            SELECT internal_id_observation_variable 
            FROM ${ObservationVariable.Schema.tableName}
            WHERE observation_variable_field_book_format = 'multicat'
            """, null).use { cursor ->

            while (cursor.moveToNext()) {
                multicatTraitIds.add(cursor.getString(0))
            }
        }

        if (multicatTraitIds.isEmpty()) {
            return@runCatching
        }

        changeTraitFormatToCategorical(db)

        setAllowMulticatAttributeForTraits(db, multicatTraitIds)
    }

    /**
     * Changes format of traits having "multicat" to "categorical"
     */
    private fun changeTraitFormatToCategorical(db: SQLiteDatabase) {
        db.execSQL("""
            UPDATE ${ObservationVariable.Schema.tableName}
            SET observation_variable_field_book_format = 'categorical'
            WHERE observation_variable_field_book_format = 'multicat'
            """)
    }

    /**
     * Set allowMulticat = true for all migrated multicat traits
     */
    private fun setAllowMulticatAttributeForTraits(db: SQLiteDatabase, multicatTraitIds: List<String>) {
        // get the attribute ID for ALLOW_MULTICAT
        val allowMulticatAttributeId = ObservationVariableAttributeDao.getAttributeIdByName(
            db, ObservationVariableAttributeDetailsView.ALLOW_MULTICAT
        )

        multicatTraitIds.forEach { traitId ->
            // Check if the attribute value already exists
            db.rawQuery("""
                SELECT internal_id_observation_variable_value
                FROM ${ObservationVariableValue.Schema.tableName}
                WHERE ${ObservationVariable.Schema.FK} = ? 
                AND ${ObservationVariableAttribute.Schema.FK} = ?
                """, arrayOf(traitId, allowMulticatAttributeId.toString())).use { cursor ->

                if (cursor.moveToFirst()) { // if attribute exists for trait
                    // set ALLOW_MULTICAT to true
                    val valueId = cursor.getString(0)
                    db.execSQL("""
                    UPDATE ${ObservationVariableValue.Schema.tableName}
                    SET observation_variable_attribute_value = 'true'
                    WHERE internal_id_observation_variable_value = ?
                    """, arrayOf(valueId))
                } else { // insert new attribute for trait
                    db.execSQL("""
                    INSERT INTO ${ObservationVariableValue.Schema.tableName} 
                    (${ObservationVariable.Schema.FK}, ${ObservationVariableAttribute.Schema.FK}, observation_variable_attribute_value)
                    VALUES (?, ?, 'true')
                    """, arrayOf(traitId, allowMulticatAttributeId.toString()))
                }
            }
        }
    }
}