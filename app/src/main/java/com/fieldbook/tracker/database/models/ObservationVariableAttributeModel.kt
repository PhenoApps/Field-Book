package com.fieldbook.tracker.database.models

import com.fieldbook.tracker.database.Row

/**
 * Are attribute names unique?
 */
class ObservationVariableAttributeModel(val map: Row) {
        val internal_id_observation_variable_attribute: Int by map
        val observation_variable_attribute_name: String? by map
}