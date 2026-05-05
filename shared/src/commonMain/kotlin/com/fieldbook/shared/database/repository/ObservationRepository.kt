package com.fieldbook.shared.database.repository

import com.fieldbook.shared.database.models.ObservationObject
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.sqldelight.createDatabase
import com.fieldbook.shared.utilities.internalTimeFormatter
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats

data class ExistingBrapiObservation(
    val observationDbId: String?,
    val observationUnitDbId: String?,
    val externalTraitDbId: String?,
    val timestamp: String?,
)

class ObservationRepository() {
    private val db: FieldbookDatabase
        get() = createDatabase()

    /**
     * Returns a map of observation_variable_db_id to list of values for the given studyId and plotId.
     * Aggregates multiple values for the same traitId.
     * Note: using same name (getUserDetail) as native app for consistency.
     */
    fun getUserDetail(studyId: Long, plotId: String): Map<Long, List<String>> {
        return db.observationsQueries.getUserDetail(studyId, plotId)
            .executeAsList()
            .filter { it.value_ != null && it.observation_variable_db_id != null }
            .groupBy { it.observation_variable_db_id!! }
            .mapValues { entry -> entry.value.map { it.value_!! } }
    }

    fun getRep(studyId: Long, plotId: String, traitId: Long): Int {
        return db.observationsQueries.countObservations(
            study_id = studyId,
            observation_unit_id = plotId,
            observation_variable_db_id = traitId
        ).executeAsOne().toInt()
    }

    fun hasObservationWithRep(
        studyId: Long,
        plotId: String,
        traitDbId: Long,
        rep: String,
    ): Boolean {
        return db.observationsQueries.hasObservationWithRep(
            study_id = studyId,
            observation_unit_id = plotId,
            observation_variable_db_id = traitDbId,
            rep = rep
        ).executeAsOne() > 0L
    }

    fun deleteTraitByValue(
        studyId: Long,
        plotId: String,
        traitDbId: Long,
        value: String,
    ) {
        db.observationsQueries.deleteTraitByValue(
            study_id = studyId,
            observation_unit_id = plotId,
            observation_variable_db_id = traitDbId,
            value_ = value
        )
    }

    @OptIn(FormatStringsInDatetimeFormats::class)
    fun upsertObservation(
        studyId: Long,
        plotId: String,
        traitDbId: Long,
        value: String,
        traitFormat: String? = null,
        person: String? = null,
        location: String? = null,
        notes: String? = null,
        lastSyncedTime: Instant? = null,
        rep: String? = (getRep(studyId, plotId, traitDbId) + 1).toString(),
    ) {
        val trait = db.observation_variablesQueries.getTraitById(traitDbId).executeAsOneOrNull()
            ?: throw IllegalArgumentException("Trait with id $traitDbId not found")

        val observation = db.observationsQueries.getObservation(
            study_id = studyId,
            observation_unit_id = plotId,
            observation_variable_db_id = traitDbId
        ).executeAsOneOrNull()

        val timestamp = Clock.System.now().format(internalTimeFormatter)

        // TODO upsert? https://github.com/sqldelight/sqldelight/issues/1436
        // Always remove existing trait before inserting again
        // Based on plot_id, prevent duplicates
        db.observationsQueries.deleteTrait(
            study_id = studyId,
            observation_unit_id = plotId,
            observation_variable_db_id = traitDbId
        )
        db.observationsQueries.insertObservation(
            study_id = studyId,
            observation_unit_id = plotId,
            observation_variable_db_id = traitDbId,
            value_ = value,
            observation_time_stamp = timestamp,
            last_synced_time = lastSyncedTime?.format(internalTimeFormatter)
                ?: observation?.last_synced_time,
            collector = person ?: observation?.collector,
            geo_coordinates = location ?: observation?.geo_coordinates,
            rep = rep,
            notes = notes ?: observation?.notes,
        )
    }

    @OptIn(FormatStringsInDatetimeFormats::class)
    fun insertObservation(
        studyId: Long,
        plotId: String,
        traitDbId: Long,
        value: String,
        traitFormat: String? = null,
        person: String? = null,
        location: String? = null,
        notes: String? = null,
        lastSyncedTime: Instant? = null,
        rep: String? = (getRep(studyId, plotId, traitDbId) + 1).toString(),
    ) {
        val trait = db.observation_variablesQueries.getTraitById(traitDbId).executeAsOneOrNull()
            ?: throw IllegalArgumentException("Trait with id $traitDbId not found")

        val timestamp = Clock.System.now().format(internalTimeFormatter)

        db.observationsQueries.insertObservation(
            study_id = studyId,
            observation_unit_id = plotId,
            observation_variable_db_id = traitDbId,
            value_ = value,
            observation_time_stamp = timestamp,
            last_synced_time = lastSyncedTime?.format(internalTimeFormatter),
            collector = person,
            geo_coordinates = location,
            rep = rep,
            notes = notes,
        )
    }

    fun insertBrapiObservation(
        studyId: Long,
        plotId: String,
        traitDbId: Long,
        value: String?,
        observationTimeStamp: String?,
        collector: String,
        observationDbId: String?,
        lastSyncedTime: String,
        rep: String,
    ) {
        db.observationsQueries.insertBrapiObservation(
            study_id = studyId,
            observation_unit_id = plotId,
            observation_variable_db_id = traitDbId,
            value_ = value,
            observation_time_stamp = observationTimeStamp,
            collector = collector,
            observation_db_id = observationDbId,
            last_synced_time = lastSyncedTime,
            rep = rep,
        )
    }

    fun getExistingBrapiObservations(studyId: Long): List<ExistingBrapiObservation> {
        return db.observationsQueries.getExistingBrapiObservations(studyId)
            .executeAsList()
            .map { row ->
                ExistingBrapiObservation(
                    observationDbId = row.observation_db_id,
                    observationUnitDbId = row.observation_unit_id,
                    externalTraitDbId = row.external_db_id,
                    timestamp = row.observation_time_stamp,
                )
            }
    }

    fun getObservation(studyId: Long, plotId: String, traitId: Long): ObservationObject? {
        val row = db.observationsQueries.getObservationWithTrait(
            study_id = studyId,
            observation_unit_id = plotId,
            observation_variable_db_id = traitId
        ).executeAsOneOrNull() ?: return null
        return ObservationObject(
            id = row.internal_id_observation,
            studyId = row.study_id,
            observationVariableName = row.observation_variable_name,
            observationVariableDbId = row.observation_variable_db_id,
            observationUnitId = row.observation_unit_id,
            value = row.value_,
            lastSyncedTime = row.last_synced_time?.let {
                try {
                    Instant.parse(it)
                } catch (e: Exception) {
                    null
                }
            },
            rep = row.rep
        )
    }
}
