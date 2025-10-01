package com.fieldbook.shared.database.models

class FieldObject(
    val exp_id: Int,
    val exp_name: String,
    val exp_alias: String = "",
    val unique_id: String = "",
    val primary_id: String = "",
    val secondary_id: String = "",
    val date_import: String = "",
    val date_edit: String? = null,
    val date_export: String? = null,
    val date_sync: String? = null,
    val import_format: String? = null,
    val exp_source: String? = null,
    val count: String? = null,
    val observation_level: String? = null,
    val attribute_count: String? = null,
    val trait_count: String? = null,
    val observation_count: String? = null,
    val trial_name: String? = null,
    val search_attribute: String? = null
)
