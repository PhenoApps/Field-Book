package com.fieldbook.shared.brapi

import com.fieldbook.shared.brapi.model.v2.core.BrapiStudyDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiImageExport
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationExport
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationImport
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationUnitDetails
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiTraitDetails
import com.fieldbook.shared.generated.brapi.v2.core.api.StudiesApi
import com.fieldbook.shared.generated.brapi.v2.germplasm.api.GermplasmApi
import com.fieldbook.shared.generated.brapi.v2.germplasm.model.Germplasm
import com.fieldbook.shared.generated.brapi.v2.phenotyping.api.ImagesApi
import com.fieldbook.shared.generated.brapi.v2.phenotyping.api.ObservationUnitsApi
import com.fieldbook.shared.generated.brapi.v2.phenotyping.api.ObservationVariablesApi
import com.fieldbook.shared.generated.brapi.v2.phenotyping.api.ObservationsApi
import com.fieldbook.shared.generated.brapi.v2.phenotyping.infrastructure.ApiClient
import com.fieldbook.shared.generated.brapi.v2.phenotyping.model.ExternalReferencesInner
import com.fieldbook.shared.generated.brapi.v2.phenotyping.model.Image
import com.fieldbook.shared.generated.brapi.v2.phenotyping.model.ImageNewRequest
import com.fieldbook.shared.generated.brapi.v2.phenotyping.model.ImageSingleResponse
import com.fieldbook.shared.generated.brapi.v2.phenotyping.model.Observation
import com.fieldbook.shared.generated.brapi.v2.phenotyping.model.ObservationNewRequest
import com.fieldbook.shared.generated.brapi.v2.phenotyping.model.ObservationUnit
import com.fieldbook.shared.generated.brapi.v2.phenotyping.model.ObservationVariable
import com.fieldbook.shared.generated.brapi.v2.phenotyping.model.ObservationVariableScale
import com.fieldbook.shared.utilities.BrAPIScaleValidValuesCategories
import com.fieldbook.shared.utilities.CategoryJsonUtil
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.encodeURLQueryComponent

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
    private val imagesApi: ImagesApi = ImagesApi(baseUrl = normalizeV2BaseUrl(baseUrl)),
    private val germplasmApi: GermplasmApi = GermplasmApi(baseUrl = normalizeV2BaseUrl(baseUrl)),
    private val httpClient: HttpClient = HttpClient(),
) : BrAPIService {
    private val v2BaseUrl = normalizeV2BaseUrl(baseUrl)

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
            imagesApi.setBearerToken(bearerToken)
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
                        seasons = study.seasons.orEmpty().filter(String::isNotBlank),
                        observationVariableDbIds = study.observationVariableDbIds.orEmpty(),
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
        return getTraitsInternal(pageSize = pageSize, studyDbId = studyDbId)
    }

    override suspend fun getTraits(pageSize: Int): BrapiResult<List<BrapiTraitDetails>> {
        return getTraitsInternal(pageSize = pageSize, studyDbId = null)
    }

    private suspend fun getTraitsInternal(
        pageSize: Int,
        studyDbId: String?,
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

    override suspend fun getStudyObservations(
        studyDbId: String,
        observationVariableDbIds: List<String>,
        pageSize: Int,
    ): BrapiResult<List<BrapiObservationImport>> {
        return try {
            val observations = mutableListOf<BrapiObservationImport>()
            val variableFilters: List<String?> = if (observationVariableDbIds.isEmpty()) {
                listOf(null)
            } else {
                observationVariableDbIds
            }

            for (variableDbId in variableFilters) {
                var page = 0
                var totalPages = 1

                do {
                    val response = observationsApi.observationsGet(
                        studyDbId = studyDbId,
                        observationVariableDbId = variableDbId,
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
            }

            BrapiResult.Success(observations)
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

    override suspend fun createImages(
        images: List<BrapiImageExport>,
    ): BrapiResult<List<BrapiImageExport>> {
        if (images.isEmpty()) return BrapiResult.Success(emptyList())
        return exportImages(images, updateMetadata = false)
    }

    override suspend fun updateImages(
        images: List<BrapiImageExport>,
    ): BrapiResult<List<BrapiImageExport>> {
        if (images.isEmpty()) return BrapiResult.Success(emptyList())
        return exportImages(images, updateMetadata = true)
    }

    private suspend fun exportImages(
        images: List<BrapiImageExport>,
        updateMetadata: Boolean,
    ): BrapiResult<List<BrapiImageExport>> {
        return try {
            val uploaded = mutableListOf<BrapiImageExport>()

            images.forEach { image ->
                val metadata = if (updateMetadata) {
                    val imageDbId = image.imageDbId?.takeIf { it.isNotBlank() }
                        ?: return BrapiResult.Failure(message = "Image ${image.fileName} is missing imageDbId.")
                    val response = imagesApi.imagesImageDbIdPut(
                        imageDbId = imageDbId,
                        imageNewRequest = image.toNewRequest(),
                    )
                    if (!response.success) return BrapiResult.Failure(statusCode = response.status)
                    response.body().result
                } else {
                    val response = imagesApi.imagesPost(
                        imageNewRequest = listOf(image.toNewRequest()),
                    )
                    if (!response.success) return BrapiResult.Failure(statusCode = response.status)
                    response.body().result.data.firstOrNull()
                        ?: return BrapiResult.Failure(message = "BrAPI image metadata response was empty.")
                }

                val imageDbId = metadata.imageDbId
                val uploadedMetadata = uploadImageContent(
                    imageDbId = imageDbId,
                    mimeType = image.mimeType,
                    content = image.content,
                ) ?: metadata
                uploaded += uploadedMetadata.toExport(original = image)
            }

            BrapiResult.Success(uploaded)
        } catch (error: Exception) {
            BrapiResult.Failure(message = error.message)
        }
    }

    private suspend fun uploadImageContent(
        imageDbId: String,
        mimeType: String,
        content: ByteArray,
    ): Image? {
        // The generated ImagesApi method for this endpoint uses OctetByteArray through jsonRequest,
        // which serializes the image as JSON/hex instead of sending the raw image/* request body.
        val response = httpClient.put("$v2BaseUrl/images/${imageDbId.encodeURLQueryComponent()}/imagecontent") {
            if (!bearerToken.isNullOrBlank()) {
                header(HttpHeaders.Authorization, "Bearer $bearerToken")
            }
            contentType(ContentType.parse(mimeType))
            setBody(content)
        }

        if (response.status.value !in 200..299) {
            error("BrAPI image content upload failed: ${response.status.value}")
        }

        val body = response.bodyAsText().takeIf { it.isNotBlank() } ?: return null
        return runCatching {
            ApiClient.JSON_DEFAULT.decodeFromString(ImageSingleResponse.serializer(), body).result
        }.getOrNull()
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
                    ?.map { category ->
                        BrAPIScaleValidValuesCategories(
                            label = category.label ?: category.value,
                            value = category.value ?: category.label
                        )
                    }
                    ?.let(CategoryJsonUtil.Companion::buildCategoryList),
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
                            ?.let { levelCode ->
                                if (attributeName !in attributes) {
                                    attributes[attributeName] = levelCode
                                }
                            }
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

        private fun BrapiImageExport.toNewRequest(): ImageNewRequest {
            return ImageNewRequest(
                imageFileName = fileName,
                imageFileSize = fileSize ?: content.size,
                imageName = imageName,
                imageTimeStamp = observationTimeStamp,
                mimeType = mimeType,
                observationUnitDbId = observationUnitDbId,
            )
        }

        private fun Image.toExport(original: BrapiImageExport): BrapiImageExport {
            return original.copy(
                imageDbId = imageDbId,
                fileName = imageFileName?.takeIf { it.isNotBlank() } ?: original.fileName,
                imageName = imageName?.takeIf { it.isNotBlank() } ?: original.imageName,
                mimeType = mimeType?.takeIf { it.isNotBlank() } ?: original.mimeType,
                fileSize = imageFileSize ?: original.fileSize,
                observationTimeStamp = imageTimeStamp ?: original.observationTimeStamp,
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

        private fun mapObservationImport(observation: Observation): BrapiObservationImport {
            return BrapiObservationImport(
                observationDbId = observation.observationDbId,
                observationUnitDbId = observation.observationUnitDbId,
                observationVariableDbId = observation.observationVariableDbId,
                observationVariableName = observation.observationVariableName,
                studyDbId = observation.studyDbId,
                value = observation.value,
                observationTimeStamp = observation.observationTimeStamp,
                collector = observation.collector,
            )
        }
    }
}
