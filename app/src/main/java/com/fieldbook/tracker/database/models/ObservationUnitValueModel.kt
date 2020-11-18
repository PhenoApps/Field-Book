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
}