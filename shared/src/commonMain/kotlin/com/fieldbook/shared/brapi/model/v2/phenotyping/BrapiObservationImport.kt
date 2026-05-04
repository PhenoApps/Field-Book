package com.fieldbook.shared.brapi.model.v2.phenotyping

data class BrapiObservationImport(
    val observationDbId: String? = null,
    val observationUnitDbId: String? = null,
    val observationVariableDbId: String? = null,
    val observationVariableName: String? = null,
    val studyDbId: String? = null,
    val value: String? = null,
    val observationTimeStamp: String? = null,
    val collector: String? = null,
)
