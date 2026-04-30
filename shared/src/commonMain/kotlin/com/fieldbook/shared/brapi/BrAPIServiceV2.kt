package com.fieldbook.shared.brapi

import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails
import com.fieldbook.shared.generated.brapi.v2.core.api.StudiesApi

class BrAPIServiceV2(
    baseUrl: String,
    bearerToken: String? = null,
    private val studiesApi: StudiesApi = StudiesApi(baseUrl = normalizeV2BaseUrl(baseUrl)),
) : BrAPIService {

    init {
        if (!bearerToken.isNullOrBlank()) {
            studiesApi.setBearerToken(bearerToken)
        }
    }

    override suspend fun getStudies(
        programDbId: String?,
        trialDbId: String?,
        paginationManager: BrapiPaginationManager,
    ): BrapiResult<List<BrapiStudyDetails>> {
        val requestedPage = paginationManager.page

        return try {
            val response = studiesApi.studiesGet(
                active = true,
                programDbId = programDbId,
                trialDbId = trialDbId,
                page = paginationManager.page,
                pageSize = paginationManager.pageSize,
            )

            if (!response.success) {
                return BrapiResult.Failure(statusCode = response.status)
            }

            val body = response.body()
            val pagination = body.metadata.pagination
            if (requestedPage == paginationManager.page) {
                paginationManager.updatePageInfo(
                    totalPages = pagination?.totalPages,
                    currentPage = pagination?.currentPage,
                    pageSize = pagination?.pageSize,
                )
            }

            BrapiResult.Success(
                body.result.data.map { study ->
                    BrapiStudyDetails(
                        studyDbId = study.studyDbId,
                        studyName = study.studyName,
                        studyDescription = study.studyDescription,
                        locationName = study.locationName,
                        trialDbId = study.trialDbId,
                        trialName = study.trialName,
                    )
                }
            )
        } catch (error: Exception) {
            BrapiResult.Failure(message = error.message)
        }
    }

    companion object {
        private const val BRAPI_V2_PATH = "/brapi/v2"

        fun normalizeV2BaseUrl(baseUrl: String): String {
            val trimmedBaseUrl = baseUrl.trim().trimEnd('/')
            return if (trimmedBaseUrl.endsWith(BRAPI_V2_PATH, ignoreCase = true)) {
                trimmedBaseUrl
            } else {
                "$trimmedBaseUrl$BRAPI_V2_PATH"
            }
        }
    }
}
