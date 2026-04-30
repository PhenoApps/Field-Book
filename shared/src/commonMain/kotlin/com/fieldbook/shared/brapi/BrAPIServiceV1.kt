package com.fieldbook.shared.brapi

import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails

class BrAPIServiceV1: BrAPIService  {
    override suspend fun getStudies(
        programDbId: String?,
        trialDbId: String?,
        paginationManager: BrapiPaginationManager,
    ): BrapiResult<List<BrapiStudyDetails>> {
        return BrapiResult.Failure(message = "BrAPI v1 studies are not implemented in shared yet.")
    }
}
