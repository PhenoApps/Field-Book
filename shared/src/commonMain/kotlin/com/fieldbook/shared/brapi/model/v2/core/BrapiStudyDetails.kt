package com.fieldbook.shared.brapi.model.v2.core

data class BrapiStudyDetails(
    val studyDbId: String,
    val studyName: String?,
    val studyDescription: String? = null,
    val locationName: String? = null,
    val trialDbId: String? = null,
    val trialName: String? = null,
)
