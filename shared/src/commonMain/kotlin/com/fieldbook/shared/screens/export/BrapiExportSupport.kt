package com.fieldbook.shared.screens.export

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.fieldbook.shared.AppContext
import com.fieldbook.shared.brapi.BrAPIService
import com.fieldbook.shared.brapi.BrapiResult
import com.fieldbook.shared.brapi.model.v2.phenotyping.BrapiObservationExport
import com.fieldbook.shared.database.models.FieldObject
import com.fieldbook.shared.database.repository.StudyRepository
import com.fieldbook.shared.database.utils.internalTimeFormatter
import com.fieldbook.shared.objects.ImportFormat
import com.fieldbook.shared.utilities.CategoryJsonUtil
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
}

data class BrapiFieldExportPreview(
    val fieldId: Int,
    val fieldName: String,
    val newObservations: Int,
    val syncedObservations: Int,
    val editedObservations: Int,
    val localObservations: Int,
    val wrongSourceObservations: Int,
)

data class BrapiExportResult(
    val created: Int,
    val updated: Int,
    val skippedSynced: Int,
)

class BrapiExportSupport(
    private val studyRepository: StudyRepository = StudyRepository(),
) {
    private val driver: SqlDriver
        get() = AppContext.driverFactory().getDriver()

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
            BrapiFieldExportPreview(
                fieldId = fieldId,
                fieldName = field.exp_alias.ifBlank { field.exp_name },
                newObservations = observations.count { it.status == BrapiObservationExport.Status.NEW },
                syncedObservations = observations.count { it.status == BrapiObservationExport.Status.SYNCED },
                editedObservations = observations.count { it.status == BrapiObservationExport.Status.EDITED || it.status == BrapiObservationExport.Status.INCOMPLETE },
                localObservations = countLocalObservations(fieldId),
                wrongSourceObservations = countWrongSourceObservations(hostUrl),
            )
        }

        return BrapiExportPreview(
            fields = previews,
            canExport = true,
            message = if (previews.sumOf { it.newObservations + it.editedObservations } == 0) {
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
        val newObservations = observations
            .filter { it.status == BrapiObservationExport.Status.NEW }
            .map { it.withBrapiTimestamp() }
        val editedObservations = observations
            .filter { it.status == BrapiObservationExport.Status.EDITED || it.status == BrapiObservationExport.Status.INCOMPLETE }
            .map { it.withBrapiTimestamp() }

        var created = 0
        var updated = 0

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

        return BrapiResult.Success(
            BrapiExportResult(
                created = created,
                updated = updated,
                skippedSynced = observations.count { it.status == BrapiObservationExport.Status.SYNCED },
            )
        )
    }

    private fun FieldObject.isBrapiFieldFrom(hostUrl: String): Boolean {
        return ImportFormat.fromString(import_format) == ImportFormat.BRAPI &&
            exp_source?.equals(hostUrl, ignoreCase = true) == true
    }

    private fun getBrapiObservations(fieldId: Int, hostUrl: String): List<BrapiObservationExport> {
        val sql = """
            SELECT
                obs.internal_id_observation AS id,
                obs.value AS value,
                obs.observation_time_stamp,
                obs.observation_unit_id,
                obs.observation_variable_db_id,
                obs.observation_db_id,
                obs.last_synced_time,
                obs.collector,
                obs.rep,
                study.study_db_id,
                vars.external_db_id,
                vars.observation_variable_name,
                vars.observation_variable_field_book_format
            FROM observations AS obs
            JOIN observation_variables AS vars ON obs.observation_variable_db_id = vars.internal_id_observation_variable
            JOIN studies AS study ON obs.study_id = study.internal_id_study
            WHERE obs.study_id = ?
              AND study.study_source IS NOT NULL
              AND obs.value <> ''
              AND vars.trait_data_source = ?
              AND vars.trait_data_source IS NOT NULL
              AND vars.observation_variable_field_book_format <> 'photo'
        """.trimIndent()

        return executeQueryRows(sql, 2, 13) {
            bindLong(0, fieldId.toLong())
            bindString(1, hostUrl)
        }.mapNotNull { values ->
            val row = listOf(
                "id",
                "value",
                "observation_time_stamp",
                "observation_unit_id",
                "observation_variable_db_id",
                "observation_db_id",
                "last_synced_time",
                "collector",
                "rep",
                "study_db_id",
                "external_db_id",
                "observation_variable_name",
                "observation_variable_field_book_format",
            ).zip(values).toMap()

            val value = CategoryJsonUtil.processValue(row)
            val variableDbId = row["external_db_id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val unitDbId = row["observation_unit_id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val studyDbId = row["study_db_id"]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null

            BrapiObservationExport(
                fieldBookDbId = row["id"].orEmpty(),
                observationDbId = row["observation_db_id"],
                observationUnitDbId = unitDbId,
                observationVariableDbId = variableDbId,
                observationVariableName = row["observation_variable_name"],
                studyDbId = studyDbId,
                value = value.orEmpty(),
                observationTimeStamp = row["observation_time_stamp"],
                lastSyncedTime = row["last_synced_time"],
                collector = row["collector"],
            )
        }
    }

    private fun countLocalObservations(fieldId: Int): Int {
        return countRows(
            """
                SELECT COUNT(*)
                FROM observations AS obs
                JOIN observation_variables AS vars ON obs.observation_variable_db_id = vars.internal_id_observation_variable
                WHERE obs.study_id = ?
                  AND vars.observation_variable_field_book_format <> 'photo'
                  AND (vars.trait_data_source = 'local' OR vars.trait_data_source IS NULL)
            """.trimIndent(),
            fieldId,
        )
    }

    private fun countWrongSourceObservations(hostUrl: String): Int {
        val result = driver.executeQuery(
            identifier = null,
            sql = """
                SELECT COUNT(*)
                FROM observations AS obs
                JOIN observation_variables AS vars ON obs.observation_variable_db_id = vars.internal_id_observation_variable
                WHERE vars.observation_variable_field_book_format <> 'photo'
                  AND vars.trait_data_source <> ?
                  AND vars.trait_data_source <> 'local'
                  AND vars.trait_data_source IS NOT NULL
            """.trimIndent(),
            mapper = { cursor ->
                cursor.next().value
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            },
            parameters = 1,
            binders = { bindString(0, hostUrl) }
        )
        return result.value.toInt()
    }

    private fun countRows(sql: String, fieldId: Int): Int {
        val result = driver.executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                cursor.next().value
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            },
            parameters = 1,
            binders = { bindLong(0, fieldId.toLong()) }
        )
        return result.value.toInt()
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

            driver.execute(
                identifier = null,
                sql = "UPDATE observations SET observation_db_id = ?, last_synced_time = ? WHERE internal_id_observation = ?",
                parameters = 3,
            ) {
                bindString(0, observationDbId)
                bindString(1, now)
                bindLong(2, original.fieldBookDbId.toLong())
            }
        }
    }

    private fun BrapiObservationExport.withBrapiTimestamp(): BrapiObservationExport {
        return copy(observationTimeStamp = observationTimeStamp?.replaceFirst(' ', 'T'))
    }

    private fun executeQueryRows(
        sql: String,
        parameterCount: Int,
        columnCount: Int,
        binder: app.cash.sqldelight.db.SqlPreparedStatement.() -> Unit = {},
    ): List<List<String?>> {
        val result: QueryResult<List<List<String?>>> = driver.executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                val rows = mutableListOf<List<String?>>()
                while (cursor.next().value) {
                    rows += List(columnCount) { index -> cursor.getString(index) }
                }
                QueryResult.Value(rows)
            },
            parameters = parameterCount,
            binders = binder
        )
        return result.value
    }
}
