package com.fieldbook.shared.database.models

import kotlinx.datetime.Instant

/**
 * Multiplatform representation of an observation, for use in repository and viewmodel.
 */
data class ObservationObject(
    val id: Long,
    val studyId: Long,
    val observationVariableName: String?,
    val observationVariableDbId: Long,
    val observationUnitId: String,
    val value: String?,
    val lastSyncedTime: Instant?,
    val rep: Int?
)

