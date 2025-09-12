package com.fieldbook.tracker.database.views

import android.database.sqlite.SQLiteDatabase
import com.fieldbook.tracker.database.Migrator
import com.fieldbook.tracker.database.ObservationVariableAttributeDetailsView
import com.fieldbook.tracker.database.models.TraitAttributes

class ObservationVariableAttributeDetailViewCreator {

    companion object {
            const val TAG = "ObservationVariableAttributeDetailViewCreator"
    }

    /**
     * Drop old views and create new ones.
     * This will dynamically create new columns that have been added to TraitAttributes.ALL,
     * TraitObject should also be adjusted to include these new attributes from the TraitAttributeValuesHelper.
     */
    fun createViews(db: SQLiteDatabase) {
        try {
            db.execSQL("DROP VIEW IF EXISTS ${ObservationVariableAttributeDetailsView.VIEW_NAME}")
            db.execSQL("DROP VIEW IF EXISTS trait_helper_debug_view")

            val attributes = TraitAttributes.ALL.map { it.key }
            db.execSQL(getObservationVariableAttributeDetailsViewSql(attributes))
            db.execSQL(getTraitsHelperDebugViewSql(attributes))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getObservationVariableAttributeDetailsViewSql(attributeKeys: List<String>): String {
        val selectClauses = mutableListOf<String>()

        //select static columns from ObservationVariable table
        selectClauses.add("O.${Migrator.ObservationVariable.Schema.PK} AS ${ObservationVariableAttributeDetailsView.INTERNAL_ID}")
        selectClauses.add("O.observation_variable_name AS ${ObservationVariableAttributeDetailsView.VARIABLE_NAME}")

        //create the dynamic columns from TraitAttributes using max/case statements
        //here likely the TraitAttributes.ALL will always be used
        attributeKeys.forEach { key ->
            selectClauses.add(
                "MAX(CASE WHEN A.observation_variable_attribute_name = '$key' " +
                        "THEN V.observation_variable_attribute_value ELSE NULL END) AS $key"
            )
        }

        val allSelects = selectClauses.joinToString(separator = ",\n")

        //use original view creation SQL
        return """
                CREATE VIEW IF NOT EXISTS ${ObservationVariableAttributeDetailsView.VIEW_NAME}
                AS SELECT
                    $allSelects
                FROM ${Migrator.ObservationVariable.Schema.tableName} AS O
                LEFT JOIN ${Migrator.ObservationVariableValue.Schema.tableName} AS V
                    ON V.${Migrator.ObservationVariable.Schema.FK} = O.${Migrator.ObservationVariable.Schema.PK}
                LEFT JOIN ${Migrator.ObservationVariableAttribute.Schema.tableName} AS A
                    ON A.${Migrator.ObservationVariableAttribute.Schema.PK} = V.${Migrator.ObservationVariableAttribute.Schema.FK}
                GROUP BY O.${Migrator.ObservationVariable.Schema.PK}, O.observation_variable_name
            """.trimIndent()
    }

    fun getTraitsHelperDebugViewSql(attributeKeys: List<String>): String {
        val selectClauses = mutableListOf<String>()

        //build static select columns
        selectClauses.add("O.${Migrator.ObservationVariable.Schema.PK} AS trait_id")
        selectClauses.add("O.observation_variable_name AS trait_name")
        selectClauses.add("O.observation_variable_field_book_format AS format")
        selectClauses.add("O.visible AS is_visible")
        selectClauses.add("O.position AS sort_position")

        //use dynamic columns from TraitAttributes
        attributeKeys.forEach { key ->
            selectClauses.add(
                "MAX(CASE WHEN A.observation_variable_attribute_name = '$key' " +
                        "THEN V.observation_variable_attribute_value ELSE NULL END) AS $key"
            )
        }

        //use other static columns
        selectClauses.add("O.default_value AS default_value")
        selectClauses.add("O.observation_variable_details AS description")
        selectClauses.add("O.external_db_id AS external_db_id")
        selectClauses.add("O.trait_data_source AS trait_data_source")

        val allSelects = selectClauses.joinToString(separator = ",\n")

        return """
                CREATE VIEW IF NOT EXISTS trait_helper_debug_view AS
                SELECT
                    $allSelects
                FROM ${Migrator.ObservationVariable.Schema.tableName} AS O
                LEFT JOIN ${Migrator.ObservationVariableValue.Schema.tableName} AS V
                    ON V.${Migrator.ObservationVariable.Schema.FK} = O.${Migrator.ObservationVariable.Schema.PK}
                LEFT JOIN ${Migrator.ObservationVariableAttribute.Schema.tableName} AS A
                    ON A.${Migrator.ObservationVariableAttribute.Schema.PK} = V.${Migrator.ObservationVariableAttribute.Schema.FK}
                GROUP BY O.${Migrator.ObservationVariable.Schema.PK}, O.observation_variable_name
                ORDER BY O.position;
            """.trimIndent()
    }
}