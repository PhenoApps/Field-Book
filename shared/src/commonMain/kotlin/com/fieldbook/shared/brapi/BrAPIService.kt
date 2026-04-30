package com.fieldbook.shared.brapi

import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationUnitDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiTraitDetails

interface BrAPIService {
    suspend fun getStudies(
        programDbId: String? = null,
        trialDbId: String? = null,
        paginationManager: BrapiPaginationManager,
    ): BrapiResult<List<BrapiStudyDetails>>

    suspend fun getStudyTraits(
        studyDbId: String,
        pageSize: Int = BrapiPaginationManager.DEFAULT_PAGE_SIZE,
    ): BrapiResult<List<BrapiTraitDetails>>

    suspend fun getStudyObservationUnits(
        studyDbId: String,
        pageSize: Int = BrapiPaginationManager.DEFAULT_PAGE_SIZE,
    ): BrapiResult<List<BrapiObservationUnitDetails>>
}

sealed interface BrapiResult<out T> {
    data class Success<T>(val value: T) : BrapiResult<T>
    data class Failure(val statusCode: Int? = null, val message: String? = null) : BrapiResult<Nothing>
}
