package com.fieldbook.shared.brapi.model.v2.core

import kotlinx.serialization.Serializable

@Serializable
data class BrapiStudyDetails(
    val studyDbId: String,
    val studyName: String?,
    val studyDescription: String? = null,
    val locationName: String? = null,
    val commonCropName: String? = null,
    val trialDbId: String? = null,
    val trialName: String? = null,
    val observationVariableDbIds: List<String> = emptyList(),
)
