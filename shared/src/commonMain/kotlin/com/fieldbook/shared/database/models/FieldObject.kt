package com.fieldbook.shared.database.models

class FieldObject(
    var exp_id: Int? = null,
    var exp_name: String = "",
    var exp_alias: String = "",
    var exp_sort: String? = null,
    var unique_id: String = "",
    var primary_id: String = "",
    var secondary_id: String = "",
    var date_import: String = "",
    var date_edit: String? = null,
    var date_export: String? = null,
    var date_sync: String? = null,
    var import_format: String? = null,
    var exp_source: String? = null,
    var count: String? = null,
    var observation_level: String? = null,
    var attribute_count: String? = null,
    var trait_count: String? = null,
    var observation_count: String? = null,
    var trial_name: String? = null,
    var search_attribute: String? = null
)
