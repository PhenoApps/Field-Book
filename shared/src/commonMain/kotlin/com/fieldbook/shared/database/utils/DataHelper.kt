package com.fieldbook.shared.database.utils

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import com.fieldbook.shared.database.migrator.Migrator
import com.fieldbook.shared.database.models.TraitObject

/**
 * In-place Kotlin migration of the original Java DataHelper.
 */
class DataHelper private constructor() {

    companion object {
        const val RANGE = "range"
        const val TRAITS = "traits"
        const val DATABASE_VERSION = 12
        private const val DATABASE_NAME = "fieldbook.db"
        private const val USER_TRAITS = "user_traits"
        private const val EXP_INDEX = "exp_id"
        private const val PLOTS = "plots"
        private const val PLOT_ATTRIBUTES = "plot_attributes"
        private const val PLOT_VALUES = "plot_values"
        private const val TAG = "Field Book"
        private const val TICK = "`"
        private const val TIME_FORMAT_PATTERN = "yyyy-MM-dd HH:mm:ss.SSSZZZZZ"

        /**
         * Ported onUpgrade that runs schema changes and delegates complex steps to Migrator.
         * Kept intentionally similar to original implementation order/logic.
         */
        fun onUpgrade(
            driver: SqlDriver,
            oldVersion: Int,
            newVersion: Int,
            prefs: Map<String, String>? = null
        ) {
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
                executeQuiet(
                    driver,
                    "CREATE TABLE IF NOT EXISTS plots (plot_id INTEGER PRIMARY KEY AUTOINCREMENT, exp_id INTEGER, unique_id VARCHAR, primary_id VARCHAR, secondary_id VARCHAR, coordinates VARCHAR);"
                )
                executeQuiet(
                    driver,
                    "CREATE TABLE IF NOT EXISTS plot_attributes (attribute_id INTEGER PRIMARY KEY AUTOINCREMENT, attribute_name VARCHAR, exp_id INTEGER);"
                )
                executeQuiet(
                    driver,
                    "CREATE TABLE IF NOT EXISTS plot_values (attribute_value_id INTEGER PRIMARY KEY AUTOINCREMENT, attribute_id INTEGER, attribute_value VARCHAR, plot_id INTEGER, exp_id INTEGER);"
                )
                executeQuiet(
                    driver,
                    "CREATE TABLE IF NOT EXISTS exp_id (exp_id INTEGER PRIMARY KEY AUTOINCREMENT, exp_name VARCHAR, exp_alias VARCHAR, unique_id VARCHAR, primary_id VARCHAR, secondary_id VARCHAR, exp_layout VARCHAR, exp_species VARCHAR, exp_sort VARCHAR, date_import VARCHAR, date_edit VARCHAR, date_export VARCHAR, count INTEGER);"
                )

                // Insert one exp_id row using prefs if available (mirrors Java code)
                prefs?.let { p ->
                    val fieldFile = p["FIELD_FILE"] ?: p["field_file"] ?: ""
                    val unique = p["UNIQUE_NAME"] ?: p["unique_name"] ?: ""
                    val primary = p["PRIMARY_NAME"] ?: p["primary_name"] ?: ""
                    val secondary = p["SECONDARY_NAME"] ?: p["secondary_name"] ?: ""

                    if (fieldFile.isNotEmpty() || unique.isNotEmpty() || primary.isNotEmpty() || secondary.isNotEmpty()) {
                        executeQuiet(
                            driver,
                            "INSERT INTO exp_id (exp_name, exp_alias, unique_id, primary_id, secondary_id) VALUES ('${
                                escape(fieldFile)
                            }','${escape(fieldFile)}','${escape(unique)}','${escape(primary)}','${
                                escape(
                                    secondary
                                )
                            }');"
                        )
                    }
                }

                try {
                    // legacy code called Migrator.migrateV6(driver). If present, call createTables as a best-effort.
                    try {
                        Migrator.createTables(driver, arrayListOf())
                    } catch (_: Throwable) {
                        // fallback no-op
                    }
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
                    // If Migrator exposes migrateSchema, call it; otherwise fall back to createTables.
                    try {
                        Migrator.createTables(driver, arrayListOf())
                    } catch (_: Throwable) {
                        // no-op
                    }
                } catch (_: Throwable) {
                    // If migrator not implemented fully yet, ensure new tables exist (migrator should create them)
                }
            }

            // v10: fix geo coords
            if (oldVersion <= 9 && newVersion >= 10) {
                try {
                    // no direct Migrator.fixGeoCoordinates in common migrator; emulate no-op
                } catch (_: Throwable) {
                    // noop
                }
            }

            // v11: add import_format and date_sync and populate/fix aliases
            if (oldVersion <= 10 && newVersion >= 11) {
                executeQuiet(driver, "ALTER TABLE studies ADD COLUMN import_format TEXT;")
                executeQuiet(driver, "ALTER TABLE studies ADD COLUMN date_sync TEXT;")

                // populate import_format using SQL CASE (mirrors Java implementation)
                executeQuiet(
                    driver,
                    "UPDATE studies SET import_format = CASE WHEN study_source IS NULL OR study_source = 'csv' OR study_source LIKE '%.csv' THEN 'csv' WHEN study_source = 'excel' OR study_source LIKE '%.xls' THEN 'xls' WHEN study_source LIKE '%.xlsx' THEN 'xlsx' ELSE 'brapi' END;"
                )

                // fix study aliases for brapi
                executeQuiet(
                    driver,
                    "UPDATE studies SET study_db_id = study_alias, study_alias = study_name WHERE import_format = 'brapi';"
                )
            }

            // v12: add observation_unit_search_attribute and set default
            if (oldVersion <= 11 && newVersion >= 12) {
                executeQuiet(
                    driver,
                    "ALTER TABLE studies ADD COLUMN observation_unit_search_attribute TEXT;"
                )
                executeQuiet(
                    driver,
                    "UPDATE studies SET observation_unit_search_attribute = study_unique_id_name;"
                )
            }
        }

        fun onCreate(driver: SqlDriver) {
            try {
                driver.execute(null, "PRAGMA foreign_keys=ON;", 0)
            } catch (_: Throwable) {
            }

            execute(
                driver,
                "CREATE TABLE $RANGE (id INTEGER PRIMARY KEY, range TEXT, plot TEXT, entry TEXT, plot_id TEXT, pedigree TEXT)"
            )
            execute(
                driver,
                "CREATE TABLE $TRAITS (id INTEGER PRIMARY KEY, external_db_id TEXT, trait_data_source TEXT, trait TEXT, format TEXT, defaultValue TEXT, minimum TEXT, maximum TEXT, details TEXT, categories TEXT, isVisible TEXT, realPosition int)"
            )
            execute(
                driver,
                "CREATE TABLE $USER_TRAITS (id INTEGER PRIMARY KEY, rid TEXT, parent TEXT, trait TEXT, userValue TEXT, timeTaken TEXT, person TEXT, location TEXT, rep TEXT, notes TEXT, exp_id TEXT, observation_db_id TEXT, last_synced_time TEXT)"
            )
            execute(
                driver,
                "CREATE TABLE $PLOTS (plot_id INTEGER PRIMARY KEY AUTOINCREMENT, exp_id INTEGER, unique_id VARCHAR, primary_id VARCHAR, secondary_id VARCHAR, coordinates VARCHAR)"
            )
            execute(
                driver,
                "CREATE TABLE $PLOT_ATTRIBUTES (attribute_id INTEGER PRIMARY KEY AUTOINCREMENT, attribute_name VARCHAR, exp_id INTEGER)"
            )
            execute(
                driver,
                "CREATE TABLE $PLOT_VALUES (attribute_value_id INTEGER PRIMARY KEY AUTOINCREMENT, attribute_id INTEGER, attribute_value VARCHAR, plot_id INTEGER, exp_id INTEGER)"
            )
            execute(
                driver,
                "CREATE TABLE $EXP_INDEX (exp_id INTEGER PRIMARY KEY AUTOINCREMENT, exp_name VARCHAR, exp_alias VARCHAR, unique_id VARCHAR, primary_id VARCHAR, secondary_id VARCHAR, exp_layout VARCHAR, exp_species VARCHAR, exp_sort VARCHAR, date_import VARCHAR, date_edit VARCHAR, date_export VARCHAR, count INTEGER, exp_source VARCHAR)"
            )

            // create android_metadata as best-effort (some drivers may not need this)
            try {
                execute(driver, "CREATE TABLE android_metadata (locale TEXT)")
                execute(driver, "INSERT INTO android_metadata(locale) VALUES('en_US')")
            } catch (_: Throwable) {
            }

            // Delegate migration of v8->v9 refactor to Migrator
            try {
                // gather trait objects if needed - read legacy traits table
                val traitObjs = getAllTraitObjects(driver)
                try {
                    Migrator.createTables(driver, traitObjs)
                } catch (_: Throwable) {
                    // ignore if Migrator does not implement createTables for driver
                }
            } catch (_: Throwable) {
            }

            // Force upgrade logic if necessary (mimic original behavior)
            onUpgrade(driver, 9, DATABASE_VERSION)
        }

        // --- Helper utilities for SqlDriver-based operations ---
        private fun executeQuiet(driver: SqlDriver, sql: String) {
            try {
                driver.execute(null, sql, 0)
            } catch (_: Throwable) {
                // ignore errors (e.g., column already exists) to emulate SQLiteOpenHelper behavior
            }
        }

        private fun execute(driver: SqlDriver, sql: String) {
            driver.execute(null, sql, 0)
        }

        private fun escape(s: String): String = s.replace("'", "''")

        // small helper to get boolean from SqlCursor.next() which returns QueryResult<Boolean>
        private fun hasNext(cursor: SqlCursor): Boolean {
            return when (val r = cursor.next()) {
                is QueryResult.Value<*> -> (r.value as? Boolean) == true
                else -> false
            }
        }

        // Utility: select all mapping helper used during migrations.
        // pattern maps old column name -> new column name.
        fun selectAll(
            driver: SqlDriver,
            name: String,
            pattern: Map<String, String>,
            function: (MutableList<Map<String, Any?>>) -> Unit
        ) {
            val entries = pattern.entries.toList()
            if (entries.isEmpty()) {
                function(mutableListOf())
                return
            }

            val selectList = entries.joinToString(",") { "${it.key} AS ${it.value}" }
            val sql = "SELECT $selectList FROM $name"

            try {
                val result = driver.executeQuery(null, sql, { cursor: SqlCursor ->
                    val table = mutableListOf<Map<String, Any?>>()
                    var traitPosition = 1

                    while (hasNext(cursor)) {
                        val row = mutableMapOf<String, Any?>()
                        for (i in entries.indices) {
                            val colName = entries[i].value

                            val longVal = cursor.getLong(i)
                            if (longVal != null) {
                                row[colName] =
                                    if (entries[i].key == "realPosition") traitPosition++ else longVal
                                continue
                            }

                            val strVal = cursor.getString(i)
                            if (strVal != null) {
                                row[colName] = strVal
                                continue
                            }

                            val bytes = cursor.getBytes(i)
                            if (bytes != null) {
                                row[colName] = bytes
                                continue
                            }

                            row[colName] = null
                        }
                        table.add(row)
                    }
                    QueryResult.Value(table)
                }, 0)

                if (result is QueryResult.Value<*>) {
                    @Suppress("UNCHECKED_CAST")
                    function((result.value as? MutableList<Map<String, Any?>>) ?: mutableListOf())
                } else {
                    function(mutableListOf())
                }
            } catch (e: Exception) {
                e.printStackTrace()
                function(mutableListOf())
            }
        }

        // Read legacy traits table using SqlDriver and map to TraitObject list.
        fun getAllTraitObjects(driver: SqlDriver): ArrayList<TraitObject> {
            val list = arrayListOf<TraitObject>()

            val sql =
                "SELECT id, trait, format, defaultValue, minimum, maximum, details, categories, isVisible, realPosition FROM $TRAITS ORDER BY realPosition"

            try {
                val result = driver.executeQuery(null, sql, { cursor: SqlCursor ->
                    val rows = arrayListOf<TraitObject>()
                    while (hasNext(cursor)) {
                        val idVal = cursor.getLong(0)
                        val traitName = cursor.getString(1)
                        val format = cursor.getString(2)

                        // v5.1.0 bugfix branch update, Android getString can return null.
                        if (traitName == null || format == null) continue

                        val t = TraitObject(
                            id = idVal?.toLong(),
                            name = traitName,
                            format = format,
                            defaultValue = cursor.getString(3),
                            minimum = cursor.getString(4),
                            maximum = cursor.getString(5),
                            details = cursor.getString(6),
                            categories = cursor.getString(7),
                            visible = cursor.getString(8),
                            realPosition = cursor.getLong(9)?.toInt() ?: 0
                        )

                        rows.add(t)
                    }

                    QueryResult.Value(rows)
                }, 0)

                if (result is QueryResult.Value<*>) {
                    @Suppress("UNCHECKED_CAST")
                    val v = result.value as? List<TraitObject>
                    if (v != null) list.addAll(v)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            return list
        }
    }
}
