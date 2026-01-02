package com.fieldbook.shared.database.repository

import com.fieldbook.shared.database.models.ObservationObject
import com.fieldbook.shared.database.utils.internalTimeFormatter
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.sqldelight.createDatabase
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats

class ObservationRepository() {
    private val db: FieldbookDatabase = createDatabase()

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
            observation_variable_name = trait.observation_variable_name,
            observation_variable_field_book_format = traitFormat
                ?: trait.observation_variable_field_book_format,
            value_ = value,
            observation_time_stamp = timestamp,
            last_synced_time = lastSyncedTime?.format(internalTimeFormatter)
                ?: observation?.last_synced_time,
            collector = person ?: observation?.collector,
            geoCoordinates = location ?: observation?.geoCoordinates,
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
            observation_variable_name = trait.observation_variable_name,
            observation_variable_field_book_format = traitFormat
                ?: trait.observation_variable_field_book_format,
            value_ = value,
            observation_time_stamp = timestamp,
            last_synced_time = lastSyncedTime?.format(internalTimeFormatter),
            collector = person,
            geoCoordinates = location,
            rep = rep,
            notes = notes,
        )
    }

    fun getObservation(studyId: Long, plotId: String, traitId: Long): ObservationObject? {
        val row = db.observationsQueries.getObservation(
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
