package com.fieldbook.tracker.database

import android.database.Cursor
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import androidx.core.content.contentValuesOf
import androidx.core.database.getBlobOrNull
import androidx.core.database.getStringOrNull
import com.fieldbook.tracker.objects.TraitObject
import java.util.*

/**
 * Migration helper class that contains all schema refactoring patterns, table names, and private/foreign key references.
 *
 * Each model class has a respective class here that contains a Schema static object.
 * These classes should never be initialized and are just used in createTables to migrate the database version.
 *
 *
 * TODO: replace on delete cascades with manual delete function (that is only used if preference is set)
 */
class Migrator {

    companion object {

        fun migrateSchema(db: SQLiteDatabase, traits: ArrayList<TraitObject>) {

            createTables(db, traits)

            //at this point the new schema has been created through DataHelper
            //next we need to query the study table for unique/primary/secondary ids to build the other queries
            val study = db.query(Study.tableName).toFirst()

            if (study.isNotEmpty()) {

//                helper.switchField(1)
//                StudyDao.switchField(1)

                //create views
                withDatabase { db ->

                    db.execSQL("DROP VIEW IF EXISTS $sImageObservationViewName")

                    db.execSQL(sImageObservationView)
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
        fun createTables(db: SQLiteDatabase, traits: ArrayList<TraitObject>) {

            if (deleteTables() == 1) {

                try {

                    db.beginTransaction()

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

                    //iterate over all traits, insert observation variable values using the above mapping
                    //old schema has extra columns in the trait table which are now bridged with attr/vals in the new schema
                    traits.forEachIndexed { index, trait ->
                        //iterate trhough mapping of the old columns that are now attr/vals
                        mapOf(
                                "validValuesMin" to trait.minimum as String,
                                "validValuesMax" to trait.maximum as String,
                                "category" to trait.categories as String,
                        ).asSequence().forEach { attrValue ->

                            //TODO: commenting this out would create a sparse table from the unused attribute values
//                        if (attrValue.value.isNotEmpty()) {

                            val rowid = db.insert(ObservationVariableValue.tableName, null, contentValuesOf(

                                    ObservationVariable.FK to trait.id,
                                    ObservationVariableAttribute.FK to attrIds[attrValue.key],
                                    "observation_variable_attribute_value" to attrValue.value,
                            ))

//                            println("$rowid Inserting ${attrValue.key} = ${attrValue.value} at ${attrIds[attrValue.key]}")
//                        }
                        }
                    }

                    with(Observation) {
                        migrateTo(db, migrateFromTableName, tableName, columnDefs, migratePattern)
                    }

                    db.execSQL(sVisibleObservationVariableView)

                    db.setTransactionSuccessful()

                } catch (e: Exception) {

                    e.printStackTrace()

                } finally {

                    db.endTransaction()

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
                                         compositeKey: String? = null,
                                         selectStatement: String? = null) = """
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

                do {

                    val row = mutableMapOf<String, Any?>()

                    columnNames.forEach {
                        val index = getColumnIndexOrThrow(it)
                        pattern.getOrElse(it) { null }?.let { colName ->
                            row[colName] = when (getType(index)) {
                                0 -> null
                                1 -> getInt(index)
                                else -> getStringOrNull(index) ?: getBlobOrNull(index)
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

//    println(table)

            db.execSQL(table)

            selectAll(db, from, pattern) { models ->

                models.forEach {
//            println(it)
                    try {
                        db.insertWithOnConflict(to, null, it.toContentValues(), SQLiteDatabase.CONFLICT_IGNORE)
                    } catch (constraint: SQLiteConstraintException) {
                        //constraint.printStackTrace()
                    } catch (exp: Exception) {
                        exp.printStackTrace()
                    }
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

            dropViewStatement(sVisibleObservationVariableViewName)
            dropViewStatement(sObservationUnitPropertyViewName)

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

        private const val TRAITS = "traits"
        private const val USER_TRAITS = "user_traits"
        private val USER_TRAITS_COLS = arrayOf("rid", "parent", "trait", "userValue", "timeTaken", "person", "location", "rep", "notes", "exp_id", "observation_db_id", "last_synced_time")

        const val sVisibleObservationVariableViewName = "VisibleObservationVariable"
        val sVisibleObservationVariableView = """
    CREATE VIEW IF NOT EXISTS $sVisibleObservationVariableViewName 
    AS SELECT ${ObservationVariable.PK}, observation_variable_name, observation_variable_field_book_format, observation_variable_details, default_value, position
    FROM ${ObservationVariable.tableName} WHERE visible LIKE "true" ORDER BY position
""".trimIndent()

        const val sObservationUnitPropertyViewName = "ObservationUnitProperty"
        const val sImageObservationViewName = "ImageObservation"
        val sImageObservationView = """
    CREATE VIEW IF NOT EXISTS $sImageObservationViewName     
    AS SELECT props.id, unit.observation_unit_db_id,
    study.study_alias,
    obs.${Observation.PK}, obs.observation_time_stamp, obs.value, obs.observation_db_id, obs.last_synced_time, obs.collector,
    vars.external_db_id, vars.observation_variable_name, vars.observation_variable_details
    FROM ${Observation.tableName} AS obs
    JOIN ${ObservationUnit.tableName} AS unit ON obs.${ObservationUnit.FK} = unit.${ObservationUnit.PK}
    JOIN $sObservationUnitPropertyViewName AS props ON obs.${ObservationUnit.FK} = unit.${ObservationUnit.PK}
    JOIN ${ObservationVariable.tableName} AS vars ON obs.${ObservationVariable.FK} = vars.${ObservationVariable.PK} 
    JOIN ${Study.tableName} AS study ON obs.${Study.FK} = study.${Study.PK} 
    WHERE vars.observation_variable_field_book_format = 'photo'
""".trimIndent()

//private fun createImageObservationView(hostUrl: String): String = """
//    CREATE VIEW IF NOT EXISTS $sImageObservationViewName
//    AS SELECT props.id, unit.observation_unit_name,
//    vars.external_db_id, vars.trait,
//    obs.time_taken, obs.value,
//    exp_id.exp_alias,
//    user_traits.id, user_traits.observation_db_id, user_traits.last_synced_time, user_traits.person,
//    traits.details
//    FROM ${ObservationModel.tableName} AS obs
//    JOIN ${ObservationUnitModel.tableName} AS unit ON obs.${ObservationUnitModel.FK} = unit.${ObservationUnitModel.PK}
//    JOIN $sObservationUnitPropertyViewName AS props ON obs.${ObservationUnitModel.FK} = unit.${ObservationUnitModel.PK}
//    JOIN ${ObservationVariableModel.tableName} AS vars ON obs.${ObservationVariableModel.FK} = vars.${ObservationVariableModel.PK}
//    JOIN ${StudyModel.tableName} AS study ON obs.${StudyModel.FK} = study.${StudyModel.PK}
//    WHERE study.study_source IS NOT NULL
//    AND vars.trait_data_source = $hostUrl
//    AND obs.value <> ''
//    AND vars.trait_data_source IS NOT NULL
//    AND vars.observation_variable_field_book_format = 'photo'
//""".trimIndent()

        private val sTableNames = arrayOf(
                Study.tableName,
                ObservationUnit.tableName,
                ObservationVariable.tableName,
                Observation.tableName,
                ObservationVariableAttribute.tableName,
                ObservationVariableValue.tableName,
                ObservationUnitAttribute.tableName,
                ObservationUnitValue.tableName)
    }

    class ObservationUnit private constructor() {

        companion object Schema {
            const val PK = "internal_id_observation_unit"
            const val FK = "observation_unit_id"
            const val migrateFromTableName = "plots"
            const val tableName = "observation_units"
            val columnDefs by lazy {
                mapOf(PK to "INTEGER PRIMARY KEY AUTOINCREMENT",
                        "study_db_id" to "INT REFERENCES ${Study.tableName}(${Study.PK}) ON DELETE CASCADE",
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
                        "exp_id" to "study_db_id",
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
                        ObservationUnit.FK to "TEXT REFERENCES ${ObservationUnit.tableName}(${ObservationUnit.PK})",
                        Study.FK to "INT REFERENCES ${Study.tableName}(${Study.PK}) ON DELETE CASCADE",
                        ObservationVariable.FK to "INT REFERENCES ${ObservationVariable.tableName}(${ObservationVariable.PK})",
                        "observation_variable_name" to "TEXT",
                        "observation_variable_field_book_format" to "TEXT",
                        "value" to "TEXT",
                        "observation_time_stamp" to "TEXT",
                        "collector" to "TEXT",
                        "geoCoordinates" to "TEXT",
                        "observation_db_id" to "TEXT",
                        "last_synced_time" to "TEXT",
                        "additional_info" to "TEXT")
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
                        "last_synced_time" to "last_synced_time")
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
                        "observation_unit_attribute_db_id" to "INT REFERENCES ${ObservationUnitAttribute.tableName}(${ObservationUnitAttribute.PK}) ON DELETE CASCADE",
                        "observation_unit_value_name" to "TEXT",
                        ObservationUnit.FK to "INT REFERENCES ${ObservationUnit.tableName}(${ObservationUnit.PK}) ON DELETE CASCADE",
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
                        ObservationVariable.FK to "INT REFERENCES ${ObservationVariableAttribute.tableName}(${ObservationVariableAttribute.PK}) ON DELETE CASCADE",
                        "observation_variable_attribute_value" to "TEXT",
                        ObservationVariable.FK to "INT REFERENCES ${ObservationVariable.tableName}(${ObservationVariable.PK}) ON DELETE CASCADE",
                )}
        }
    }

    class Study private constructor() {
        companion object Schema {
            const val PK: String = "internal_id_study"
            const val FK = "study_db_id"
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
                        "date_export" to "date_export",
                        "exp_source" to "study_source",)
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
                        PK to "INTEGER PRIMARY KEY AUTOINCREMENT")
            }
        }
    }
}