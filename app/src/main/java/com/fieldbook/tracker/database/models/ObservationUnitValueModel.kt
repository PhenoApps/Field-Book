package com.fieldbook.tracker.database.models

import com.fieldbook.tracker.database.Row

/**
 * ObservationUnit Attribute/Value Models are used to store
 * user-input plot-level properties, these values are transposed
 * and used as columns in the range view.
 */
data class ObservationUnitValueModel(val map: Row) {
        val internal_id_observation_unit_value: Int by map //comp. pk 1, excel says UnitsAttributes
        val observation_unit_attribute_db_id: Int by map    //foreign key to unit attribute
        val observation_unit_attribute_value: String? by map //says Name in excel, but should this be value?, string value saved for this attribute
        val observation_unit_id: Int by map //comp. pk 2
        val study_db_id: Int by map //comp. pk 3
    companion object {
        const val PK = "internal_id_observation_unit_value"
        const val FK = "observation_unit_value_db_id"
        val migrateFromTableName = "plot_values"
        val tableName = "observation_units_values"
        val columnDefs by lazy {
            mapOf(PK to "INTEGER PRIMARY KEY AUTOINCREMENT",
                    "observation_unit_attribute_db_id" to "INT REFERENCES ${ObservationUnitAttributeModel.tableName}(${ObservationUnitAttributeModel.PK}) ON DELETE CASCADE",
                    "observation_unit_value_name" to "TEXT",
                    ObservationUnitModel.FK to "INT REFERENCES ${ObservationUnitModel.tableName}(${ObservationUnitModel.PK}) ON DELETE CASCADE",
                    StudyModel.FK to "INT REFERENCES ${StudyModel.tableName}(${StudyModel.PK}) ON DELETE CASCADE")
        }
        val migratePattern by lazy {
            mapOf("attribute_value_id" to PK,
                    "attribute_id" to "observation_unit_attribute_db_id",
                    "attribute_value" to "observation_unit_value_name",
                    "plot_id" to ObservationUnitModel.FK,
                    "exp_id" to StudyModel.FK,)
        }
    }
}