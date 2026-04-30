package com.fieldbook.shared.brapi

import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails

interface BrAPIService {
    suspend fun getStudies(
        programDbId: String? = null,
        trialDbId: String? = null,
        paginationManager: BrapiPaginationManager,
    ): BrapiResult<List<BrapiStudyDetails>>
}

sealed interface BrapiResult<out T> {
    data class Success<T>(val value: T) : BrapiResult<T>
    data class Failure(val statusCode: Int? = null, val message: String? = null) : BrapiResult<Nothing>
}
