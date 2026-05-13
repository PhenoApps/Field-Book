package com.fieldbook.shared.brapi.model.v2.phenotyping

import kotlinx.serialization.Serializable

@Serializable
data class BrapiTraitDetails(
    val observationVariableDbId: String,
    val observationVariableName: String,
    val format: String,
    val defaultValue: String? = null,
    val minimum: String? = null,
    val maximum: String? = null,
    val categories: String? = null,
    val details: String? = null,
    val commonCropName: String? = null,
    val language: String? = null,
    val dataType: String? = null,
    val ontologyDbId: String? = null,
    val ontologyName: String? = null,
)
