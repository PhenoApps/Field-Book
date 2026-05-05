package com.fieldbook.shared.screens.fields

import com.fieldbook.shared.brapi.BrAPIService
import com.fieldbook.shared.brapi.BrapiResult
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationImport
import com.fieldbook.shared.database.repository.ExistingBrapiObservation
import com.fieldbook.shared.database.repository.ObservationRepository
import com.fieldbook.shared.database.repository.StudyRepository
import com.fieldbook.shared.database.repository.TraitRepository
import com.fieldbook.shared.utilities.currentLocalInternalTimestamp

data class BrapiObservationSyncPreview(
    val traitCount: Int,
    val observationCount: Int,
)

data class BrapiObservationSyncResult(
    val savedObservations: Int,
    val skippedObservations: Int,
)

class BrapiObservationSyncSupport(
    private val studyRepository: StudyRepository = StudyRepository(),
    private val traitRepository: TraitRepository = TraitRepository(),
    private val observationRepository: ObservationRepository = ObservationRepository(),
) {
    suspend fun loadPreview(
        fieldId: Int,
        service: BrAPIService,
        pageSize: Int,
    ): BrapiResult<BrapiObservationSyncPreview> {
        val field = studyRepository.getById(fieldId)
        val studyDbId = field.study_db_id.takeIf { it.isNotBlank() }
            ?: return BrapiResult.Failure(message = "Field is missing BrAPI study id")
        val traitDbIds = getExistingBrapiTraitDbIds()

        return when (val result = service.getStudyObservations(studyDbId, traitDbIds, pageSize)) {
            is BrapiResult.Failure -> result
            is BrapiResult.Success -> BrapiResult.Success(
                BrapiObservationSyncPreview(
                    traitCount = traitDbIds.size,
                    observationCount = result.value.count { it.canImport }
                )
            )
        }
    }

    suspend fun sync(
        fieldId: Int,
        service: BrAPIService,
        pageSize: Int,
    ): BrapiResult<BrapiObservationSyncResult> {
        val field = studyRepository.getById(fieldId)
        val studyDbId = field.study_db_id.takeIf { it.isNotBlank() }
            ?: return BrapiResult.Failure(message = "Field is missing BrAPI study id")
        val traitByExternalId = traitRepository.getAllTraits()
            .mapNotNull { trait ->
                val traitId = trait.id ?: return@mapNotNull null
                val externalDbId = trait.externalDbId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                externalDbId to traitId
            }
            .toMap()

        return when (val result = service.getStudyObservations(studyDbId, traitByExternalId.keys.toList(), pageSize)) {
            is BrapiResult.Failure -> result
            is BrapiResult.Success -> {
                val syncResult = saveObservations(fieldId, result.value, traitByExternalId)
                BrapiResult.Success(syncResult)
            }
        }
    }

    private fun getExistingBrapiTraitDbIds(): List<String> {
        return traitRepository.getAllTraits()
            .mapNotNull { it.externalDbId?.takeIf(String::isNotBlank) }
            .distinct()
    }

    private fun saveObservations(
        fieldId: Int,
        observations: List<BrapiObservationImport>,
        traitByExternalId: Map<String, Long>,
    ): BrapiObservationSyncResult {
        val existingObservations = getExistingObservations(fieldId)
        val existingDbIds = existingObservations.mapNotNull { it.observationDbId }.toSet()
        val repBase = existingObservations
            .groupingBy { it.observationUnitDbId to it.externalTraitDbId }
            .eachCount()
            .toMutableMap()
        val syncTimestamp = currentLocalInternalTimestamp()
        var saved = 0
        var skipped = 0

        observations
            .filter { it.canImport }
            .sortedBy { it.observationTimeStamp.orEmpty() }
            .forEach { observation ->
                val observationUnitDbId = observation.observationUnitDbId ?: return@forEach
                val externalTraitDbId = observation.observationVariableDbId ?: return@forEach
                val traitId = traitByExternalId[externalTraitDbId] ?: return@forEach
                val observationDbId = observation.observationDbId
                val normalizedTimestamp = observation.observationTimeStamp.normalizeBrapiTimestamp()

                if (observationDbId != null && observationDbId in existingDbIds) {
                    val existing = existingObservations.firstOrNull { it.observationDbId == observationDbId }
                    if (existing?.timestamp.isSameOrAfter(normalizedTimestamp) == true) {
                        skipped++
                        return@forEach
                    }
                }

                val repKey = observationUnitDbId to externalTraitDbId
                val nextRep = (repBase[repKey] ?: 0) + 1
                repBase[repKey] = nextRep

                if (hasObservation(fieldId, observationUnitDbId, traitId, nextRep.toString())) {
                    skipped++
                    return@forEach
                }

                insertObservation(
                    fieldId = fieldId,
                    observation = observation,
                    traitId = traitId,
                    observationTimeStamp = normalizedTimestamp,
                    lastSyncedTime = syncTimestamp,
                    rep = nextRep.toString(),
                )
                saved++
            }

        studyRepository.updateSyncDate(fieldId, syncTimestamp)

        return BrapiObservationSyncResult(
            savedObservations = saved,
            skippedObservations = skipped,
        )
    }

    private fun insertObservation(
        fieldId: Int,
        observation: BrapiObservationImport,
        traitId: Long,
        observationTimeStamp: String?,
        lastSyncedTime: String,
        rep: String,
    ) {
        observationRepository.insertBrapiObservation(
            studyId = fieldId.toLong(),
            plotId = observation.observationUnitDbId.orEmpty(),
            traitDbId = traitId,
            value = observation.value,
            observationTimeStamp = observationTimeStamp,
            collector = observation.collector.orEmpty(),
            observationDbId = observation.observationDbId,
            lastSyncedTime = lastSyncedTime,
            rep = rep,
        )
    }

    private fun hasObservation(
        fieldId: Int,
        observationUnitDbId: String,
        traitId: Long,
        rep: String,
    ): Boolean {
        return observationRepository.hasObservationWithRep(
            studyId = fieldId.toLong(),
            plotId = observationUnitDbId,
            traitDbId = traitId,
            rep = rep,
        )
    }

    private fun getExistingObservations(fieldId: Int): List<ExistingBrapiObservation> {
        return observationRepository.getExistingBrapiObservations(fieldId.toLong())
    }

    private val BrapiObservationImport.canImport: Boolean
        get() = !observationUnitDbId.isNullOrBlank() &&
            !observationVariableDbId.isNullOrBlank() &&
            !value.isNullOrBlank()

    private fun String?.normalizeBrapiTimestamp(): String? {
        return this
            ?.takeIf { it.isNotBlank() }
            ?.replaceFirst('T', ' ')
    }

    private fun String?.isSameOrAfter(other: String?): Boolean {
        if (this.isNullOrBlank() || other.isNullOrBlank()) return false
        return this >= other
    }
}
