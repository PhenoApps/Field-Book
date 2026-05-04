package com.fieldbook.shared.brapi

import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationExport
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationUnitDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiTraitDetails
import com.fieldbook.shared.generated.brapi.v2.core.api.StudiesApi
import com.fieldbook.shared.generated.brapi.v2.germplasm.api.GermplasmApi
import com.fieldbook.shared.generated.brapi.v2.germplasm.model.Germplasm
import com.fieldbook.shared.generated.brapi.v2.phenotyping.api.ObservationUnitsApi
import com.fieldbook.shared.generated.brapi.v2.phenotyping.api.ObservationVariablesApi
import com.fieldbook.shared.generated.brapi.v2.phenotyping.api.ObservationsApi
import com.fieldbook.shared.generated.brapi.v2.phenotyping.model.ExternalReferencesInner
import com.fieldbook.shared.generated.brapi.v2.phenotyping.model.Observation
import com.fieldbook.shared.generated.brapi.v2.phenotyping.model.ObservationNewRequest
import com.fieldbook.shared.generated.brapi.v2.phenotyping.model.ObservationUnit
import com.fieldbook.shared.generated.brapi.v2.phenotyping.model.ObservationVariable
import com.fieldbook.shared.generated.brapi.v2.phenotyping.model.ObservationVariableScale

class BrAPIServiceV2(
    baseUrl: String,
    private val bearerToken: String? = null,
    private val studiesApi: StudiesApi = StudiesApi(baseUrl = normalizeV2BaseUrl(baseUrl)),
    private val observationVariablesApi: ObservationVariablesApi = ObservationVariablesApi(
        baseUrl = normalizeV2BaseUrl(baseUrl)
    ),
    private val observationUnitsApi: ObservationUnitsApi = ObservationUnitsApi(
        baseUrl = normalizeV2BaseUrl(baseUrl)
    ),
    private val observationsApi: ObservationsApi = ObservationsApi(
        baseUrl = normalizeV2BaseUrl(baseUrl)
    ),
    private val germplasmApi: GermplasmApi = GermplasmApi(baseUrl = normalizeV2BaseUrl(baseUrl)),
) : BrAPIService {
    private data class BrapiGermplasmDetails(
        val accessionNumber: String? = null,
        val pedigree: String? = null,
        val synonyms: String? = null,
    )

    init {
        if (!bearerToken.isNullOrBlank()) {
            studiesApi.setBearerToken(bearerToken)
            observationVariablesApi.setBearerToken(bearerToken)
            observationUnitsApi.setBearerToken(bearerToken)
            observationsApi.setBearerToken(bearerToken)
            germplasmApi.setBearerToken(bearerToken)
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
                        commonCropName = study.commonCropName,
                        trialDbId = study.trialDbId,
                        trialName = study.trialName,
                    )
                }
            )
        } catch (error: Exception) {
            BrapiResult.Failure(message = error.message)
        }
    }

    override suspend fun getStudyObservationUnits(
        studyDbId: String,
        pageSize: Int,
    ): BrapiResult<List<BrapiObservationUnitDetails>> {
        return try {
            val nativeUnits = mutableListOf<ObservationUnit>()
            var page = 0
            var totalPages = 1

            do {
                val response = observationUnitsApi.observationunitsGet(
                    studyDbId = studyDbId,
                    page = page,
                    pageSize = pageSize,
                )

                if (!response.success) {
                    return BrapiResult.Failure(statusCode = response.status)
                }

                val body = response.body()
                nativeUnits += body.result.data
                totalPages = body.metadata.pagination?.totalPages ?: 1
                page++
            } while (page < totalPages)

            val germplasm = fetchStudyGermplasm(studyDbId, pageSize)
            val units = nativeUnits.mapNotNull { unit ->
                mapObservationUnit(unit, germplasm[unit.germplasmDbId])
            }

            BrapiResult.Success(units)
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
                val response = observationVariablesApi.variablesGet(
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

    override suspend fun createObservations(
        observations: List<BrapiObservationExport>,
    ): BrapiResult<List<BrapiObservationExport>> {
        if (observations.isEmpty()) return BrapiResult.Success(emptyList())

        return try {
            val response = observationsApi.observationsPost(
                observationNewRequest = observations.map { it.toNewRequest() }
            )

            if (!response.success) {
                return BrapiResult.Failure(statusCode = response.status)
            }

            BrapiResult.Success(response.body().result.data.map(::mapObservation))
        } catch (error: Exception) {
            BrapiResult.Failure(message = error.message)
        }
    }

    override suspend fun updateObservations(
        observations: List<BrapiObservationExport>,
    ): BrapiResult<List<BrapiObservationExport>> {
        if (observations.isEmpty()) return BrapiResult.Success(emptyList())

        return try {
            val request = observations
                .mapNotNull { observation ->
                    val observationDbId = observation.observationDbId?.takeIf { it.isNotBlank() }
                    observationDbId?.let { it to observation.toNewRequest() }
                }
                .toMap()

            val response = observationsApi.observationsPut(requestBody = request)

            if (!response.success) {
                return BrapiResult.Failure(statusCode = response.status)
            }

            BrapiResult.Success(response.body().result.data.map(::mapObservation))
        } catch (error: Exception) {
            BrapiResult.Failure(message = error.message)
        }
    }

    private suspend fun fetchStudyGermplasm(
        studyDbId: String,
        pageSize: Int,
    ): Map<String, BrapiGermplasmDetails> {
        val germplasm = linkedMapOf<String, BrapiGermplasmDetails>()

        return try {
            var page = 0
            var totalPages = 1

            do {
                val response = germplasmApi.germplasmGet(
                    studyDbId = studyDbId,
                    page = page,
                    pageSize = pageSize,
                )

                if (!response.success) {
                    return germplasm
                }

                val body = response.body()
                body.result.data.forEach { model ->
                    germplasm[model.germplasmDbId] = model.toDetails()
                }

                totalPages = body.metadata.pagination?.totalPages ?: 1
                page++
            } while (page < totalPages)

            germplasm
        } catch (_: Exception) {
            emptyMap()
        }
    }

    companion object {
        private const val BRAPI_V2_PATH = "/brapi/v2"
        private const val FIELD_BOOK_REFERENCE_SOURCE = "Field Book Upload"

        fun normalizeV2BaseUrl(baseUrl: String): String {
            val trimmedBaseUrl = baseUrl.trim().trimEnd('/')
            return if (trimmedBaseUrl.endsWith(BRAPI_V2_PATH, ignoreCase = true)) {
                trimmedBaseUrl
            } else {
                "$trimmedBaseUrl$BRAPI_V2_PATH"
            }
        }

        private fun mapTrait(variable: ObservationVariable): BrapiTraitDetails {
            val validValues = variable.scale.validValues
            return BrapiTraitDetails(
                observationVariableDbId = variable.observationVariableDbId,
                observationVariableName = variable.observationVariableName,
                format = variable.scale.dataType.toFieldBookFormat(),
                defaultValue = variable.defaultValue,
                minimum = validValues?.minimumValue ?: validValues?.min?.toString(),
                maximum = validValues?.maximumValue ?: validValues?.max?.toString(),
                categories = validValues?.categories
                    ?.mapNotNull { category -> category.value ?: category.label }
                    ?.joinToString(","),
                details = variable.trait.traitDescription,
                commonCropName = variable.commonCropName,
                language = variable.language,
                dataType = variable.scale.dataType?.value,
                ontologyDbId = variable.ontologyReference?.ontologyDbId,
                ontologyName = variable.ontologyReference?.ontologyName,
            )
        }

        private fun mapObservationUnit(
            unit: ObservationUnit,
            germplasm: BrapiGermplasmDetails?,
        ): BrapiObservationUnitDetails? {
            val unitDbId = unit.observationUnitDbId?.takeIf { it.isNotBlank() } ?: return null
            return BrapiObservationUnitDetails(
                observationUnitDbId = unitDbId,
                observationUnitName = unit.observationUnitName,
                germplasmDbId = unit.germplasmDbId,
                germplasmName = unit.germplasmName,
                attributes = unit.toImportAttributes(germplasm),
            )
        }

        private fun Germplasm.toDetails(): BrapiGermplasmDetails {
            val synonyms = synonyms
                ?.mapNotNull { synonym -> synonym.synonym?.replace("\"", "\"\"") }
                ?.filter { it.isNotBlank() }
                ?.joinToString("; ")

            return BrapiGermplasmDetails(
                accessionNumber = accessionNumber,
                pedigree = pedigree,
                synonyms = synonyms,
            )
        }

        private fun ObservationUnit.toImportAttributes(germplasm: BrapiGermplasmDetails?): Map<String, String> {
            val attributes = linkedMapOf<String, String>()

            germplasmName?.takeIf { it.isNotBlank() }?.let { attributes["Germplasm"] = it }
            locationName?.takeIf { it.isNotBlank() }?.let { attributes["Location"] = it }

            observationUnitPosition?.let { position ->
                position.observationLevelRelationships.orEmpty().forEach { level ->
                    level.levelName?.toAttributeName()?.let { attributeName ->
                        level.levelCode?.takeIf { it.isNotBlank() }?.let { attributes[attributeName] = it }
                    }
                }

                position.observationLevel?.let { level ->
                    level.levelName?.toAttributeName()?.let { attributeName ->
                        level.levelCode
                            ?.takeIf { it.isNotBlank() }
                            ?.let { attributes.putIfAbsent(attributeName, it) }
                    }
                }

                position.positionCoordinateX?.takeIf { it.isNotBlank() }?.let { x ->
                    attributes[position.positionCoordinateXType?.name.toRowColName() ?: "Row"] = x
                }

                position.positionCoordinateY?.takeIf { it.isNotBlank() }?.let { y ->
                    attributes[position.positionCoordinateYType?.name.toRowColName() ?: "Column"] = y
                }

                position.entryType?.takeIf { it.isNotBlank() }?.let { attributes["EntryType"] = it }
            }

            germplasm?.accessionNumber?.takeIf { it.isNotBlank() }?.let { attributes["AccessionNumber"] = it }
            germplasm?.pedigree?.takeIf { it.isNotBlank() }?.let { attributes["Pedigree"] = it }
            germplasm?.synonyms?.takeIf { it.isNotBlank() }?.let { attributes["Synonyms"] = it }
            observationUnitDbId?.takeIf { it.isNotBlank() }?.let { attributes["ObservationUnitDbId"] = it }
            observationUnitName?.takeIf { it.isNotBlank() }?.let { attributes["ObservationUnitName"] = it }

            return attributes
        }

        private fun String.toAttributeName(): String {
            return replaceFirstChar { char ->
                if (char.isLowerCase()) char.titlecase() else char.toString()
            }
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

        private fun ObservationVariableScale.DataType?.toFieldBookFormat(): String {
            return when (this) {
                ObservationVariableScale.DataType.NUMERICAL -> "numeric"
                ObservationVariableScale.DataType.DATE -> "date"
                ObservationVariableScale.DataType.NOMINAL,
                ObservationVariableScale.DataType.ORDINAL -> "categorical"
                ObservationVariableScale.DataType.CODE,
                ObservationVariableScale.DataType.DURATION,
                ObservationVariableScale.DataType.TEXT,
                null -> "text"
            }
        }

        @Suppress("DEPRECATION")
        private fun BrapiObservationExport.toNewRequest(): ObservationNewRequest {
            return ObservationNewRequest(
                collector = collector?.trim(),
                externalReferences = listOf(
                    ExternalReferencesInner(
                        referenceID = fieldBookDbId,
                        referenceId = fieldBookDbId,
                        referenceSource = FIELD_BOOK_REFERENCE_SOURCE,
                    )
                ),
                observationTimeStamp = observationTimeStamp,
                observationUnitDbId = observationUnitDbId,
                observationVariableDbId = observationVariableDbId,
                observationVariableName = observationVariableName,
                studyDbId = studyDbId,
                value = value,
            )
        }

        @Suppress("DEPRECATION")
        private fun mapObservation(observation: Observation): BrapiObservationExport {
            val fieldBookDbId = observation.externalReferences
                ?.firstOrNull { it.referenceSource == FIELD_BOOK_REFERENCE_SOURCE }
                ?.let { it.referenceId ?: it.referenceID }
                .orEmpty()

            return BrapiObservationExport(
                fieldBookDbId = fieldBookDbId,
                observationDbId = observation.observationDbId,
                observationUnitDbId = observation.observationUnitDbId.orEmpty(),
                observationVariableDbId = observation.observationVariableDbId.orEmpty(),
                observationVariableName = observation.observationVariableName,
                studyDbId = observation.studyDbId.orEmpty(),
                value = observation.`value`.orEmpty(),
                observationTimeStamp = observation.observationTimeStamp,
                collector = observation.collector,
            )
        }
    }
}
