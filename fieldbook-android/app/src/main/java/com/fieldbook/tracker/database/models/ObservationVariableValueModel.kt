package com.fieldbook.tracker.database.models

import com.fieldbook.tracker.database.Row

/**
 * ObservationVariable Attribute/Value Models are used to store
 * user-input traits, these values are transposed
 * and used as columns in the range view.
 */
data class ObservationVariableValueModel(val map: Row) {
        val internal_id_observation_variable_value: Int by map //comp. pk 1
        val observation_variable_attribute_db_id: Int by map      //foreign key to unit attribute
        val observation_variable_attribute_value: String? by map //says Name in excel, but should this be value?, string value saved for this attribute
        val observation_variable_id: Int by map //comp. pk 2
}