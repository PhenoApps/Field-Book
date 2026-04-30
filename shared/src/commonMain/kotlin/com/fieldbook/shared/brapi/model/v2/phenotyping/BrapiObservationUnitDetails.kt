package com.fieldbook.shared.brapi.model.v2.phenotyping

data class BrapiObservationUnitDetails(
    val observationUnitDbId: String,
    val observationUnitName: String?,
    val germplasmDbId: String? = null,
    val germplasmName: String? = null,
)
