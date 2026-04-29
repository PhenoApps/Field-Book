package com.fieldbook.tracker.database.dao.spectral

import android.content.ContentValues
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.Migrator
import com.fieldbook.tracker.database.SpectralFactTable
import com.fieldbook.tracker.database.models.spectral.SpectralFact
import javax.inject.Inject

class SpectralDao @Inject constructor(private val helper: DataHelper) {

//Potential Details query
//    fun getDetails(
//        studyId: String,
//    ): SpectralFact? {
//        helper.open()
//        val cursor = helper.db.rawQuery(
//            """SELECT color, data, created_at, observation_unit_id, study_id, V.observation_variable_name, name, address, uri
//               FROM facts_spectral
//               JOIN observations as O on internal_id_observation=observation_id
//               JOIN observation_variables as V on internal_id_observation_variable=O.observation_variable_db_id
//               JOIN spectral_dim_uri as DU on DU.id=uri_id
//               JOIN spectral_dim_device as DD on DD.id=device_id
//               JOIN spectral_dim_protocol as DP on DP.id=protocol_id
//               WHERE study_id = ?""",
//            arrayOf(studyId)
//        )
//
//        if (cursor.moveToFirst()) {
//            val id = cursor.getColumnIndex(SpectralFactTable.ID)
//            val protocolId = cursor.getColumnIndex(SpectralFactTable.PROTOCOL_ID)
//            val deviceId = cursor.getColumnIndex(SpectralFactTable.DEVICE_ID)
//            val uriId = cursor.getColumnIndex(SpectralFactTable.URI_ID)
//            val observationId = cursor.getColumnIndex(SpectralFactTable.OBSERVATION_ID)
//            val data = cursor.getColumnIndex(SpectralFactTable.DATA)
//            val color = cursor.getColumnIndex(SpectralFactTable.COLOR)
//            val comment = cursor.getColumnIndex(SpectralFactTable.COMMENT)
//            val createdAt = cursor.getColumnIndex(SpectralFactTable.CREATED_AT)
//
//            if (id >= 0 && protocolId >= 0 && deviceId >= 0 && uriId >= 0 && data >= 0 && comment >= 0 && createdAt >= 0 && observationId >= 0 && color >= 0) {
//                return SpectralFact(
//                    id = cursor.getInt(id),
//                    protocolId = cursor.getInt(protocolId),
//                    uriId = uriId,
//                    deviceId = cursor.getInt(deviceId),
//                    observationId = cursor.getInt(observationId),
//                    data = cursor.getBlob(data),
//                    color = cursor.getString(color),
//                    comment = cursor.getString(comment),
//                    createdAt = cursor.getString(createdAt)
//                )
//            }
//        }
//
//        cursor.close()
//
//        return null
//    }

    fun insertSpectralFact(fact: SpectralFact): Long {
        helper.open()
        val values = ContentValues().apply {
            put(SpectralFactTable.PROTOCOL_ID, fact.protocolId)
            put(SpectralFactTable.URI_ID, fact.uriId)
            put(SpectralFactTable.DEVICE_ID, fact.deviceId)
            put(SpectralFactTable.OBSERVATION_ID, fact.observationId)
            put(SpectralFactTable.DATA, fact.data)
            put(SpectralFactTable.COLOR, fact.color)
            put(SpectralFactTable.COMMENT, fact.comment)
            put(SpectralFactTable.CREATED_AT, fact.createdAt)
        }
        return helper.db.insert(SpectralFactTable.TABLE_NAME, null, values)
    }

    fun updateFact(fact: SpectralFact) {
        helper.open()
        helper.db.update(
            SpectralFactTable.TABLE_NAME, ContentValues().apply {
                put(SpectralFactTable.PROTOCOL_ID, fact.protocolId)
                put(SpectralFactTable.URI_ID, fact.uriId)
                put(SpectralFactTable.DEVICE_ID, fact.deviceId)
                put(SpectralFactTable.OBSERVATION_ID, fact.observationId)
                put(SpectralFactTable.DATA, fact.data)
                put(SpectralFactTable.COLOR, fact.color)
                put(SpectralFactTable.COMMENT, fact.comment)
                put(SpectralFactTable.CREATED_AT, fact.createdAt)
            }, "${SpectralFactTable.ID} = ?", arrayOf(fact.id.toString())
        )
    }

    fun getSpectralFacts(observationIds: List<Int>): List<SpectralFact> {
        helper.open()
        val cursor = helper.db.rawQuery(
            "SELECT * FROM ${SpectralFactTable.TABLE_NAME} WHERE ${SpectralFactTable.OBSERVATION_ID} IN ${observationIds.joinToString(", ", "(", ")")}",
            null
        )

        val spectralFacts = mutableListOf<SpectralFact>()

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getColumnIndex(SpectralFactTable.ID)
                val protocolId = cursor.getColumnIndex(SpectralFactTable.PROTOCOL_ID)
                val deviceId = cursor.getColumnIndex(SpectralFactTable.DEVICE_ID)
                val uriId = cursor.getColumnIndex(SpectralFactTable.URI_ID)
                val observationId = cursor.getColumnIndex(SpectralFactTable.OBSERVATION_ID)
                val data = cursor.getColumnIndex(SpectralFactTable.DATA)
                val color = cursor.getColumnIndex(SpectralFactTable.COLOR)
                val comment = cursor.getColumnIndex(SpectralFactTable.COMMENT)
                val createdAt = cursor.getColumnIndex(SpectralFactTable.CREATED_AT)

                if (id >= 0 && protocolId >= 0 && deviceId >= 0 && uriId >= 0 && data >= 0 && comment >= 0 && createdAt >= 0 && observationId >= 0 && color >= 0) {
                    spectralFacts.add(
                        SpectralFact(
                            id = cursor.getInt(id),
                            protocolId = cursor.getInt(protocolId),
                            uriId = cursor.getInt(uriId),
                            deviceId = cursor.getInt(deviceId),
                            observationId = cursor.getInt(observationId),
                            data = cursor.getBlob(data),
                            color = cursor.getString(color),
                            comment = cursor.getString(comment),
                            createdAt = cursor.getString(createdAt)
                        )
                    )
                }
            } while (cursor.moveToNext())
        }

        cursor.close()

        return spectralFacts
    }

    fun getSpectralFactById(id: Int): SpectralFact? {
        helper.open()
        val cursor = helper.db.rawQuery(
            "SELECT * FROM ${SpectralFactTable.TABLE_NAME} WHERE ${SpectralFactTable.ID} = ?",
            arrayOf(id.toString())
        )
        var spectralFact: SpectralFact? = null
        if (cursor.moveToFirst()) {
            val protocolId = cursor.getColumnIndex(SpectralFactTable.PROTOCOL_ID)
            val deviceId = cursor.getColumnIndex(SpectralFactTable.DEVICE_ID)
            val uriId = cursor.getColumnIndex(SpectralFactTable.URI_ID)
            val observationId = cursor.getColumnIndex(SpectralFactTable.OBSERVATION_ID)
            val data = cursor.getColumnIndex(SpectralFactTable.DATA)
            val color = cursor.getColumnIndex(SpectralFactTable.COLOR)
            val comment = cursor.getColumnIndex(SpectralFactTable.COMMENT)
            val createdAt = cursor.getColumnIndex(SpectralFactTable.CREATED_AT)

            if (protocolId >= 0 && deviceId >= 0 && uriId >= 0 && data >= 0 && comment >= 0 && createdAt >= 0 && observationId >= 0 && color >= 0) {
                spectralFact = SpectralFact(
                    id = id,
                    protocolId = cursor.getInt(protocolId),
                    uriId = cursor.getInt(uriId),
                    deviceId = cursor.getInt(deviceId),
                    observationId = cursor.getInt(observationId),
                    data = cursor.getBlob(data),
                    color = cursor.getString(color),
                    comment = cursor.getString(comment),
                    createdAt = cursor.getString(createdAt)
                )
            }
        }
        cursor.close()
        return spectralFact
    }

    fun getSpectralFacts(studyId: Int): List<SpectralFact> {

        helper.open()
        val cursor = helper.db.rawQuery(
            "SELECT * FROM ${Migrator.Observation.tableName} WHERE study_id = ?",
            arrayOf(studyId.toString())
        )

        val spectralFacts = mutableListOf<SpectralFact>()

        if (cursor.moveToFirst()) {
            do {
                val id = cursor.getColumnIndex(SpectralFactTable.ID)
                val protocolId = cursor.getColumnIndex(SpectralFactTable.PROTOCOL_ID)
                val deviceId = cursor.getColumnIndex(SpectralFactTable.DEVICE_ID)
                val uriId = cursor.getColumnIndex(SpectralFactTable.URI_ID)
                val observationId = cursor.getColumnIndex(SpectralFactTable.OBSERVATION_ID)
                val data = cursor.getColumnIndex(SpectralFactTable.DATA)
                val color = cursor.getColumnIndex(SpectralFactTable.COLOR)
                val comment = cursor.getColumnIndex(SpectralFactTable.COMMENT)
                val createdAt = cursor.getColumnIndex(SpectralFactTable.CREATED_AT)

                if (id >= 0 && protocolId >= 0 && deviceId >= 0 && uriId >= 0 && data >= 0 && comment >= 0 && createdAt >= 0 && observationId >= 0 && color >= 0) {
                    spectralFacts.add(
                        SpectralFact(
                            id = cursor.getInt(id),
                            protocolId = cursor.getInt(protocolId),
                            uriId = cursor.getInt(uriId),
                            deviceId = cursor.getInt(deviceId),
                            observationId = cursor.getInt(observationId),
                            data = cursor.getBlob(data),
                            color = cursor.getString(color),
                            comment = cursor.getString(comment),
                            createdAt = cursor.getString(createdAt)
                        )
                    )
                }
            } while (cursor.moveToNext())
        }

        cursor.close()

        return spectralFacts
    }

    fun deleteSpectralFact(fact: SpectralFact) {
        helper.open()
        val whereClause = "${SpectralFactTable.ID} = ?"
        val whereArgs = arrayOf(fact.id.toString())
        helper.db.delete(SpectralFactTable.TABLE_NAME, whereClause, whereArgs)
    }

    fun deleteSpectralFactById(factId: String) {
        helper.open()
        val whereClause = "${SpectralFactTable.ID} = ?"
        val whereArgs = arrayOf(factId)
        helper.db.delete(SpectralFactTable.TABLE_NAME, whereClause, whereArgs)
    }

    fun deleteSpectralObservation(studyId: String, plotId: String, traitId: String, value: String) {
        helper.open()
        helper.deleteTraitByValue(studyId, plotId, traitId, value)
    }
}