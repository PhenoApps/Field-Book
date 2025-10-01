package com.fieldbook.shared.database.models

data class TraitObject(
    var id: Long? = null,
    var name: String = "",
    var format: String? = null,
    var defaultValue: String? = null,
    var minimum: String? = null,
    var maximum: String? = null,
    var details: String? = null,
    var categories: String? = null,
    var visible: String? = null,
    var realPosition: Int = 0,
    var externalDbId: String? = null,
    var traitDataSource: String? = null,
    var additionalInfo: String? = null,
    var commonCropName: String? = null,
    var language: String? = null,
    var dataType: String? = null,
    var observationVariableDbId: String? = null,
    var ontologyDbId: String? = null,
    var ontologyName: String? = null,
)
