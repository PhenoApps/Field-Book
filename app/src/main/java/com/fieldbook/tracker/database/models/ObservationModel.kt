package com.fieldbook.tracker.database.models

import com.fieldbook.tracker.database.Row

data class ObservationModel(val map: Row) {
        val internal_id_observation: Int by map
        val observation_unit_id: String by map
        val observation_variable_db_id: Int by map
        val observation_variable_field_book_format: String? by map
        val observation_variable_name: String? by map
        val value: String? by map
        val observation_time_stamp: String? by map
        val collector: String? by map
        val geo_coordinates: String? by map
        val study_id: String? by map
        val last_synced_time: String by map
        val additional_info: String? by map
}