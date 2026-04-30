package com.fieldbook.shared.brapi

import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationExport
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationUnitDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiTraitDetails

class BrAPIServiceV1: BrAPIService  {
    override suspend fun getStudies(
        programDbId: String?,
        trialDbId: String?,
        paginationManager: BrapiPaginationManager,
    ): BrapiResult<List<BrapiStudyDetails>> {
        return BrapiResult.Failure(message = "BrAPI v1 studies are not implemented in shared yet.")
    }

    override suspend fun getStudyTraits(
        studyDbId: String,
        pageSize: Int,
    ): BrapiResult<List<BrapiTraitDetails>> {
        return BrapiResult.Failure(message = "BrAPI v1 traits are not implemented in shared yet.")
    }

    override suspend fun getStudyObservationUnits(
        studyDbId: String,
        pageSize: Int,
    ): BrapiResult<List<BrapiObservationUnitDetails>> {
        return BrapiResult.Failure(message = "BrAPI v1 observation units are not implemented in shared yet.")
    }

    override suspend fun createObservations(
        observations: List<BrapiObservationExport>,
    ): BrapiResult<List<BrapiObservationExport>> {
        return BrapiResult.Failure(message = "BrAPI v1 observation export is not implemented in shared yet.")
    }

    override suspend fun updateObservations(
        observations: List<BrapiObservationExport>,
    ): BrapiResult<List<BrapiObservationExport>> {
        return BrapiResult.Failure(message = "BrAPI v1 observation export is not implemented in shared yet.")
    }
}
