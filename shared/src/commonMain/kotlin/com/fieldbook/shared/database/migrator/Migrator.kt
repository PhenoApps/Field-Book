package com.fieldbook.shared.database.migrator

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import com.fieldbook.shared.database.models.TraitObject


class Migrator {

    companion object {

        const val TAG = "Migrator"

        const val sVisibleObservationVariableViewName = "VisibleObservationVariable"
        val sVisibleObservationVariableView = """
            CREATE VIEW IF NOT EXISTS $sVisibleObservationVariableViewName 
            AS SELECT ${ObservationVariable.PK}, observation_variable_name, observation_variable_field_book_format, observation_variable_details, default_value, position
            FROM ${ObservationVariable.tableName} WHERE visible LIKE "true" ORDER BY position
        """.trimIndent()

        const val sObservationUnitPropertyViewName = "ObservationUnitProperty"

        //view for getUserTraitObservations call which uses joins
        //looks like this query just finds all observations that are not pictures
        //view for all remote/local observations that are not photos format
        const val sNonImageObservationsViewName = "NonImageObservations"
        const val sNonImageObservationsView = """
            CREATE VIEW IF NOT EXISTS $sNonImageObservationsViewName
            AS SELECT obs.${Observation.PK} AS id, 
                obs.value AS value, 
                s.${Study.PK} AS study_db_id,
                vars.trait_data_source
            FROM ${Observation.tableName} AS obs
            JOIN ${ObservationVariable.tableName} AS vars ON obs.${ObservationVariable.FK} = vars.${ObservationVariable.PK}
            JOIN ${Study.tableName} AS s ON obs.${Study.FK} = s.${Study.PK}
            WHERE vars.observation_variable_field_book_format <> 'photo'
        """

        //a view for handling local image observations
        const val sLocalImageObservationsViewName = "LocalImageObservations"
        const val sLocalImageObservationsView = """
            CREATE VIEW IF NOT EXISTS $sLocalImageObservationsViewName     
            AS SELECT obs.${Observation.PK} AS id, 
                obs.value AS value, 
                study.${Study.PK} AS ${Study.FK},
                vars.observation_variable_name as observation_variable_name
            FROM ${Observation.tableName} AS obs
            JOIN ${ObservationVariable.tableName} AS vars ON obs.${ObservationVariable.FK} = vars.${ObservationVariable.PK} 
            JOIN ${Study.tableName} AS study ON obs.${Study.FK} = study.${Study.PK} 
            WHERE (vars.trait_data_source = 'local' OR vars.trait_data_source IS NULL) 
                AND vars.observation_variable_field_book_format = 'photo'
        """

        const val sRemoteImageObservationsViewName = "RemoteImageObservationsView"
        const val sRemoteImageObservationsView = """
            CREATE VIEW IF NOT EXISTS $sRemoteImageObservationsViewName     
            AS SELECT obs.${Observation.PK} AS id, 
                        obs.value AS value, 
                        study.${Study.PK} AS ${Study.FK},
                        vars.trait_data_source AS trait_data_source,
                        vars.observation_variable_field_book_format,
                        vars.observation_variable_name
            FROM ${Observation.tableName} AS obs
            JOIN ${ObservationVariable.tableName} AS vars ON obs.${ObservationVariable.FK} = vars.${ObservationVariable.PK} 
            JOIN ${Study.tableName} AS study ON obs.${Study.FK} = study.${Study.PK} 
            WHERE study.study_source IS NOT NULL
                AND vars.trait_data_source <> 'local' 
                AND vars.trait_data_source IS NOT NULL
                AND vars.observation_variable_field_book_format = 'photo'
        """

        private val sTableNames = arrayOf(
                Study.tableName,
                ObservationUnit.tableName,
                ObservationVariable.tableName,
                Observation.tableName,
                ObservationVariableAttribute.tableName,
                ObservationVariableValue.tableName,
                ObservationUnitAttribute.tableName,
                ObservationUnitValue.tableName)

        private val sViewNames = arrayOf(
                sVisibleObservationVariableViewName,
                sNonImageObservationsViewName,
                sLocalImageObservationsViewName,
                sRemoteImageObservationsViewName,
                sObservationUnitPropertyViewName)

        /**
         * Public entrypoint used by older codepaths — migrated to SqlDriver
         */
        fun migrateSchema(driver: SqlDriver, traits: ArrayList<TraitObject>) {

            createTables(driver, traits)

            removeOldTables(driver)
        }

        /**
         * Function that iterates over all version 8 table names and drops them.
         */
        private fun removeOldTables(driver: SqlDriver) {

            try {

                beginTransaction(driver)

                for (table in arrayOf("exp_id", "plots", "range", "traits", "user_traits")) {

                    execute(driver, "DROP TABLE IF EXISTS $table")
                }

                commitTransaction(driver)

            } catch (e: Exception) {

                e.printStackTrace()

                try {
                    rollbackTransaction(driver)
                } catch (_: Throwable) {
                }

            }

        }

        /**
         * Creates Tables for new schema.
         * 1. deletes any old table that exists referencing new schema rows. e.g StudyModel
         * 2. Builds and runs a create table statement based on the table class.
         * 3. Queries the original db for exp_id rows
         * 4. Populates table based on previous query by translating
         *      column headers using a migration pattern mapping.
         */
        fun createTables(driver: SqlDriver, traits: ArrayList<TraitObject>) {

            try {

                beginTransaction(driver)

                deleteTables(driver)

                //maybe replace this wall of with statements with kotlin.reflect nested class iteration
                with(Study) {
                    migrateTo(driver, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                with(ObservationUnit) {
                    migrateTo(driver, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                with(ObservationUnitAttribute) {
                    migrateTo(driver, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                with(ObservationUnitValue) {
                    migrateTo(driver, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                with(ObservationVariable) {
                    migrateTo(driver, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                with(ObservationVariableAttribute) {
                    execute(driver, createTableStatement(tableName, columnDefs))
                }

                with(ObservationVariableValue) {
                    execute(driver, createTableStatement(tableName, columnDefs))
                }

                with(Observation) {
                    migrateTo(driver, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                //insert all default columns as observation variable attribute rows
                val attrIds = mutableMapOf<String, String>()

                ObservationVariableAttribute.defaultColumnDefs.asSequence().forEach { entry ->

                    val attrId = insertWithReturningId(driver, ObservationVariableAttribute.tableName, mapOf(
                            "observation_variable_attribute_name" to entry.key,
                    ))

                    //associate default column names with rowids of the attribute inserted
                    attrIds[entry.key] = attrId.toString()
                }

//                println("ids: $attrIds")

                //val traits = ObservationVariableDao.getAllTraitObjects()
                //iterate over all traits, insert observation variable values using the above mapping
                //old schema has extra columns in the trait table which are now bridged with attr/vals in the new schema
                traits.forEachIndexed { _, trait ->
                    //iterate trhough mapping of the old columns that are now attr/vals
                    mapOf(
                            "validValuesMin" to (trait.minimum ?: ""),
                            "validValuesMax" to (trait.maximum ?: ""),
                            "category" to (trait.categories ?: ""),
                    ).asSequence().forEach { attrValue ->

                        //TODO: commenting this out would create a sparse table from the unused attribute values
//                        if (attrValue.value.isNotEmpty()) {

                        insertWithReturningId(driver, ObservationVariableValue.tableName, mapOf(

                                ObservationVariable.FK to trait.id,
                                ObservationVariableAttribute.FK to attrIds[attrValue.key],
                                "observation_variable_attribute_value" to attrValue.value,
                        ))

//                            println("$rowid Inserting ${attrValue.key} = ${attrValue.value} at ${attrIds[attrValue.key]}")
//                        }
                    }
                }

                execute(driver, sVisibleObservationVariableView)

                execute(driver, sLocalImageObservationsView)

                execute(driver, sNonImageObservationsView)

                execute(driver, sRemoteImageObservationsView)

                //all views but ObservationUnitProperty are created

                commitTransaction(driver) //commits the transaction

            } catch (constraint: Exception) {

                constraint.printStackTrace()

                try {
                    rollbackTransaction(driver)
                } catch (_: Throwable) {
                }

            } catch (e: Exception) {

                //an error caught during a transaction will rollback
                    // before setTransactionSuccessful is called

                e.printStackTrace()

                try {
                    rollbackTransaction(driver)
                } catch (_: Throwable) {
                }

            }

        }

        /**
         * Builds a create table statement based on given parameters:
         * name: name of table to be created
         * columnDefs: mapping between column name and affinity
         * compositeKey: simply the primary key to this table, e.g [internal_study_db_id INT PRIMARY KEY AUTOGENERATE]
         */
        private fun createTableStatement(name: String,
                                         columnDefs: Map<String, String>,
                                         compositeKey: String? = null) = """
            CREATE TABLE IF NOT EXISTS $name ${
                    columnDefs
                            .map { col -> "${col.key} ${col.value}" }
                            .joinToString(",", "(", compositeKey ?: "")
                });
            """.trimIndent()


        /**
         * Small helpers that wrap SqlDriver execute/query for the migration implementation.
         */
        private fun execute(driver: SqlDriver, sql: String) {
            driver.execute(null, sql, 0)
        }

        private fun beginTransaction(driver: SqlDriver) {
            execute(driver, "BEGIN TRANSACTION;")
        }

        private fun commitTransaction(driver: SqlDriver) {
            execute(driver, "COMMIT;")
        }

        private fun rollbackTransaction(driver: SqlDriver) {
            try {
                execute(driver, "ROLLBACK;")
            } catch (_: Throwable) {
            }
        }

        // Helper to unwrap SqlCursor.next() which returns a QueryResult<Boolean> in SQLDelight
        private fun hasNext(cursor: SqlCursor): Boolean {
            return when (val r = cursor.next()) {
                is QueryResult.Value<*> -> (r.value as? Boolean) == true
                else -> false
            }
        }

        private fun escapeSqlLiteral(value: Any?): String = when (value) {
            null -> "NULL"
            is Number -> value.toString()
            is Boolean -> if (value) "1" else "0"
            else -> "'${value.toString().replace("'", "''")}'"
        }

        private fun insertWithReturningId(driver: SqlDriver, table: String, values: Map<String, Any?>): Long {
            if (values.isEmpty()) return -1

            val columns = values.keys.joinToString(",")
            val vals = values.values.joinToString(",") { escapeSqlLiteral(it) }
            val sql = "INSERT INTO $table ($columns) VALUES ($vals);"

            execute(driver, sql)

            // Query last_insert_rowid
            return try {
                val result = driver.executeQuery(null, "SELECT last_insert_rowid();", { cursor: SqlCursor ->
                    val v = cursor.getLong(0) ?: 0L
                    QueryResult.Value(v)
                }, 0)

                when (result) {
                    is QueryResult.Value<*> -> (result.value as? Long) ?: -1L
                    else -> -1L
                }

            } catch (_: Throwable) {
                -1L
            }
        }

        /**
         * Uses the createTables and selectAll functions to migrate a schema.
         * driver: the SqlDelight driver to use.
         * from: the table name to migrate from
         * to: the table renaming
         * columnDefs: map entries where keys are column names and values are column affinities
         * pattern: map entries where keys are original column names and values are new schema names
         */
        private fun migrateTo(driver: SqlDriver, from: String, to: String, columnDefs: Map<String, String>, pattern: Map<String, String>) {

            val table = createTableStatement(to, columnDefs)

            execute(driver, table)

            selectAll(driver, from, pattern) { models ->

                models.forEach {

                    insertWithReturningId(driver, to, it)

                }
            }
        }

        /**
         * Function called to drop entire refactored database schema.
         * Is also called before schema is created.
         */
        fun deleteTables(driver: SqlDriver): Int = try {

            sTableNames.forEach<String> {

                execute(driver, dropTableStatement(it))

            }

            sViewNames.forEach {

                execute(driver, dropViewStatement(it))

            }

            1

        } catch (e: Exception) {

            e.printStackTrace()

            -1
        }

        private fun dropTableStatement(name: String) = """
            DROP TABLE IF EXISTS $name
        """.trimIndent()

        private fun dropViewStatement(name: String) = """
            DROP VIEW IF EXISTS $name
        """.trimIndent()

        /**
         * Used to select all rows from previous database schema.
         * Used in conjunction with toMigratedTable which applies the naming pattern.
         * This implementation generates a SELECT that aliases old column names to the new schema names
         * so that we can read rows without relying on Android Cursor APIs.
         */
        fun selectAll(driver: SqlDriver, name: String, pattern: Map<String, String>, function: (MutableList<Map<String, Any?>>) -> Unit) {

            // build a select list mapping old column names to new schema names
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

                    // iterate over rows
                    while (hasNext(cursor)) {
                        val row = mutableMapOf<String, Any?>()

                        // columns correspond to pattern.values in same order
                        for (i in entries.indices) {
                            val colName = entries[i].value

                            // try reading as long, then string, then bytes
                            val longVal = cursor.getLong(i)
                            if (longVal != null) {
                                // mimic original behavior: integers where appropriate
                                row[colName] = if (entries[i].key == "realPosition") traitPosition++ else longVal
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

        // Stub for example migrations; no-op in common code to avoid unresolved references
        fun migrateToVersionExampleN(driver: SqlDriver) {
            // Intentionally left blank; platform-specific migrators can be invoked elsewhere.
        }

    }

    class ObservationUnit private constructor() {

        companion object Schema {
            const val PK = "internal_id_observation_unit"
            const val FK = "observation_unit_id"
            const val migrateFromTableName = "plots"
            const val tableName = "observation_units"
            val columnDefs by lazy {
                mapOf(PK to "INTEGER PRIMARY KEY AUTOINCREMENT",
                        Study.FK to "INT REFERENCES ${Study.tableName}(${Study.PK}) ON DELETE CASCADE",
                        "observation_unit_db_id" to "TEXT",
                        "primary_id" to "TEXT",
                        "secondary_id" to "TEXT",
                        "geo_coordinates" to "TEXT",
                        "additional_info" to "TEXT",
                        "germplasm_db_id" to "TEXT",
                        "germplasm_name" to "TEXT",
                        "observation_level" to "TEXT",
                        "position_coordinate_x" to "TEXT",
                        "position_coordinate_x_type" to "TEXT",
                        "position_coordinate_y" to "TEXT",
                        "position_coordinate_y_type" to "TEXT"
                )
            }
            val migratePattern by lazy {
                mapOf("plot_id" to PK,
                        "exp_id" to Study.FK,
                        "unique_id" to "observation_unit_db_id",
                        "primary_id" to "primary_id",
                        "secondary_id" to "secondary_id",
                        "coordinates" to "geo_coordinates",)
            }
        }
    }

    class Observation private constructor() {

        companion object Schema {
            const val PK = "internal_id_observation"
            const val FK = "observation_id"
            const val migrateFromTableName = "user_traits"
            const val tableName = "observations"
            val columnDefs by lazy {
                mapOf(PK to "INTEGER PRIMARY KEY AUTOINCREMENT",
                        ObservationUnit.FK to "TEXT",
                        Study.FK to "INT",
                        ObservationVariable.FK to "INT",
                        "observation_variable_name" to "TEXT",
                        "observation_variable_field_book_format" to "TEXT",
                        "value" to "TEXT",
                        "observation_time_stamp" to "TEXT",
                        "collector" to "TEXT",
                        "geoCoordinates" to "TEXT",
                        "observation_db_id" to "TEXT",
                        "last_synced_time" to "TEXT",
                        "additional_info" to "TEXT",
                        "rep" to "TEXT",
                        "notes" to "TEXT")
            }
            val migratePattern by lazy {
                mapOf("id" to PK,
                        "rid" to ObservationUnit.FK,
                        "exp_id" to Study.FK,
                        "parent" to "observation_variable_name",
                        "trait" to "observation_variable_field_book_format",
                        "userValue" to "value",
                        "timeTaken" to "observation_time_stamp",
                        "person" to "collector",
                        "location" to "geoCoordinates",
                        "observation_db_id" to "observation_db_id",
                        "last_synced_time" to "last_synced_time",
                        "rep" to "rep",
                        "notes" to "notes")
            }
        }
    }

    class ObservationUnitAttribute private constructor() {

        companion object Schema {
            const val PK = "internal_id_observation_unit_attribute"
            const val FK = "observation_unit_attribute_db_id"
            const val migrateFromTableName = "plot_attributes"
            const val tableName = "observation_units_attributes"
            val columnDefs by lazy {
                mapOf(PK to "INTEGER PRIMARY KEY AUTOINCREMENT",
                        "observation_unit_attribute_name" to "TEXT",
                        Study.FK to "INT REFERENCES ${Study.tableName}(${Study.PK}) ON DELETE CASCADE")
            }
            val migratePattern by lazy {
                mapOf("attribute_id" to PK,
                        "attribute_name" to "observation_unit_attribute_name",
                        "exp_id" to Study.FK,)
            }
        }
    }

    class ObservationUnitValue private constructor() {

        companion object Schema {
            const val PK = "internal_id_observation_unit_value"
            const val FK = "observation_unit_value_db_id"
            const val migrateFromTableName = "plot_values"
            const val tableName = "observation_units_values"
            val columnDefs by lazy {
                mapOf(PK to "INTEGER PRIMARY KEY AUTOINCREMENT",
                        "observation_unit_attribute_db_id" to "INT REFERENCES ${ObservationUnitAttribute.tableName}(${ObservationUnitAttribute.PK})",
                        "observation_unit_value_name" to "TEXT",
                        ObservationUnit.FK to "INT",
                        Study.FK to "INT REFERENCES ${Study.tableName}(${Study.PK}) ON DELETE CASCADE")
            }
            val migratePattern by lazy {
                mapOf("attribute_value_id" to PK,
                        "attribute_id" to "observation_unit_attribute_db_id",
                        "attribute_value" to "observation_unit_value_name",
                        "plot_id" to ObservationUnit.FK,
                        "exp_id" to Study.FK,)
            }
        }
    }

    class ObservationVariableAttribute private constructor() {
        companion object Schema {
            val defaultColumnDefs = mapOf(
                    "validValuesMax" to "Text",
                    "validValuesMin" to "Text",
                    "category" to "Text",
                    "decimalPlaces" to "Text",
                    "percentBreaks" to "Text",
                    "dateFormat" to "Text",
                    "multiMeasures" to "Text",
                    "minMeasures" to "Text",
                    "maxMeasures" to "Text",
                    "separatorValue" to "Text",
                    "categoryLimit" to "Text",
                    "formula" to "Text",
            )
            const val PK = "internal_id_observation_variable_attribute"
            const val FK = "observation_variable_attribute_db_id"
            val migrateFromTableName = null
            const val tableName = "observation_variable_attributes"
            val columnDefs by lazy {
                mapOf(PK to "INTEGER PRIMARY KEY AUTOINCREMENT",
                        "observation_variable_attribute_name" to "Text",
                )}
        }
    }

    class ObservationVariable private constructor() {
        companion object Schema {
            const val PK = "internal_id_observation_variable"
            const val FK = "observation_variable_db_id"
            const val migrateFromTableName = "traits"
            const val tableName = "observation_variables"
            val columnDefs by lazy {
                mapOf("observation_variable_name" to "TEXT",
                        "observation_variable_field_book_format" to "TEXT",
                        "default_value" to "TEXT",
                        "visible" to "TEXT",
                        "position" to "INT",
                        "external_db_id" to "TEXT",
                        "trait_data_source" to "TEXT",
                        "additional_info" to "TEXT",
                        "common_crop_name" to "TEXT",
                        "language" to "TEXT",
                        "data_type" to "TEXT",
                        "observation_variable_db_id" to "TEXT",
                        "ontology_db_id" to "TEXT",
                        "ontology_name" to "TEXT",
                        "observation_variable_details" to "TEXT",
                        PK to "INTEGER PRIMARY KEY AUTOINCREMENT")
            }

            val migratePattern by lazy {
                mapOf(  "id" to PK,
                        "trait" to "observation_variable_name",
                        "format" to "observation_variable_field_book_format",
                        "defaultValue" to "default_value",
                        "isVisible" to "visible",
                        "realPosition" to "position",
                        "external_db_id" to "external_db_id",
                        "trait_data_source" to "trait_data_source",
                        "details" to "observation_variable_details")
            }
        }
    }

    class ObservationVariableValue private constructor() {
        companion object Schema {
            const val PK = "internal_id_observation_variable_value"
            const val FK = "observation_variable_value_db_id"
            const val tableName = "observation_variable_values"
            val columnDefs by lazy {
                mapOf(PK to "INTEGER PRIMARY KEY AUTOINCREMENT",
                        ObservationVariableAttribute.FK to "INT REFERENCES ${ObservationVariableAttribute.tableName}(${ObservationVariableAttribute.PK})",
                        "observation_variable_attribute_value" to "TEXT",
                        ObservationVariable.FK to "INT REFERENCES ${ObservationVariable.tableName}(${ObservationVariable.PK}) ON DELETE CASCADE",
                )}
        }
    }

    class Study private constructor() {
        companion object Schema {
            const val PK: String = "internal_id_study"
            const val FK = "study_id"
            const val migrateFromTableName = "exp_id"
            const val tableName = "studies"
            val migratePattern by lazy {
                mapOf("exp_id" to PK,
                        "exp_name" to "study_name",
                        "exp_alias" to "study_alias",
                        "unique_id" to "study_unique_id_name",
                        "primary_id" to "study_primary_id_name",
                        "secondary_id" to "study_secondary_id_name",
                        "exp_layout" to "experimental_design",
                        "exp_species" to "common_crop_name",
                        "exp_sort" to "study_sort_name",
                        "date_import" to "date_import",
                        "date_edit" to "date_edit",
                        "date_export" to "date_export",
                        "exp_source" to "study_source",
                        "count" to "count",)
            }
            val columnDefs by lazy {
                mapOf("study_db_id" to "Text",
                        "study_name" to "Text",
                        "study_alias" to "Text",
                        "study_unique_id_name" to "Text",
                        "study_primary_id_name" to "Text",
                        "study_secondary_id_name" to "Text",
                        "experimental_design" to "Text",
                        "common_crop_name" to "Text",
                        "study_sort_name" to "Text",
                        "date_import" to "Text",
                        "date_edit" to "Text",
                        "date_export" to "Text",
                        "study_source" to "Text",
                        "additional_info" to "Text",
                        "location_db_id" to "Text",
                        "location_name" to "Text",
                        "observation_levels" to "Text",
                        "seasons" to "Text",
                        "start_date" to "Text",
                        "study_code" to "Text",
                        "study_description" to "Text",
                        "study_type" to "Text",
                        "trial_db_id" to "Text",
                        "trial_name" to "Text",
                        "count" to "Int",
                        PK to "INTEGER PRIMARY KEY AUTOINCREMENT")
            }
        }
    }
}
