package com.fieldbook.tracker.database.models

import com.fieldbook.tracker.database.Row

/**
 * Trait-level table structure.
 */
data class ObservationVariableModel(val map: Row) {
        val observation_variable_name: String? by map
        val observation_variable_field_book_format: String? by map
        val default_value: String? by map
        val visible: String? by map
        val position: Int by map
        val external_db_id: String? by map
        val trait_data_source: String? by map
        val additional_info: String? by map //use value/attr
        val common_crop_name: String? by map
        val language: String? by map
        val data_type: String? by map
        val observation_variable_db_id: String? by map //brapId ?
        val ontology_db_id: String? by map //brapId
        val ontology_name: String? by map
        val minimum: String? by map
        val maximum: String? by map
        val categories: String? by map
        val observation_variable_details: String? by map
        val internal_id_observation_variable: Int by map
}