package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase
import com.fieldbook.tracker.database.Migrator.Observation
import com.fieldbook.tracker.database.Migrator.ObservationUnit
import com.fieldbook.tracker.database.Migrator.ObservationUnitAttribute
import com.fieldbook.tracker.database.Migrator.ObservationVariable
import com.fieldbook.tracker.database.Migrator.Study

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

        //update observation units table
        migrateObservationUnitsTable(db)

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

        migrateObservationUnitAttributesTable(db)

        migrateObservationVariablesTable(db)

        migrateStudiesTable(db)

        migrateObservationsTable(db)
    }

    private fun migrateObservationUnitsTable(db: SQLiteDatabase) {

        //drop unused columns
        //Creates table schema without data
        db.execSQL("""
            CREATE TABLE observation_units_temp (
                ${ObservationUnit.PK} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${Study.FK} INTEGER REFERENCES studies(${Study.PK}) ON DELETE CASCADE,
                ${ObservationUnit.BRAPI_KEY} TEXT,
                primary_id TEXT,
                secondary_id TEXT,
                geo_coordinates TEXT
            )
        """.trimIndent())

        //copy data from old table to new table
        db.execSQL("""
            INSERT INTO observation_units_temp (${ObservationUnit.PK}, ${Study.FK}, ${ObservationUnit.BRAPI_KEY}, primary_id, secondary_id, geo_coordinates)
            SELECT internal_id_observation_unit, study_id, observation_unit_db_id, primary_id, secondary_id, geo_coordinates
            FROM observation_units;
        """.trimIndent())

        //drop old table and rename new table
        db.execSQL("""
            DROP TABLE observation_units;
        """.trimIndent())

        db.execSQL("""
            ALTER TABLE observation_units_temp RENAME TO observation_units;
        """.trimIndent())
    }

    private fun migrateObservationUnitAttributesTable(db: SQLiteDatabase) {

        //drop unused study_id column from observation_units_attributes using temporary table method
        db.execSQL("""
            CREATE TABLE observation_units_attributes_temp (
                ${ObservationUnitAttribute.PK} INTEGER PRIMARY KEY AUTOINCREMENT,
                observation_unit_attribute_name TEXT
            )
        """.trimIndent())

        db.execSQL("""
            INSERT INTO observation_units_attributes_temp (${ObservationUnitAttribute.PK}, observation_unit_attribute_name)
            SELECT internal_id_observation_unit_attribute, observation_unit_attribute_name
            FROM observation_units_attributes;
        """.trimIndent())

        db.execSQL("DROP TABLE observation_units_attributes")

        db.execSQL("""
            ALTER TABLE observation_units_attributes_temp RENAME TO observation_units_attributes;
        """.trimIndent())
    }

    private fun migrateObservationVariablesTable(db: SQLiteDatabase) {

        db.execSQL("""
            CREATE TABLE observation_variables_temp (
                ${ObservationVariable.PK} INTEGER PRIMARY KEY AUTOINCREMENT,
                observation_variable_name TEXT,
                observation_variable_field_book_format TEXT,
                default_value TEXT,
                visible TEXT,
                position INTEGER,
                external_db_id TEXT,
                trait_data_source TEXT,
                additional_info TEXT,
                common_crop_name TEXT,
                language TEXT,
                data_type TEXT,
                ontology_db_id TEXT,
                ontology_name TEXT,
                observation_variable_details TEXT
            )
        """.trimIndent())

        //copy data from old table to new table
        db.execSQL("""
            INSERT INTO observation_variables_temp (${ObservationVariable.PK}, observation_variable_name, observation_variable_field_book_format, default_value, visible, position, external_db_id, trait_data_source, additional_info, common_crop_name, language, data_type, ontology_db_id, ontology_name, observation_variable_details)
            SELECT internal_id_observation_variable, observation_variable_name, observation_variable_field_book_format, default_value, visible, position, external_db_id, trait_data_source, additional_info, common_crop_name, language, data_type, ontology_db_id, ontology_name, observation_variable_details
            FROM observation_variables;
        """.trimIndent())

        db.execSQL("DROP TABLE observation_variables")

        db.execSQL("""
            ALTER TABLE observation_variables_temp RENAME TO observation_variables;
        """.trimIndent())
    }

    private fun migrateStudiesTable(db: SQLiteDatabase) {

        //drop unused columns from studies table using temporary table method
        db.execSQL("""
            CREATE TABLE studies_temp (
                ${Study.PK} INTEGER PRIMARY KEY AUTOINCREMENT,
                study_name TEXT,
                study_db_id TEXT,
                study_alias TEXT,
                study_unique_id_name TEXT,
                study_primary_id_name TEXT,
                study_secondary_id_name TEXT,
                experimental_design TEXT,
                common_crop_name TEXT,
                study_sort_name TEXT,
                date_import TEXT,
                date_edit TEXT,
                date_export TEXT,
                date_sync TEXT,
                additional_info TEXT,
                study_source TEXT,
                observation_levels TEXT,
                seasons TEXT,
                trial_db_id TEXT,
                trial_name TEXT,
                count INTEGER,
                import_format TEXT,
                observation_unit_search_attribute TEXT
            )
        """.trimIndent())

        //copy data from old table to new table
        db.execSQL("""
            INSERT INTO studies_temp (${Study.PK}, study_name, study_db_id, study_alias, study_unique_id_name, study_primary_id_name, study_secondary_id_name, experimental_design, common_crop_name, study_sort_name, date_import, date_edit, date_export, date_sync, study_source, additional_info, observation_levels, seasons, trial_db_id, trial_name, count, import_format, observation_unit_search_attribute)
            SELECT ${Study.PK}, study_name, study_db_id, study_alias, study_unique_id_name, study_primary_id_name, study_secondary_id_name, experimental_design, common_crop_name, study_sort_name, date_import, date_edit, date_export, date_sync, study_source, additional_info, observation_levels, seasons, trial_db_id, trial_name, count, import_format, observation_unit_search_attribute
            FROM studies;
        """.trimIndent())

        //drop old table and rename new table
        db.execSQL("DROP TABLE studies")

        db.execSQL("""
            ALTER TABLE studies_temp RENAME TO studies;
        """.trimIndent())
    }

    private fun migrateObservationsTable(db: SQLiteDatabase) {

        //drop unused columns from observations table using temporary table method
        db.execSQL("""
            CREATE TABLE observations_temp (
                ${Observation.PK} INTEGER PRIMARY KEY AUTOINCREMENT,
                ${ObservationUnit.FK} TEXT,
                ${ObservationVariable.FK} INTEGER,
                study_id INTEGER,
                observation_db_id TEXT,
                value TEXT,
                observation_time_stamp TEXT,
                collector TEXT,
                geo_coordinates TEXT,
                last_synced_time TEXT,
                additional_info TEXT,
                rep TEXT,
                notes TEXT
            )
        """.trimIndent())

        //copy data from old table to new table
        db.execSQL("""
            INSERT INTO observations_temp (${Observation.PK}, ${ObservationUnit.FK}, ${ObservationVariable.FK}, study_id, value, observation_time_stamp, collector, geo_coordinates, last_synced_time, additional_info, rep, notes)
            SELECT ${Observation.PK}, ${ObservationUnit.FK}, ${ObservationVariable.FK}, study_id, value, observation_time_stamp, collector, geoCoordinates, last_synced_time, additional_info, rep, notes
            FROM observations;
        """.trimIndent())

        //drop old table and rename new table
        db.execSQL("DROP TABLE observations")

        db.execSQL("""
            ALTER TABLE observations_temp RENAME TO observations;
        """.trimIndent())
    }
}