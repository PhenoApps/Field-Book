package com.fieldbook.tracker.database.models

import com.fieldbook.tracker.database.Row

/**
 * Are attribute names unique?
 */
class ObservationUnitAttributeModel(val map: Row) {
        val internal_id_observation_unit_attribute: Int by map
        val observation_unit_attribute_name: String? by map
        val study_id: Int by map //fk to study table
}