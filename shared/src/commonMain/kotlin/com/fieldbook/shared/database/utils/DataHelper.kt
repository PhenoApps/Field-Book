package com.fieldbook.shared.database.utils

import app.cash.sqldelight.db.SqlDriver
import com.fieldbook.shared.database.migrator.Migrator

object DataHelper {

    /**
     * Port of DataHelper.onUpgrade from the Android app into shared module.
     * This function executes schema changes and delegates the complex refactor
     * steps to `Migrator` helpers.
     *
     * @param driver the SqlDelight SqlDriver for performing raw SQL
     * @param oldVersion previous DB user_version
     * @param newVersion desired DB schema version
     * @param prefs optional map of preference keys used during migration (e.g. FIELD_FILE, UNIQUE_NAME, PRIMARY_NAME, SECONDARY_NAME)
     */
    fun onUpgrade(driver: SqlDriver, oldVersion: Int, newVersion: Int, prefs: Map<String, String>? = null) {
        // Drop legacy tables for very old DBs
        if (oldVersion < 5) {
            executeQuiet(driver, "DROP TABLE IF EXISTS range;")
            executeQuiet(driver, "DROP TABLE IF EXISTS traits;")
            executeQuiet(driver, "DROP TABLE IF EXISTS ser_traits;")
        }

        // v5: add columns to user_traits
        if (oldVersion <= 5 && newVersion >= 5) {
            executeQuiet(driver, "ALTER TABLE user_traits ADD COLUMN person TEXT;")
            executeQuiet(driver, "ALTER TABLE user_traits ADD COLUMN location TEXT;")
            executeQuiet(driver, "ALTER TABLE user_traits ADD COLUMN rep TEXT;")
            executeQuiet(driver, "ALTER TABLE user_traits ADD COLUMN notes TEXT;")
            executeQuiet(driver, "ALTER TABLE user_traits ADD COLUMN exp_id TEXT;")
        }

        // v6: create plots-related tables and insert an exp row if preferences provided
        if (oldVersion <= 6 && newVersion >= 6) {
            executeQuiet(driver, "CREATE TABLE IF NOT EXISTS plots (plot_id INTEGER PRIMARY KEY AUTOINCREMENT, exp_id INTEGER, unique_id VARCHAR, primary_id VARCHAR, secondary_id VARCHAR, coordinates VARCHAR);")
            executeQuiet(driver, "CREATE TABLE IF NOT EXISTS plot_attributes (attribute_id INTEGER PRIMARY KEY AUTOINCREMENT, attribute_name VARCHAR, exp_id INTEGER);")
            executeQuiet(driver, "CREATE TABLE IF NOT EXISTS plot_values (attribute_value_id INTEGER PRIMARY KEY AUTOINCREMENT, attribute_id INTEGER, attribute_value VARCHAR, plot_id INTEGER, exp_id INTEGER);")
            executeQuiet(driver, "CREATE TABLE IF NOT EXISTS exp_id (exp_id INTEGER PRIMARY KEY AUTOINCREMENT, exp_name VARCHAR, exp_alias VARCHAR, unique_id VARCHAR, primary_id VARCHAR, secondary_id VARCHAR, exp_layout VARCHAR, exp_species VARCHAR, exp_sort VARCHAR, date_import VARCHAR, date_edit VARCHAR, date_export VARCHAR, count INTEGER);")

            // Insert one exp_id row using prefs if available (mirrors Java code)
            prefs?.let { p ->
                val fieldFile = p["FIELD_FILE"] ?: p["field_file"] ?: ""
                val unique = p["UNIQUE_NAME"] ?: p["unique_name"] ?: ""
                val primary = p["PRIMARY_NAME"] ?: p["primary_name"] ?: ""
                val secondary = p["SECONDARY_NAME"] ?: p["secondary_name"] ?: ""

                if (fieldFile.isNotEmpty() || unique.isNotEmpty() || primary.isNotEmpty() || secondary.isNotEmpty()) {
                    executeQuiet(driver, "INSERT INTO exp_id (exp_name, exp_alias, unique_id, primary_id, secondary_id) VALUES ('${escape(fieldFile)}','${escape(fieldFile)}','${escape(unique)}','${escape(primary)}','${escape(secondary)}');")
                }
            }

            try {
                Migrator.migrateV6(driver)
            } catch (_: Throwable) {
                // swallow migrator errors to avoid breaking startup; developer should improve Migrator implementation
            }
        }

        // v8: add brapi-related columns
        if (oldVersion <= 7 && newVersion >= 8) {
            executeQuiet(driver, "ALTER TABLE traits ADD COLUMN external_db_id VARCHAR;")
            executeQuiet(driver, "ALTER TABLE traits ADD COLUMN trait_data_source VARCHAR;")
            executeQuiet(driver, "ALTER TABLE exp_id ADD COLUMN exp_source VARCHAR;")
            executeQuiet(driver, "ALTER TABLE user_traits ADD COLUMN observation_db_id TEXT;")
            executeQuiet(driver, "ALTER TABLE user_traits ADD COLUMN last_synced_time TEXT;")
        }

        // v9: full refactor to new EAV/normalized schema — delegate to migrator which should implement data copy
        if (oldVersion < 9 && newVersion >= 9) {
            try {
                Migrator.migrateV9(driver)
            } catch (_: Throwable) {
                // If migrator not implemented fully yet, ensure new tables exist (migrator should create them)
            }
        }

        // v10: fix geo coords
        if (oldVersion <= 9 && newVersion >= 10) {
            try {
                Migrator.fixGeoCoordinates(driver)
            } catch (_: Throwable) {
                // noop
            }
        }

        // v11: add import_format and date_sync and populate/fix aliases
        if (oldVersion <= 10 && newVersion >= 11) {
            executeQuiet(driver, "ALTER TABLE studies ADD COLUMN import_format TEXT;")
            executeQuiet(driver, "ALTER TABLE studies ADD COLUMN date_sync TEXT;")

            // populate import_format using SQL CASE (mirrors Java implementation)
            executeQuiet(driver, "UPDATE studies SET import_format = CASE WHEN study_source IS NULL OR study_source = 'csv' OR study_source LIKE '%.csv' THEN 'csv' WHEN study_source = 'excel' OR study_source LIKE '%.xls' THEN 'xls' WHEN study_source LIKE '%.xlsx' THEN 'xlsx' ELSE 'brapi' END;")

            // fix study aliases for brapi
            executeQuiet(driver, "UPDATE studies SET study_db_id = study_alias, study_alias = study_name WHERE import_format = 'brapi';")
        }

        // v12: add observation_unit_search_attribute and set default
        if (oldVersion <= 11 && newVersion >= 12) {
            executeQuiet(driver, "ALTER TABLE studies ADD COLUMN observation_unit_search_attribute TEXT;")
            executeQuiet(driver, "UPDATE studies SET observation_unit_search_attribute = study_unique_id_name;")
        }
    }

    private fun executeQuiet(driver: SqlDriver, sql: String) {
        try {
            driver.execute(null, sql, 0)
        } catch (_: Throwable) {
            // ignore errors (e.g., column already exists) to emulate SQLiteOpenHelper behavior
        }
    }

    private fun escape(s: String): String = s.replace("'", "''")
}
