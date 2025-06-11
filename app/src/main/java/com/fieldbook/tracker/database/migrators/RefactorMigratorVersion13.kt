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
        //Creates table schema without data
        db.execSQL("""
            CREATE TABLE observation_units_temp AS
                SELECT internal_id_observation_unit, study_id, observation_unit_db_id, primary_id, secondary_id, geo_coordinates
                FROM observation_units 
        """.trimIndent())

        //drop old table and rename new table
        db.execSQL("""
            DROP TABLE observation_units;
        """.trimIndent())

        db.execSQL("""
            ALTER TABLE observation_units_temp RENAME TO observation_units;
        """.trimIndent())

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

        //drop unused study_id column from observation_units_attributes using temporary table method
        db.execSQL("""
            CREATE TABLE observation_units_attributes_temp AS
                SELECT internal_id_observation_unit_attribute, observation_unit_attribute_name
                FROM observation_units_attributes;
        """.trimIndent())

        db.execSQL("DROP TABLE observation_units_attributes")

        db.execSQL("""
            ALTER TABLE observation_units_attributes_temp RENAME TO observation_units_attributes;
        """.trimIndent())

        //drop unused observation_variable_db_id column from observation_variables using temporary table method
        db.execSQL("""
            CREATE TABLE observation_variables_temp AS
                SELECT internal_id_observation_variable, observation_variable_name, observation_variable_field_book_format, default_value, visible, position, external_db_id, trait_data_source, additional_info, common_crop_name, language, data_type, ontology_db_id, ontology_name, observation_variable_details
                FROM observation_variables;
        """.trimIndent())

        db.execSQL("DROP TABLE observation_variables")

        db.execSQL("""
            ALTER TABLE observation_variables_temp RENAME TO observation_variables;
        """.trimIndent())

        //drop unused study columns from studies table using temporary table method
        db.execSQL("""
            CREATE TABLE studies_temp AS
                SELECT internal_id_study, study_db_id, study_name, study_alias, study_unique_id_name, study_primary_id_name, study_secondary_id_name, experimental_design, common_crop_name, study_sort_name, date_import, date_edit, date_export, study_source, additional_info, observation_levels, seasons, trial_db_id, trial_name, count, import_format, date_sync, observation_unit_search_attribute
                FROM studies;
        """.trimIndent())

        db.execSQL("DROP TABLE studies")

        db.execSQL("""
            ALTER TABLE studies_temp RENAME TO studies;
        """.trimIndent())

        //drop unused columns from observations table using temporary table method
        db.execSQL("""
            CREATE TABLE observations_temp AS
                SELECT internal_id_observation, observation_db_id, observation_unit_id, study_id, observation_variable_db_id, value, observation_time_stamp, collector, geoCoordinates, last_synced_time, additional_info, rep, notes
                FROM observations;
        """.trimIndent())

        db.execSQL("DROP TABLE observations")

        db.execSQL("""
            ALTER TABLE observations_temp RENAME TO observations;
        """.trimIndent())
    }
}