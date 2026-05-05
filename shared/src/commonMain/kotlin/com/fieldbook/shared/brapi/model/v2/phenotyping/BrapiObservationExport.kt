package com.fieldbook.shared.brapi.model.v2.phenotyping

data class BrapiObservationExport(
    val fieldBookDbId: String,
    val observationDbId: String? = null,
    val observationUnitDbId: String,
    val observationVariableDbId: String,
    val observationVariableName: String? = null,
    val studyDbId: String,
    val value: String,
    val observationTimeStamp: String? = null,
    val lastSyncedTime: String? = null,
    val collector: String? = null,
) {
    enum class Status {
        NEW,
        SYNCED,
        EDITED,
        INCOMPLETE,
        INVALID
    }

    val status: Status
        get() = when {
            observationDbId.isNullOrBlank() -> Status.NEW
            lastSyncedTime.isNullOrBlank() -> Status.INCOMPLETE
            observationTimeStamp.isNullOrBlank() || observationTimeStamp <= lastSyncedTime -> Status.SYNCED
            observationTimeStamp > lastSyncedTime -> Status.EDITED
            else -> Status.INVALID
        }
}
