package com.fieldbook.shared.brapi

import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiImageExport
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationExport
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationImport
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

    suspend fun getTraits(
        pageSize: Int = BrapiPaginationManager.DEFAULT_PAGE_SIZE,
    ): BrapiResult<List<BrapiTraitDetails>>

    suspend fun getStudyObservationUnits(
        studyDbId: String,
        pageSize: Int = BrapiPaginationManager.DEFAULT_PAGE_SIZE,
    ): BrapiResult<List<BrapiObservationUnitDetails>>

    suspend fun getStudyObservations(
        studyDbId: String,
        observationVariableDbIds: List<String> = emptyList(),
        pageSize: Int = BrapiPaginationManager.DEFAULT_PAGE_SIZE,
    ): BrapiResult<List<BrapiObservationImport>>

    suspend fun createObservations(
        observations: List<BrapiObservationExport>,
    ): BrapiResult<List<BrapiObservationExport>>

    suspend fun updateObservations(
        observations: List<BrapiObservationExport>,
    ): BrapiResult<List<BrapiObservationExport>>

    suspend fun createImages(
        images: List<BrapiImageExport>,
    ): BrapiResult<List<BrapiImageExport>>

    suspend fun updateImages(
        images: List<BrapiImageExport>,
    ): BrapiResult<List<BrapiImageExport>>
}

sealed interface BrapiResult<out T> {
    data class Success<T>(val value: T) : BrapiResult<T>
    data class Failure(val statusCode: Int? = null, val message: String? = null) : BrapiResult<Nothing>
}
