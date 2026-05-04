package com.fieldbook.shared.brapi

import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationExport
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationImport
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationUnitDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiTraitDetails
import com.fieldbook.shared.generated.brapi.v1.brapi.api.ObservationVariablesApi
import com.fieldbook.shared.generated.brapi.v1.brapi.api.ObservationsApi
import com.fieldbook.shared.generated.brapi.v1.brapi.api.StudiesApi
import com.fieldbook.shared.generated.brapi.v1.brapi.model.NewObservationDbIdsObservationsInner
import com.fieldbook.shared.generated.brapi.v1.brapi.model.Observation
import com.fieldbook.shared.generated.brapi.v1.brapi.model.ObservationUnit
import com.fieldbook.shared.generated.brapi.v1.brapi.model.ObservationVariable
import com.fieldbook.shared.generated.brapi.v1.brapi.model.PhenotypesRequest
import com.fieldbook.shared.generated.brapi.v1.brapi.model.PhenotypesRequestDataInner
import com.fieldbook.shared.generated.brapi.v1.brapi.model.PhenotypesRequestObservation
import com.fieldbook.shared.generated.brapi.v1.brapi.model.StudySummary
import com.fieldbook.shared.generated.brapi.v1.brapi.model.TraitDataType

class BrAPIServiceV1(
    baseUrl: String,
    bearerToken: String? = null,
    private val studiesApi: StudiesApi = StudiesApi(baseUrl = normalizeV1BaseUrl(baseUrl)),
    private val observationVariablesApi: ObservationVariablesApi = ObservationVariablesApi(
        baseUrl = normalizeV1BaseUrl(baseUrl)
    ),
    private val observationsApi: ObservationsApi = ObservationsApi(
        baseUrl = normalizeV1BaseUrl(baseUrl)
    ),
) : BrAPIService {

    init {
        if (!bearerToken.isNullOrBlank()) {
            studiesApi.setBearerToken(bearerToken)
            observationVariablesApi.setBearerToken(bearerToken)
            observationsApi.setBearerToken(bearerToken)
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
                programDbId = programDbId,
                trialDbId = trialDbId,
                active = true,
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

            BrapiResult.Success(body.result.data.mapNotNull(::mapStudy))
        } catch (error: Exception) {
            BrapiResult.Failure(message = error.message)
        }
    }

    override suspend fun getStudyTraits(
        studyDbId: String,
        pageSize: Int,
    ): BrapiResult<List<BrapiTraitDetails>> {
        return try {
            val traits = mutableListOf<BrapiTraitDetails>()
            var page = 0
            var totalPages = 1

            do {
                val response = observationVariablesApi.studiesStudyDbIdObservationvariablesGet(
                    studyDbId = studyDbId,
                    page = page,
                    pageSize = pageSize,
                )

                if (!response.success) {
                    return BrapiResult.Failure(statusCode = response.status)
                }

                val body = response.body()
                traits += body.result.data.map(::mapTrait)
                totalPages = body.metadata.pagination?.totalPages ?: 1
                page++
            } while (page < totalPages)

            BrapiResult.Success(traits)
        } catch (error: Exception) {
            BrapiResult.Failure(message = error.message)
        }
    }

    override suspend fun getStudyObservationUnits(
        studyDbId: String,
        pageSize: Int,
    ): BrapiResult<List<BrapiObservationUnitDetails>> {
        return try {
            val units = mutableListOf<BrapiObservationUnitDetails>()
            var page = 0
            var totalPages = 1

            do {
                val response = studiesApi.studiesStudyDbIdObservationunitsGet(
                    studyDbId = studyDbId,
                    page = page,
                    pageSize = pageSize,
                )

                if (!response.success) {
                    return BrapiResult.Failure(statusCode = response.status)
                }

                val body = response.body()
                units += body.result.data.mapNotNull(::mapObservationUnit)
                totalPages = body.metadata.pagination?.totalPages ?: 1
                page++
            } while (page < totalPages)

            BrapiResult.Success(units)
        } catch (error: Exception) {
            BrapiResult.Failure(message = error.message)
        }
    }

    override suspend fun getStudyObservations(
        studyDbId: String,
        observationVariableDbIds: List<String>,
        pageSize: Int,
    ): BrapiResult<List<BrapiObservationImport>> {
        return try {
            val observations = mutableListOf<BrapiObservationImport>()
            var page = 0
            var totalPages = 1

            do {
                val response = observationsApi.studiesStudyDbIdObservationsGet(
                    studyDbId = studyDbId,
                    observationVariableDbIds = observationVariableDbIds.takeIf { it.isNotEmpty() },
                    page = page,
                    pageSize = pageSize,
                )

                if (!response.success) {
                    return BrapiResult.Failure(statusCode = response.status)
                }

                val body = response.body()
                observations += body.result.data.map(::mapObservationImport)
                totalPages = body.metadata.pagination?.totalPages ?: 1
                page++
            } while (page < totalPages)

            BrapiResult.Success(observations)
        } catch (error: Exception) {
            BrapiResult.Failure(message = error.message)
        }
    }

    override suspend fun createObservations(
        observations: List<BrapiObservationExport>,
    ): BrapiResult<List<BrapiObservationExport>> {
        return exportObservations(observations)
    }

    override suspend fun updateObservations(
        observations: List<BrapiObservationExport>,
    ): BrapiResult<List<BrapiObservationExport>> {
        return exportObservations(observations)
    }

    private suspend fun exportObservations(
        observations: List<BrapiObservationExport>,
    ): BrapiResult<List<BrapiObservationExport>> {
        if (observations.isEmpty()) return BrapiResult.Success(emptyList())

        return try {
            val response = observationsApi.phenotypesPost(
                phenotypesRequest = PhenotypesRequest(
                    data = observations
                        .groupBy { it.studyDbId to it.observationUnitDbId }
                        .map { (key, groupedObservations) ->
                            PhenotypesRequestDataInner(
                                studyDbId = key.first,
                                observatioUnitDbId = key.second,
                                observations = groupedObservations.map { it.toPhenotypeObservation() },
                            )
                        }
                )
            )

            if (!response.success) {
                return BrapiResult.Failure(statusCode = response.status)
            }

            BrapiResult.Success(mergeObservationIds(observations, response.body().result.observations.orEmpty()))
        } catch (error: Exception) {
            BrapiResult.Failure(message = error.message)
        }
    }

    companion object {
        private const val BRAPI_V1_PATH = "/brapi/v1"
        private const val DEFAULT_COLLECTOR = "Field Book"

        fun normalizeV1BaseUrl(baseUrl: String): String {
            val trimmedBaseUrl = baseUrl.trim().trimEnd('/')
            return if (trimmedBaseUrl.endsWith(BRAPI_V1_PATH, ignoreCase = true)) {
                trimmedBaseUrl
            } else {
                "$trimmedBaseUrl$BRAPI_V1_PATH"
            }
        }

        @Suppress("DEPRECATION")
        private fun mapStudy(study: StudySummary): BrapiStudyDetails? {
            val studyDbId = study.studyDbId?.takeIf { it.isNotBlank() } ?: return null
            return BrapiStudyDetails(
                studyDbId = studyDbId,
                studyName = study.studyName ?: study.name,
                locationName = study.locationName,
                commonCropName = study.commonCropName,
                trialDbId = study.trialDbId,
                trialName = study.trialName,
            )
        }

        @Suppress("DEPRECATION")
        private fun mapTrait(variable: ObservationVariable): BrapiTraitDetails {
            val validValues = variable.scale.validValues
            return BrapiTraitDetails(
                observationVariableDbId = variable.observationVariableDbId,
                observationVariableName = variable.observationVariableName.takeIf { it.isNotBlank() }
                    ?: variable.name
                    ?: variable.observationVariableDbId,
                format = variable.scale.dataType.toFieldBookFormat(),
                defaultValue = variable.defaultValue,
                minimum = validValues?.min?.toString(),
                maximum = validValues?.max?.toString(),
                categories = validValues?.categories?.joinToString(","),
                details = variable.trait.description,
                commonCropName = variable.crop,
                language = variable.language,
                dataType = variable.scale.dataType?.value,
                ontologyDbId = variable.ontologyReference?.ontologyDbId ?: variable.ontologyDbId,
                ontologyName = variable.ontologyReference?.ontologyName ?: variable.ontologyName,
            )
        }

        private fun mapObservationUnit(unit: ObservationUnit): BrapiObservationUnitDetails? {
            val unitDbId = unit.observationUnitDbId?.takeIf { it.isNotBlank() } ?: return null
            return BrapiObservationUnitDetails(
                observationUnitDbId = unitDbId,
                observationUnitName = unit.observationUnitName,
                germplasmDbId = unit.germplasmDbId,
                germplasmName = unit.germplasmName,
                attributes = unit.toImportAttributes(),
            )
        }

        private fun ObservationUnit.toImportAttributes(): Map<String, String> {
            val attributes = linkedMapOf<String, String>()

            germplasmName?.takeIf { it.isNotBlank() }?.let { attributes["Germplasm"] = it }
            locationName?.takeIf { it.isNotBlank() }?.let { attributes["Location"] = it }
            pedigree?.takeIf { it.isNotBlank() }?.let { attributes["Pedigree"] = it }
            blockNumber?.takeIf { it.isNotBlank() }?.let { attributes["Block"] = it }
            entryNumber?.takeIf { it.isNotBlank() }?.let { attributes["Entry"] = it }
            plotNumber?.takeIf { it.isNotBlank() }?.let { attributes["Plot"] = it }
            plantNumber?.takeIf { it.isNotBlank() }?.let { attributes["Plant"] = it }
            replicate?.takeIf { it.isNotBlank() }?.let { attributes["Rep"] = it }

            positionCoordinateX?.takeIf { it.isNotBlank() }?.let { x ->
                attributes[positionCoordinateXType?.name.toRowColName() ?: "Row"] = x
            }

            positionCoordinateY?.takeIf { it.isNotBlank() }?.let { y ->
                attributes[positionCoordinateYType?.name.toRowColName() ?: "Column"] = y
            }

            entryType?.takeIf { it.isNotBlank() }?.let { attributes["EntryType"] = it }
            observationUnitDbId?.takeIf { it.isNotBlank() }?.let { attributes["ObservationUnitDbId"] = it }
            observationUnitName?.takeIf { it.isNotBlank() }?.let { attributes["ObservationUnitName"] = it }

            return attributes
        }

        private fun mapObservationImport(observation: Observation): BrapiObservationImport {
            return BrapiObservationImport(
                observationDbId = observation.observationDbId,
                observationUnitDbId = observation.observationUnitDbId,
                observationVariableDbId = observation.observationVariableDbId,
                observationVariableName = observation.observationVariableName,
                studyDbId = observation.studyDbId,
                value = observation.value,
                observationTimeStamp = observation.observationTimeStamp,
                collector = observation.`operator`,
            )
        }

        private fun String?.toRowColName(): String? {
            return when (this) {
                "PLANTED_INDIVIDUAL",
                "GRID_COL",
                "MEASURED_COL" -> "Column"
                "PLANTED_ROW",
                "GRID_ROW",
                "MEASURED_ROW" -> "Row"
                else -> null
            }
        }

        private fun TraitDataType?.toFieldBookFormat(): String {
            return when (this) {
                TraitDataType.NOMINAL,
                TraitDataType.ORDINAL -> "categorical"
                TraitDataType.DATE -> "date"
                TraitDataType.NUMERICAL -> "numeric"
                TraitDataType.CODE,
                TraitDataType.DURATION,
                TraitDataType.TEXT,
                null -> "text"
            }
        }

        private fun BrapiObservationExport.toPhenotypeObservation(): PhenotypesRequestObservation {
            return PhenotypesRequestObservation(
                collector = collector?.trim().takeUnless { it.isNullOrBlank() } ?: DEFAULT_COLLECTOR,
                observationTimeStamp = observationTimeStamp.orEmpty(),
                observationVariableDbId = observationVariableDbId,
                observationVariableName = observationVariableName ?: observationVariableDbId,
                value = value,
                observationDbId = observationDbId?.takeIf { it.isNotBlank() },
            )
        }

        private fun mergeObservationIds(
            originalObservations: List<BrapiObservationExport>,
            returnedObservations: List<NewObservationDbIdsObservationsInner>,
        ): List<BrapiObservationExport> {
            val returnedByKey = returnedObservations
                .filter { !it.observationDbId.isNullOrBlank() }
                .groupBy { it.observationUnitDbId to it.observationVariableDbId }
            val nextReturnedIndexByKey = mutableMapOf<Pair<String?, String?>, Int>()

            return originalObservations.map { original ->
                val key = original.observationUnitDbId to original.observationVariableDbId
                val nextReturnedIndex = nextReturnedIndexByKey[key] ?: 0
                val returned = returnedByKey[key]?.getOrNull(nextReturnedIndex)
                nextReturnedIndexByKey[key] = nextReturnedIndex + 1

                original.copy(observationDbId = returned?.observationDbId ?: original.observationDbId)
            }
        }
    }
}
