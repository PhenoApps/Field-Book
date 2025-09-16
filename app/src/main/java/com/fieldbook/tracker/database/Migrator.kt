package com.fieldbook.tracker.database

import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.core.content.contentValuesOf
import androidx.core.database.getBlobOrNull
import androidx.core.database.getStringOrNull
import com.fieldbook.tracker.database.migrators.RefactorMigratorVersion13
import com.fieldbook.tracker.database.migrators.GroupMigratorVersion14
import com.fieldbook.tracker.database.migrators.SpectralMigratorVersion16
import com.fieldbook.tracker.database.migrators.TraitAliasVersion18
import com.fieldbook.tracker.objects.TraitObject

/**
 * this class only handles the upgrade from DBv8 to DBv9
 *
 * Migration helper class that contains all schema refactoring patterns, table names, and private/foreign key references.
 *
 * Each model class has a respective class here that contains a Schema static object.
 * These classes should never be initialized and are just used in createTables to migrate the database version.
 *
 *
 */
class Migrator {

    companion object {

        const val TAG = "Migrator"

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
                sNonImageObservationsViewName,
                sLocalImageObservationsViewName,
                sRemoteImageObservationsViewName,
                sObservationUnitPropertyViewName)

        fun migrateSchema(db: SQLiteDatabase, traits: ArrayList<TraitObject>) {

            createTables(db, traits)

            removeOldTables(db)
        }

        /**
         * Function that iterates over all version 8 table names and drops them.
         */
        private fun removeOldTables(db: SQLiteDatabase) {

            try {

                db.beginTransaction()

                for (table in arrayOf("exp_id", "plots", "range", "traits", "user_traits")) {

                    db.execSQL("DROP TABLE IF EXISTS $table")
                }

                db.setTransactionSuccessful()

            } catch (e: SQLiteException) {

                e.printStackTrace()

            } finally {

                db.endTransaction()
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
        fun createTables(db: SQLiteDatabase, traits: ArrayList<TraitObject>) {

            try {

                db.beginTransaction()

                deleteTables()

                //maybe replace this wall of with statements with kotlin.reflect nested class iteration
                with(Study) {
                    migrateTo(db, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                with(ObservationUnit) {
                    migrateTo(db, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                with(ObservationUnitAttribute) {
                    migrateTo(db, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                with(ObservationUnitValue) {
                    migrateTo(db, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                with(ObservationVariable) {
                    migrateTo(db, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                with(ObservationVariableAttribute) {
                    db.execSQL(createTableStatement(tableName, columnDefs))
                }

                with(ObservationVariableValue) {
                    db.execSQL(createTableStatement(tableName, columnDefs))
                }

                with(Observation) {
                    migrateTo(db, migrateFromTableName, tableName, columnDefs, migratePattern)
                }

                //insert all default columns as observation variable attribute rows
                val attrIds = mutableMapOf<String, String>()

                ObservationVariableAttribute.defaultColumnDefs.asSequence().forEach { entry ->

                    val attrId = db.insert(ObservationVariableAttribute.tableName, null, contentValuesOf(
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

                        db.insert(ObservationVariableValue.tableName, null, contentValuesOf(

                                ObservationVariable.FK to trait.id,
                                ObservationVariableAttribute.FK to attrIds[attrValue.key],
                                "observation_variable_attribute_value" to attrValue.value,
                        ))

//                            println("$rowid Inserting ${attrValue.key} = ${attrValue.value} at ${attrIds[attrValue.key]}")
//                        }
                    }
                }

                db.execSQL(sLocalImageObservationsView)

                db.execSQL(sNonImageObservationsView)

                db.execSQL(sRemoteImageObservationsView)

                //all views but ObservationUnitProperty are created

                db.setTransactionSuccessful() //commits the transaction

            } catch (constraint: SQLiteConstraintException) {

                constraint.printStackTrace()

            } catch (e: Exception) {

                //an error caught during a transaction will rollback
                    // before setTransactionSuccessful is called

                e.printStackTrace()

            } finally {

                db.endTransaction()

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
         * Extension function that converts a cursor into a map with new column names using the pattern parameter.
         */
        fun Cursor.toMigratedTable(pattern: Map<String, String>): MutableList<Map<String, Any?>> = if (moveToFirst()) {

            val table = mutableListOf<Map<String, Any?>>()

            try {

                //hack to repopulate trait positions if they're shy
                //brapi bug made trait positions empty
                var traitPosition = 1
                do {

                    val row = mutableMapOf<String, Any?>()

                    columnNames.forEach {
                        val index = getColumnIndexOrThrow(it)
                        pattern.getOrElse(it) { null }?.let { colName ->
                            row[colName] = when(it) {
                                "realPosition" -> traitPosition++
                                else -> when (getType(index)) {
                                    0 -> null
                                    1 -> getInt(index)
                                    else -> getStringOrNull(index) ?: getBlobOrNull(index)
                                }
                            }
                        }
                    }

                    table.add(row)

                } while (moveToNext())

            } catch (e: IllegalArgumentException) {
                e.printStackTrace()
            }

            table

        } else mutableListOf()

        /**
         * Uses the createTables and selectAll functions to migrate a schema.
         * db: the SQLite database to use.
         * from: the table name to migrate from
         * to: the table renaming
         * columnDefs: map entries where keys are column names and values are column affinities
         * pattern: map entries where keys are original column names and values are new schema names
         */
        private fun migrateTo(db: SQLiteDatabase, from: String, to: String, columnDefs: Map<String, String>, pattern: Map<String, String>) {

            val table = createTableStatement(to, columnDefs)

            db.execSQL(table)

            selectAll(db, from, pattern) { models ->

                models.forEach {

                    db.insertWithOnConflict(to, null, it.toContentValues(), SQLiteDatabase.CONFLICT_IGNORE)

                }
            }
        }

        /**
         * Function called to drop entire refactored database schema.
         * Is also called before schema is created.
         */
        fun deleteTables(): Int = try {

            sTableNames.forEach<String> {

                dropTableStatement(it)

            }

            sViewNames.forEach {

                dropViewStatement(it)

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
         */
        fun selectAll(db: SQLiteDatabase, name: String, pattern: Map<String, String>, function: (Table) -> Unit) {

            function(db.query(name, null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null).use { it.toMigratedTable(pattern) })

        }

        fun migrateToVersion13(db: SQLiteDatabase) {
            RefactorMigratorVersion13().migrate(db)
                .onFailure {
                    Log.e(TAG, "Failed to migrate to version 13", it)
                }
                .onSuccess {
                    Log.d(TAG, "Migrated to version 13")
                }
        }

        fun migrateToVersion14(db: SQLiteDatabase) {

            GroupMigratorVersion14().migrate(db)
                .onFailure {
                    Log.e(TAG, "Failed to migrate to version 14", it)
                }
                .onSuccess {
                    Log.d(TAG, "Migrated to version 14")
                }
        }

        fun migrateToVersion16(db: SQLiteDatabase) {

            SpectralMigratorVersion16().migrate(db)
                .onFailure {
                    Log.e(TAG, "Failed to migrate to version 16", it)
                }
                .onSuccess {
                    Log.d(TAG, "Migrated to version 16")
                }
        }

        fun migrateToVersion18(db: SQLiteDatabase) {

            TraitAliasVersion18().migrate(db)
                .onFailure {
                    Log.e(TAG, "Failed to migrate to version 18", it)
                }
                .onSuccess {
                    Log.d(TAG, "Migrated to version 18")
                }
        }
    }

    class ObservationUnit private constructor() {

        companion object Schema {
            const val PK = "internal_id_observation_unit"
            const val FK = "observation_unit_id"
            const val BRAPI_KEY = "observation_unit_db_id"
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
            const val INTERNAL_ID_OBSERVATION_UNIT_ATTRIBUTE = "internal_id_observation_unit_attribute"
            const val OBSERVATION_UNIT_ATTRIBUTE_NAME = "observation_unit_attribute_name"
            const val OBSERVATION_UNIT_ATTRIBUTE_DB_ID = "observation_unit_attribute_db_id"
            const val PK = INTERNAL_ID_OBSERVATION_UNIT_ATTRIBUTE
            const val FK = OBSERVATION_UNIT_ATTRIBUTE_DB_ID
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
            const val INTERNAL_ID_OBSERVATION_UNIT_VALUE = "internal_id_observation_unit_value"
            const val OBSERVATION_UNIT_VALUE_NAME = "observation_unit_value_name"
            const val OBSERVATION_UNIT_ATTRIBUTE_DB_ID = "observation_unit_attribute_db_id"
            const val OBSERVATION_UNIT_ID = "observation_unit_id"
            const val STUDY_ID = "study_id"
            const val PK = INTERNAL_ID_OBSERVATION_UNIT_VALUE
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