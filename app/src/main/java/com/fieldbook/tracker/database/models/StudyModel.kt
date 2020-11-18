package com.fieldbook.tracker.database.models

import com.fieldbook.tracker.database.Row

/**
 * Entity definition parameters are exceptions to AOSP coding standards
 * to reflect column names in SQLite table.
 */
data class StudyModel(val map: Row) {

        val study_db_id: String? by map //brapId
        val study_name: String? by map
        val study_alias: String? by map //stored if user edits study_name
        val study_unique_id_name: String? by map //user assigned
        val study_primary_id_name: String? by map //user assigned maybe coordinate_x_type ?
        val study_secondary_id_name: String? by map //user assigned
        val experimental_design: String? by map
        val common_crop_name: String? by map
        val study_sort_name: String? by map //selected column (plot prop or trait?) to sort field by when moving through entries
        val date_import: String? by map
        val date_export: String? by map
        val study_source: String? by map //denotes whether field was imported via brapi or locally
        val additional_info: String? by map //catch all, dont need with attr/value tables, turn into a query when making BrapiStudyDetail
        val location_db_id: String? by map //brapId?
        val location_name: String? by map
        val observation_levels: String? by map
        val season: String? by map
        val start_date: String? by map
        val study_code: String? by map
        val study_description: String? by map
        val study_type: String? by map
        val trial_db_id: String? by map //brapId?
        val trial_name: String? by map
        val internal_id_study: Int by map //pk
}
