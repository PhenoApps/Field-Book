package com.fieldbook.shared.screens.export

import com.fieldbook.shared.brapi.BrAPIService
import com.fieldbook.shared.brapi.BrapiResult
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiImageExport
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationExport
import com.fieldbook.shared.database.models.FieldObject
import com.fieldbook.shared.database.repository.BrapiExportObservationRow
import com.fieldbook.shared.database.repository.ObservationRepository
import com.fieldbook.shared.database.repository.StudyRepository
import com.fieldbook.shared.objects.ImportFormat
import com.fieldbook.shared.utilities.CategoryJsonUtil
import com.fieldbook.shared.utilities.DocumentFile
import com.fieldbook.shared.utilities.DocumentTreeUtil
import com.fieldbook.shared.utilities.internalTimeFormatter
import com.fieldbook.shared.utilities.listFiles
import kotlinx.datetime.Clock
import kotlinx.datetime.format

data class BrapiExportPreview(
    val fields: List<BrapiFieldExportPreview>,
    val canExport: Boolean,
    val message: String? = null,
) {
    val newObservations: Int = fields.sumOf { it.newObservations }
    val syncedObservations: Int = fields.sumOf { it.syncedObservations }
    val editedObservations: Int = fields.sumOf { it.editedObservations }
    val localObservations: Int = fields.sumOf { it.localObservations }
    val wrongSourceObservations: Int = fields.sumOf { it.wrongSourceObservations }
    val newImages: Int = fields.sumOf { it.newImages }
    val syncedImages: Int = fields.sumOf { it.syncedImages }
    val editedImages: Int = fields.sumOf { it.editedImages }
    val invalidImages: Int = fields.sumOf { it.invalidImages }
}

data class BrapiFieldExportPreview(
    val fieldId: Int,
    val fieldName: String,
    val newObservations: Int,
    val syncedObservations: Int,
    val editedObservations: Int,
    val localObservations: Int,
    val wrongSourceObservations: Int,
    val newImages: Int,
    val syncedImages: Int,
    val editedImages: Int,
    val invalidImages: Int,
)

data class BrapiExportResult(
    val created: Int,
    val updated: Int,
    val skippedSynced: Int,
    val imagesCreated: Int,
    val imagesUpdated: Int,
    val imagesSkippedSynced: Int,
)

class BrapiExportSupport(
    private val studyRepository: StudyRepository = StudyRepository(),
    private val observationRepository: ObservationRepository = ObservationRepository(),
) {
    fun preview(fieldIds: List<Int>, hostUrl: String): BrapiExportPreview {
        val fields = fieldIds.map { studyRepository.getById(it) }
        val invalidFields = fields.filterNot { it.isBrapiFieldFrom(hostUrl) }
        if (invalidFields.isNotEmpty()) {
            return BrapiExportPreview(
                fields = emptyList(),
                canExport = false,
                message = "Unable to sync data. Field data originates from ${invalidFields.joinToString(", ") { it.exp_source.orEmpty() }}, BrAPI url currently set to $hostUrl. Sources must match.",
            )
        }

        val previews = fields.mapNotNull { field ->
            val fieldId = field.exp_id ?: return@mapNotNull null
            val observations = getBrapiObservations(fieldId, hostUrl)
            val images = getBrapiImages(fieldId, hostUrl)
            BrapiFieldExportPreview(
                fieldId = fieldId,
                fieldName = field.exp_alias.ifBlank { field.exp_name },
                newObservations = observations.count { it.status == BrapiObservationExport.Status.NEW },
                syncedObservations = observations.count { it.status == BrapiObservationExport.Status.SYNCED },
                editedObservations = observations.count { it.status == BrapiObservationExport.Status.EDITED || it.status == BrapiObservationExport.Status.INCOMPLETE },
                localObservations = countLocalObservations(fieldId),
                wrongSourceObservations = countWrongSourceObservations(hostUrl),
                newImages = images.count { it.status == BrapiImageExport.Status.NEW },
                syncedImages = images.count { it.status == BrapiImageExport.Status.SYNCED },
                editedImages = images.count { it.status == BrapiImageExport.Status.EDITED || it.status == BrapiImageExport.Status.INCOMPLETE },
                invalidImages = images.count { it.status == BrapiImageExport.Status.INVALID },
            )
        }

        return BrapiExportPreview(
            fields = previews,
            canExport = true,
            message = if (previews.sumOf { it.newObservations + it.editedObservations + it.newImages + it.editedImages } == 0) {
                "Nothing to sync"
            } else {
                null
            },
        )
    }

    suspend fun export(
        fieldIds: List<Int>,
        hostUrl: String,
        service: BrAPIService,
    ): BrapiResult<BrapiExportResult> {
        val observations = fieldIds.flatMap { getBrapiObservations(it, hostUrl) }
        val images = fieldIds.flatMap { getBrapiImages(it, hostUrl) }
        val newObservations = observations
            .filter { it.status == BrapiObservationExport.Status.NEW }
            .map { it.withBrapiTimestamp() }
        val editedObservations = observations
            .filter { it.status == BrapiObservationExport.Status.EDITED || it.status == BrapiObservationExport.Status.INCOMPLETE }
            .map { it.withBrapiTimestamp() }
        val newImages = images
            .filter { it.status == BrapiImageExport.Status.NEW }
            .map { it.withBrapiTimestamp() }
        val editedImages = images
            .filter { it.status == BrapiImageExport.Status.EDITED || it.status == BrapiImageExport.Status.INCOMPLETE }
            .map { it.withBrapiTimestamp() }

        var created = 0
        var updated = 0
        var imagesCreated = 0
        var imagesUpdated = 0

        if (newObservations.isNotEmpty()) {
            when (val result = service.createObservations(newObservations)) {
                is BrapiResult.Failure -> return result
                is BrapiResult.Success -> {
                    updateLocalSyncState(newObservations, result.value)
                    created = result.value.size
                }
            }
        }

        if (editedObservations.isNotEmpty()) {
            when (val result = service.updateObservations(editedObservations)) {
                is BrapiResult.Failure -> return result
                is BrapiResult.Success -> {
                    updateLocalSyncState(editedObservations, result.value)
                    updated = result.value.size
                }
            }
        }

        if (newImages.isNotEmpty()) {
            when (val result = service.createImages(newImages)) {
                is BrapiResult.Failure -> return result
                is BrapiResult.Success -> {
                    updateLocalImageSyncState(newImages, result.value)
                    imagesCreated = result.value.size
                }
            }
        }

        if (editedImages.isNotEmpty()) {
            when (val result = service.updateImages(editedImages)) {
                is BrapiResult.Failure -> return result
                is BrapiResult.Success -> {
                    updateLocalImageSyncState(editedImages, result.value)
                    imagesUpdated = result.value.size
                }
            }
        }

        return BrapiResult.Success(
            BrapiExportResult(
                created = created,
                updated = updated,
                skippedSynced = observations.count { it.status == BrapiObservationExport.Status.SYNCED },
                imagesCreated = imagesCreated,
                imagesUpdated = imagesUpdated,
                imagesSkippedSynced = images.count { it.status == BrapiImageExport.Status.SYNCED },
            )
        )
    }

    private fun FieldObject.isBrapiFieldFrom(hostUrl: String): Boolean {
        return ImportFormat.fromString(import_format) == ImportFormat.BRAPI &&
            exp_source?.equals(hostUrl, ignoreCase = true) == true
    }

    private fun getBrapiObservations(fieldId: Int, hostUrl: String): List<BrapiObservationExport> {
        return observationRepository.getBrapiExportObservations(
            studyId = fieldId.toLong(),
            hostUrl = hostUrl,
        ).mapNotNull { row ->
            val value = CategoryJsonUtil.processValue(row.toCategoryValueMap())
            val variableDbId = row.externalTraitDbId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val unitDbId = row.observationUnitDbId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val studyDbId = row.studyDbId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

            BrapiObservationExport(
                fieldBookDbId = row.id.toString(),
                observationDbId = row.observationDbId,
                observationUnitDbId = unitDbId,
                observationVariableDbId = variableDbId,
                observationVariableName = row.observationVariableName,
                studyDbId = studyDbId,
                value = value.orEmpty(),
                observationTimeStamp = row.observationTimeStamp,
                lastSyncedTime = row.lastSyncedTime,
                collector = row.collector,
            )
        }
    }

    private fun getBrapiImages(fieldId: Int, hostUrl: String): List<BrapiImageExport> {
        return observationRepository.getBrapiExportImages(
            studyId = fieldId.toLong(),
            hostUrl = hostUrl,
        ).flatMap { row ->
            val unitDbId = row.observationUnitDbId?.takeIf { it.isNotBlank() } ?: return@flatMap emptyList()
            val traitName = row.observationVariableName?.takeIf { it.isNotBlank() }
            val fieldName = row.studyAlias?.takeIf { it.isNotBlank() } ?: row.studyName

            decodePhotoRefs(row.value.orEmpty()).map { photoRef ->
                val file = findStoredPhotoFile(
                    photoRef = photoRef,
                    fieldName = fieldName,
                    traitName = traitName,
                )
                val content = file?.let { runCatching { it.readBytes() }.getOrDefault(ByteArray(0)) } ?: ByteArray(0)
                val fileName = file?.name()?.takeIf { it.isNotBlank() } ?: extractPhotoFileName(photoRef)
                val mimeType = fileName.toImageMimeType()

                BrapiImageExport(
                    fieldBookDbId = row.id.toString(),
                    imageDbId = row.imageDbId,
                    observationUnitDbId = unitDbId,
                    fileName = fileName,
                    imageName = fileName,
                    mimeType = mimeType,
                    fileSize = content.size,
                    observationTimeStamp = row.observationTimeStamp,
                    lastSyncedTime = row.lastSyncedTime,
                    content = content,
                )
            }
        }
    }

    private fun countLocalObservations(fieldId: Int): Int {
        return observationRepository.countLocalBrapiExportObservations(fieldId.toLong())
    }

    private fun countWrongSourceObservations(hostUrl: String): Int {
        return observationRepository.countWrongSourceBrapiExportObservations(hostUrl)
    }

    private fun updateLocalSyncState(
        inputObservations: List<BrapiObservationExport>,
        responseObservations: List<BrapiObservationExport>,
    ) {
        val now = Clock.System.now().format(internalTimeFormatter)
        val byFieldBookId = inputObservations.associateBy { it.fieldBookDbId }
        val byObservationDbId = inputObservations
            .mapNotNull { observation -> observation.observationDbId?.let { it to observation } }
            .toMap()
        val byKey = inputObservations.associateBy { it.observationUnitDbId to it.observationVariableDbId }

        responseObservations.forEach { response ->
            val original = response.fieldBookDbId.takeIf { it.isNotBlank() }?.let(byFieldBookId::get)
                ?: response.observationDbId?.let(byObservationDbId::get)
                ?: byKey[response.observationUnitDbId to response.observationVariableDbId]
                ?: return@forEach

            val observationDbId = response.observationDbId?.takeIf { it.isNotBlank() }
                ?: original.observationDbId
                ?: return@forEach

            observationRepository.updateBrapiExportSyncState(
                observationId = original.fieldBookDbId.toLong(),
                remoteDbId = observationDbId,
                lastSyncedTime = now,
            )
        }
    }

    private fun updateLocalImageSyncState(
        inputImages: List<BrapiImageExport>,
        responseImages: List<BrapiImageExport>,
    ) {
        val now = Clock.System.now().format(internalTimeFormatter)
        val byFieldBookId = inputImages.associateBy { it.fieldBookDbId }
        val byImageDbId = inputImages
            .mapNotNull { image -> image.imageDbId?.let { it to image } }
            .toMap()

        responseImages.forEach { response ->
            val original = byFieldBookId[response.fieldBookDbId]
                ?: response.imageDbId?.let(byImageDbId::get)
                ?: return@forEach
            val imageDbId = response.imageDbId?.takeIf { it.isNotBlank() }
                ?: original.imageDbId
                ?: return@forEach

            observationRepository.updateBrapiExportSyncState(
                observationId = original.fieldBookDbId.toLong(),
                remoteDbId = imageDbId,
                lastSyncedTime = now,
            )
        }
    }

    private fun BrapiObservationExport.withBrapiTimestamp(): BrapiObservationExport {
        return copy(observationTimeStamp = observationTimeStamp?.replaceFirst(' ', 'T'))
    }

    private fun BrapiImageExport.withBrapiTimestamp(): BrapiImageExport {
        return copy(observationTimeStamp = observationTimeStamp?.replaceFirst(' ', 'T'))
    }

    private fun decodePhotoRefs(value: String): List<String> {
        return value
            .split(PHOTO_VALUE_SEPARATOR)
            .map(::normalizeStoredPhotoRef)
            .filter { it.isNotBlank() }
    }

    private fun normalizeStoredPhotoRef(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return ""
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> trimmed
            trimmed.startsWith("content://") -> trimmed
            trimmed.startsWith("file://") -> trimmed
            trimmed.startsWith("/") -> "file://$trimmed"
            else -> trimmed
        }
    }

    private fun extractPhotoFileName(photoRef: String): String {
        return normalizeStoredPhotoRef(photoRef)
            .substringBefore("?")
            .substringAfterLast("/")
            .replace("%20", " ")
    }

    private fun findStoredPhotoFile(
        photoRef: String,
        fieldName: String?,
        traitName: String?,
    ): DocumentFile? {
        val normalizedPhotoRef = normalizeStoredPhotoRef(photoRef)
        val fileName = extractPhotoFileName(normalizedPhotoRef)
        if (fileName.isBlank()) return null

        val candidateDirectories = listOfNotNull(
            DocumentTreeUtil.getFieldMediaDirectory(fieldName, traitName),
            DocumentTreeUtil.getFieldMediaDirectory(fieldName, PHOTO_DIRECTORY_NAME),
            DocumentTreeUtil.getPlotDataDirectory(),
        ).distinctBy { it.uri() }

        candidateDirectories.forEach { directory ->
            directory.findFile(fileName)
                ?.takeIf { it.exists() }
                ?.let { return it }

            listFiles(directory)
                .firstOrNull { storedFile ->
                    storedFile.exists() && (
                        storedFile.uri() == normalizedPhotoRef ||
                            storedFile.name() == fileName
                        )
                    }
                ?.let { return it }
        }

        return null
    }

    private fun String.toImageMimeType(): String {
        return when (substringAfterLast('.', "").lowercase()) {
            "jpg",
            "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "svg" -> "image/svg+xml"
            else -> BrapiImageExport.DEFAULT_MIME_TYPE
        }
    }

    private fun BrapiExportObservationRow.toCategoryValueMap(): Map<String, String?> {
        return mapOf(
            "id" to id.toString(),
            "value" to value,
            "observation_time_stamp" to observationTimeStamp,
            "observation_unit_id" to observationUnitDbId,
            "observation_db_id" to observationDbId,
            "last_synced_time" to lastSyncedTime,
            "collector" to collector,
            "study_db_id" to studyDbId,
            "external_db_id" to externalTraitDbId,
            "observation_variable_name" to observationVariableName,
            "observation_variable_field_book_format" to observationVariableFieldBookFormat,
        )
    }

    private companion object {
        const val PHOTO_VALUE_SEPARATOR = "\n"
        const val PHOTO_DIRECTORY_NAME = "photo"
    }
}
