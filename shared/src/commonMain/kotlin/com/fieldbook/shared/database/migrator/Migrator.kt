package com.fieldbook.shared.database.migrator

import app.cash.sqldelight.db.SqlDriver


class Migrator {

    companion object {
        fun migrateV6(driver: SqlDriver) {
            try {
                driver.execute(null, "BEGIN TRANSACTION;", 0)
                driver.execute(null, "CREATE TABLE IF NOT EXISTS plots (plot_id INTEGER PRIMARY KEY AUTOINCREMENT, exp_id INTEGER, unique_id VARCHAR, primary_id VARCHAR, secondary_id VARCHAR, coordinates VARCHAR);", 0)
                driver.execute(null, "CREATE TABLE IF NOT EXISTS plot_attributes (attribute_id INTEGER PRIMARY KEY AUTOINCREMENT, attribute_name VARCHAR, exp_id INTEGER);", 0)
                driver.execute(null, "CREATE TABLE IF NOT EXISTS plot_values (attribute_value_id INTEGER PRIMARY KEY AUTOINCREMENT, attribute_id INTEGER, attribute_value VARCHAR, plot_id INTEGER, exp_id INTEGER);", 0)
                driver.execute(null, "CREATE TABLE IF NOT EXISTS exp_id (exp_id INTEGER PRIMARY KEY AUTOINCREMENT, exp_name VARCHAR, exp_alias VARCHAR, unique_id VARCHAR, primary_id VARCHAR, secondary_id VARCHAR, exp_layout VARCHAR, exp_species VARCHAR, exp_sort VARCHAR, date_import VARCHAR, date_edit VARCHAR, date_export VARCHAR, count INTEGER);", 0)
                driver.execute(null, "COMMIT;", 0)
            } catch (_: Throwable) {
                try { driver.execute(null, "ROLLBACK;", 0) } catch (_: Throwable) {}
            }
        }


        fun migrateV9(driver: SqlDriver) {
            try {
                driver.execute(null, "BEGIN TRANSACTION;", 0)
                driver.execute(null, "CREATE TABLE IF NOT EXISTS observation_units (internal_id_observation_unit INTEGER PRIMARY KEY AUTOINCREMENT, study_id INTEGER REFERENCES studies(internal_id_study) ON DELETE CASCADE, observation_unit_db_id TEXT, primary_id TEXT, secondary_id TEXT, geo_coordinates TEXT, additional_info TEXT, germplasm_db_id TEXT, germplasm_name TEXT, observation_level TEXT, position_coordinate_x TEXT, position_coordinate_x_type TEXT, position_coordinate_y TEXT, position_coordinate_y_type TEXT);", 0)
                driver.execute(null, "CREATE TABLE IF NOT EXISTS observation_units_attributes (id INTEGER NOT NULL PRIMARY KEY, study_id INTEGER NOT NULL REFERENCES studies(internal_id_study), observation_unit_attribute_name TEXT NOT NULL);", 0)
                driver.execute(null, "CREATE TABLE IF NOT EXISTS observation_variables (internal_id_observation_variable INTEGER PRIMARY KEY AUTOINCREMENT, observation_variable_name TEXT, observation_variable_field_book_format TEXT, default_value TEXT, visible TEXT, position INTEGER, external_db_id TEXT, trait_data_source TEXT, additional_info TEXT, common_crop_name TEXT, language TEXT, data_type TEXT, observation_variable_db_id TEXT, ontology_db_id TEXT, ontology_name TEXT, observation_variable_details TEXT);", 0)
                driver.execute(null, "CREATE TABLE IF NOT EXISTS observation_variable_attributes (internal_id_observation_variable_attribute INTEGER PRIMARY KEY AUTOINCREMENT, observation_variable_attribute_name TEXT);", 0)
                driver.execute(null, "CREATE TABLE IF NOT EXISTS observation_variable_values (internal_id_observation_variable_value INTEGER PRIMARY KEY AUTOINCREMENT, observation_variable_attribute_db_id INT, observation_variable_attribute_value TEXT, observation_variable_db_id INT);", 0)
                driver.execute(null, "CREATE TABLE IF NOT EXISTS observations (internal_id_observation INTEGER PRIMARY KEY, study_id INTEGER REFERENCES studies(internal_id_study) NOT NULL, observation_variable_name TEXT, observation_variable_db_id INTEGER, observation_unit_id TEXT, value TEXT, observation_time_stamp TEXT, collector TEXT, geoCoordinates TEXT, observation_db_id TEXT, last_synced_time TEXT, additional_info TEXT, rep TEXT, notes TEXT, observation_variable_field_book_format TEXT);", 0)
                driver.execute(null, "CREATE TABLE IF NOT EXISTS studies (internal_id_study INTEGER PRIMARY KEY AUTOINCREMENT, study_name TEXT, study_alias TEXT, study_unique_id_name TEXT, study_primary_id_name TEXT, study_secondary_id_name TEXT, study_sort_name TEXT, date_import INTEGER, date_edit TEXT);", 0)
                driver.execute(null, "CREATE TABLE IF NOT EXISTS traits (id TEXT PRIMARY KEY, name TEXT, format TEXT, min REAL, max REAL);", 0)

                // Drop legacy tables that were used in older schema versions
                driver.execute(null, "DROP TABLE IF EXISTS exp_id;", 0)
                driver.execute(null, "DROP TABLE IF EXISTS plots;", 0)
                driver.execute(null, "DROP TABLE IF EXISTS range;", 0)
                driver.execute(null, "DROP TABLE IF EXISTS traits;", 0)
                driver.execute(null, "DROP TABLE IF EXISTS user_traits;", 0)

                driver.execute(null, "COMMIT;", 0)
            } catch (_: Throwable) {
                try { driver.execute(null, "ROLLBACK;", 0) } catch (_: Throwable) {}
            }
        }

        /**
         * TODO v10: Fix geo coordinates. The original implementation delegated to GeoJsonUtil which is Android-specific.
         */
        fun fixGeoCoordinates(driver: SqlDriver) {
            // No-op by default; platform-specific implementations may be added later if needed.
        }

}
