package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase

/**
 * Example migrator
 */
class RefactorMigratorVersion13: FieldBookMigrator {

    companion object {
        const val TAG = "RefactorMigratorVersion13"
        const val VERSION = 13
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {

        try {
            db.beginTransaction()
            refactorDatabase(db)
            db.setTransactionSuccessful()
        } catch (e: Exception) {
            e.printStackTrace()
            throw e
        } finally {
            db.endTransaction()
        }
    }

    private fun refactorDatabase(db: SQLiteDatabase) {

        //migrate plot attributes to ensure all exist in new tables
        db.execSQL("""
            INSERT OR REPLACE INTO observation_units_attributes 
                (internal_id_observation_unit_attribute, observation_unit_attribute_name, study_id)
            SELECT attribute_id AS internal_id_observation_unit_attribute, 
                attribute_name AS observation_unit_attribute_name, 
                exp_id AS study_id
            FROM plot_attributes AS p
        """.trimIndent())

        //delete old tables
        db.execSQL("DROP TABLE IF EXISTS exp_id")
        db.execSQL("DROP TABLE IF EXISTS plot_attributes")
        db.execSQL("DROP TABLE IF EXISTS plot_values")
        db.execSQL("DROP TABLE IF EXISTS plots")
        db.execSQL("DROP TABLE IF EXISTS `range`")
        db.execSQL("DROP TABLE IF EXISTS user_traits")
        db.execSQL("DROP TABLE IF EXISTS traits")

        //drop unused columns
        db.execSQL("ALTER TABLE observation_units DROP COLUMN position_coordinate_x")
        db.execSQL("ALTER TABLE observation_units DROP COLUMN position_coordinate_y")
        db.execSQL("ALTER TABLE observation_units DROP COLUMN position_coordinate_x_type")
        db.execSQL("ALTER TABLE observation_units DROP COLUMN position_coordinate_y_type")
        db.execSQL("ALTER TABLE observation_units DROP COLUMN additional_info")
        db.execSQL("ALTER TABLE observation_units DROP COLUMN germplasm_db_id")
        db.execSQL("ALTER TABLE observation_units DROP COLUMN germplasm_name")
        db.execSQL("ALTER TABLE observation_units DROP COLUMN observation_level")

        //update unit values to the first occurrence of an attribute name
        db.execSQL("""
            --create CTE for easy access to first occurrences of attribute names
            WITH attribute_ids AS (
                SELECT MIN(A.internal_id_observation_unit_attribute) AS first_id,
                       A.observation_unit_attribute_name AS name
                FROM observation_units_attributes AS A
                JOIN observation_units_values AS V
                    ON A.internal_id_observation_unit_attribute = V.observation_unit_attribute_db_id
                GROUP BY A.observation_unit_attribute_name
            )
            --update actual values by joining values and attributes, and using the above CTE
            UPDATE observation_units_values
            SET observation_unit_attribute_db_id = (
                SELECT I.first_id
                FROM attribute_ids AS I
                JOIN observation_units_attributes AS A 
                    ON A.internal_id_observation_unit_attribute = observation_units_values.observation_unit_attribute_db_id
                WHERE I.name = A.observation_unit_attribute_name
            );
        """.trimIndent())

        //delete unused/redundant observation attribute rows
        db.execSQL("""
            --delete unused observation unit attributes
            DELETE FROM observation_units_attributes
            WHERE internal_id_observation_unit_attribute IN
                --find all attribute/values pairs that aren't used
                (SELECT internal_id_observation_unit_attribute
                FROM observation_units_attributes
                WHERE internal_id_observation_unit_attribute NOT IN (
                --find all attribute/values pairs
                    SELECT internal_id_observation_unit_attribute
                    FROM observation_units_values AS V
                    JOIN observation_units_attributes AS A ON A.internal_id_observation_unit_attribute = V.observation_unit_attribute_db_id));
        """.trimIndent())

        //drop unnecessary study_id column from unit attributes
        db.execSQL("ALTER TABLE observation_units_attributes DROP COLUMN study_id")

        //drop unused observation variable columns
        db.execSQL("ALTER TABLE observation_variables DROP COLUMN observation_variable_db_id")

        //drop unused studies columns
        db.execSQL("ALTER TABLE studies DROP COLUMN study_type")
        db.execSQL("ALTER TABLE studies DROP COLUMN study_description")
        db.execSQL("ALTER TABLE studies DROP COLUMN study_code")
        db.execSQL("ALTER TABLE studies DROP COLUMN start_date")
        db.execSQL("ALTER TABLE studies DROP COLUMN location_name")
        db.execSQL("ALTER TABLE studies DROP COLUMN location_db_id")

        //drop observation variable name from observations table
        db.execSQL("ALTER TABLE observations DROP COLUMN observation_variable_name")
        db.execSQL("ALTER TABLE observations DROP COLUMN observation_variable_field_book_format")
    }
}