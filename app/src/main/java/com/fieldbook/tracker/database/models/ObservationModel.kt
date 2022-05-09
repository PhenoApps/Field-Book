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
        val geo_coordinates: String? = if (map.containsKey("geoCoordinates")) map["geoCoordinates"]?.toString() else null
        val study_id: String = (map["study_id"] ?: -1).toString()
        val last_synced_time: String by map
        val additional_info: String? by map

        fun createMap() = mutableMapOf<String, Any?>(
                "internal_id_observation" to internal_id_observation,
                "observation_unit_id" to observation_unit_id,
                "observation_variable_db_id" to observation_variable_db_id,
                "observation_variable_field_book_format" to observation_variable_field_book_format,
                "observation_variable_name" to observation_variable_name,
                "value" to value,
                "observation_time_stamp" to observation_time_stamp,
                "collector" to collector,
                "geoCoordinates" to geo_coordinates,
                "study_id" to study_id,
                "last_synced_time" to last_synced_time,
                "additional_info" to additional_info
        )
}