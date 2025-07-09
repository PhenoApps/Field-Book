package com.fieldbook.tracker.database.migrators

import android.database.sqlite.SQLiteDatabase
import com.fieldbook.tracker.database.Migrator.ObservationVariable
import com.fieldbook.tracker.database.Migrator.ObservationVariableAttribute
import com.fieldbook.tracker.database.Migrator.ObservationVariableValue
import com.fieldbook.tracker.database.ObservationVariableAttributeDetailsView

class ObservationVariableAttributeDetailMigratorVersion15: FieldBookMigrator {

    companion object {
        val OBSERVATION_VARIABLE_ATTRIBUTE_DETAILS_VIEW = """
            CREATE VIEW IF NOT EXISTS ${ObservationVariableAttributeDetailsView.VIEW_NAME} 
            AS SELECT 
                O.internal_id_observation_variable AS ${ObservationVariableAttributeDetailsView.INTERNAL_ID},
                O.observation_variable_name AS ${ObservationVariableAttributeDetailsView.VARIABLE_NAME},
                MAX(CASE WHEN A.observation_variable_attribute_name = 'validValuesMax' 
                     THEN V.observation_variable_attribute_value ELSE NULL END) AS ${ObservationVariableAttributeDetailsView.VALID_VALUES_MAX},
                MAX(CASE WHEN A.observation_variable_attribute_name = 'validValuesMin' 
                     THEN V.observation_variable_attribute_value ELSE NULL END) AS ${ObservationVariableAttributeDetailsView.VALID_VALUES_MIN},
                MAX(CASE WHEN A.observation_variable_attribute_name = 'category' 
                     THEN V.observation_variable_attribute_value ELSE NULL END) AS ${ObservationVariableAttributeDetailsView.CATEGORY},
                MAX(CASE WHEN A.observation_variable_attribute_name = 'closeKeyboardOnOpen' 
                     THEN V.observation_variable_attribute_value ELSE NULL END) AS ${ObservationVariableAttributeDetailsView.CLOSE_KEYBOARD_ON_OPEN},
                MAX(CASE WHEN A.observation_variable_attribute_name = 'cropImage' 
                     THEN V.observation_variable_attribute_value ELSE NULL END) AS ${ObservationVariableAttributeDetailsView.CROP_IMAGE}
            FROM ${ObservationVariable.tableName} AS O
            LEFT JOIN ${ObservationVariableValue.tableName} AS V 
                ON V.${ObservationVariable.FK} = O.${ObservationVariable.PK}
            LEFT JOIN ${ObservationVariableAttribute.tableName} AS A 
                ON A.${ObservationVariableAttribute.PK} = V.${ObservationVariableAttribute.FK}
            GROUP BY O.${ObservationVariable.PK}, O.observation_variable_name
        """.trimIndent()

        val TRAITS_HELPER_DEBUG_VIEW = """
            CREATE VIEW IF NOT EXISTS trait_helper_debug_view AS
            SELECT
                O.internal_id_observation_variable AS trait_id,
                O.observation_variable_name AS trait_name,
                O.observation_variable_field_book_format AS format,
                O.visible AS is_visible,
                O.position AS sort_position,

                MAX(CASE WHEN A.observation_variable_attribute_name = 'validValuesMax'
                     THEN V.observation_variable_attribute_value ELSE NULL END) AS max_value,
                MAX(CASE WHEN A.observation_variable_attribute_name = 'validValuesMin'
                     THEN V.observation_variable_attribute_value ELSE NULL END) AS min_value,
                MAX(CASE WHEN A.observation_variable_attribute_name = 'category'
                     THEN V.observation_variable_attribute_value ELSE NULL END) AS categories,
                MAX(CASE WHEN A.observation_variable_attribute_name = 'closeKeyboardOnOpen'
                     THEN V.observation_variable_attribute_value ELSE NULL END) AS close_keyboard,
                MAX(CASE WHEN A.observation_variable_attribute_name = 'cropImage'
                     THEN V.observation_variable_attribute_value ELSE NULL END) AS crop_image,

                O.default_value,
                O.observation_variable_details AS description,
                O.external_db_id,
                O.trait_data_source
                
            FROM ${ObservationVariable.tableName} AS O
            LEFT JOIN ${ObservationVariableValue.tableName} AS V 
                ON V.${ObservationVariable.FK} = O.${ObservationVariable.PK}
            LEFT JOIN ${ObservationVariableAttribute.tableName} AS A 
                ON A.${ObservationVariableAttribute.PK} = V.${ObservationVariableAttribute.FK}
            GROUP BY O.${ObservationVariable.PK}, O.observation_variable_name
            ORDER BY O.position;
        """.trimIndent()
    }

    override fun migrate(db: SQLiteDatabase): Result<Any> = runCatching {
        db.execSQL(OBSERVATION_VARIABLE_ATTRIBUTE_DETAILS_VIEW)
        db.execSQL(TRAITS_HELPER_DEBUG_VIEW)
    }
}