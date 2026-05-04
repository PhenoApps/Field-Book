package com.fieldbook.shared.screens.fields

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.fieldbook.shared.AppContext
import com.fieldbook.shared.brapi.BrAPIService
import com.fieldbook.shared.brapi.BrapiResult
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationImport
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
) {
    private val driver: SqlDriver
        get() = AppContext.driverFactory().getDriver()

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
                val nextRep = repBase.getOrDefault(repKey, 0) + 1
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
        driver.execute(
            identifier = null,
            sql = """
                INSERT INTO observations (
                    study_id,
                    observation_unit_id,
                    observation_variable_db_id,
                    value,
                    observation_time_stamp,
                    collector,
                    observation_db_id,
                    last_synced_time,
                    rep
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            parameters = 9,
        ) {
            bindLong(0, fieldId.toLong())
            bindString(1, observation.observationUnitDbId)
            bindLong(2, traitId)
            bindString(3, observation.value)
            bindString(4, observationTimeStamp)
            bindString(5, observation.collector.orEmpty())
            bindString(6, observation.observationDbId)
            bindString(7, lastSyncedTime)
            bindString(8, rep)
        }
    }

    private fun hasObservation(
        fieldId: Int,
        observationUnitDbId: String,
        traitId: Long,
        rep: String,
    ): Boolean {
        val result = driver.executeQuery(
            identifier = null,
            sql = """
                SELECT COUNT(*)
                FROM observations
                WHERE study_id = ?
                  AND observation_unit_id = ?
                  AND observation_variable_db_id = ?
                  AND rep = ?
            """.trimIndent(),
            mapper = { cursor ->
                cursor.next().value
                QueryResult.Value((cursor.getLong(0) ?: 0L) > 0L)
            },
            parameters = 4,
        ) {
            bindLong(0, fieldId.toLong())
            bindString(1, observationUnitDbId)
            bindLong(2, traitId)
            bindString(3, rep)
        }

        return result.value
    }

    private fun getExistingObservations(fieldId: Int): List<ExistingBrapiObservation> {
        val result = driver.executeQuery(
            identifier = null,
            sql = """
                SELECT obs.observation_db_id,
                       obs.observation_unit_id,
                       vars.external_db_id,
                       obs.observation_time_stamp
                FROM observations AS obs
                JOIN observation_variables AS vars
                  ON obs.observation_variable_db_id = vars.internal_id_observation_variable
                WHERE obs.study_id = ?
                  AND vars.external_db_id IS NOT NULL
            """.trimIndent(),
            mapper = { cursor ->
                val rows = mutableListOf<ExistingBrapiObservation>()
                while (cursor.next().value) {
                    rows += ExistingBrapiObservation(
                        observationDbId = cursor.getString(0),
                        observationUnitDbId = cursor.getString(1),
                        externalTraitDbId = cursor.getString(2),
                        timestamp = cursor.getString(3),
                    )
                }
                QueryResult.Value(rows)
            },
            parameters = 1,
        ) {
            bindLong(0, fieldId.toLong())
        }

        return result.value
    }

    private data class ExistingBrapiObservation(
        val observationDbId: String?,
        val observationUnitDbId: String?,
        val externalTraitDbId: String?,
        val timestamp: String?,
    )

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
