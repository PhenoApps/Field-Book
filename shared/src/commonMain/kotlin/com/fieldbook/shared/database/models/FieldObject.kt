package com.fieldbook.shared.database.models

class FieldObject(
    val expId: Int,
    val expName: String,
    val expAlias: String = "",
    val uniqueId: String = "",
    val primaryId: String = "",
    val secondaryId: String = "",
    val dateImport: String = "",
    val dateEdit: String? = null,
    val dateExport: String? = null,
    val dateSync: String? = null,
    val importFormat: String? = null,
    val expSource: String? = null,
    val count: String? = null,
    val observationLevel: String? = null,
    val attributeCount: String? = null,
    val traitCount: String? = null,
    val observationCount: String? = null,
    val trialName: String? = null,
    val searchAttribute: String? = null
)
