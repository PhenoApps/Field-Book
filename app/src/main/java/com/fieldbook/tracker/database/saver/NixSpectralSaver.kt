package com.fieldbook.tracker.database.saver

import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.database.models.spectral.Device
import com.fieldbook.tracker.database.models.spectral.Protocol
import com.fieldbook.tracker.database.models.spectral.SpectralFact
import com.fieldbook.tracker.database.saver.NixSpectralSaver.RequiredData
import com.fieldbook.tracker.database.viewmodels.SpectralViewModel
import com.fieldbook.tracker.devices.spectrometers.SpectralFrame
import com.fieldbook.tracker.traits.formats.Formats
import javax.inject.Inject

class NixSpectralSaver @Inject constructor(private val database: DataHelper) : DatabaseSaver<RequiredData, SpectralFact> {

    override fun saveData(requiredData: RequiredData): Result<SpectralFact> = runCatching {

        with (requiredData) {

            val deviceId = saveDevice(
                requiredData.viewModel,
                deviceName,
                deviceAddress
            )

            val uriId = saveUri(
                requiredData.viewModel,
                requiredData.uri
            )

            //default to color range 380-750
            val waveStart = if (frame.wavelengths.isNotEmpty()) frame.wavelengths.split(" ").minOfOrNull { it.toFloat() } ?: 380f else 380f
            val waveEnd = if (frame.wavelengths.isNotEmpty()) frame.wavelengths.split(" ").maxOfOrNull { it.toFloat() } ?: 750f else 750f

            val traitObjects = database.getAllTraitObjects().filter { it.id == traitId }

            if (traitObjects.isEmpty()) return Result.failure(NixDatabaseSaveException())

            val traitObject = traitObjects[0]

            val protocolId = saveProtocol(
                requiredData.viewModel,
                "${traitObject.name} ${traitObject.details}",
                waveStart,
                waveEnd
            )

            if (uriId > -1 && deviceId > -1 && protocolId > -1) {

                checkAndDeleteNa(studyId, entryId, traitId)

                val obsId = insertObservation(studyId, entryId, traitId.toString(), "-1", person, location)

                if (obsId > -1) {

                    val fact = insertSpectralFact(
                        viewModel = requiredData.viewModel,
                        protocolId = protocolId,
                        uriId = uriId,
                        deviceId = deviceId,
                        obsId = obsId,
                        encodedByteArray = frame.toByteArray(),
                        color = color,
                        comment = comment,
                        createdAt = createdAt
                    )

                    //update the observation with the spectral fact ID
                    database.updateObservationValue(obsId, fact.id.toString())

                    return Result.success(fact)
                }
            }
        }

        return Result.failure(NixDatabaseSaveException())
    }

    private fun saveDevice(
        viewModel: SpectralViewModel,
        deviceName: String,
        deviceAddress: String
    ): Int {

        var deviceId = viewModel.getDeviceId(deviceName, deviceAddress)

        if (deviceId < 0) {
            //insert device address, then get the generated ID
            deviceId = viewModel
                .insertDevice(Device(-1, deviceName, deviceAddress))
        }

        return deviceId
    }

    private fun saveUri(
        viewModel: SpectralViewModel,
        uri: String
    ): Int {

        var uriId = viewModel.getUriId(uri)

        if (uriId < 0) {
            //insert device address, then get the generated ID
            uriId = viewModel
                .insertUri(uri)
        }

        return uriId
    }

    private fun saveProtocol(
        viewModel: SpectralViewModel,
        protocolTitle: String,
        waveStart: Float,
        waveEnd: Float
    ): Int {

        var protocolId = viewModel.getProtocolId("FieldBook Protocol Title")
        //TODO get and check the wave start/wave end from the protocol

        if (protocolId < 0) {
            //insert protocol, then get the generated ID
            protocolId = viewModel.insertProtocol(
                Protocol(
                    id = -1,
                    externalId = "",
                    title = "FieldBook Protocol Title",
                    description = "FieldBook Protocol Description",
                    waveStart = waveStart,
                    waveEnd = waveEnd,
                    waveStep = 1f
                )
            )
        }

        return protocolId
    }

    private fun checkAndDeleteNa(studyId: String, entryId: String, traitId: String) {

        //check if NA exists, delete it
        val existingObservations = database.getAllObservations(
            studyId,
            entryId,
            traitId.toString()
        )

        if (existingObservations.isNotEmpty() && existingObservations[0].value == "NA") {

            database.deleteObservation(existingObservations[0].internal_id_observation.toString())

        }
    }

    private fun insertObservation(
        studyId: String,
        entryId: String,
        traitId: String,
        uriId: String,
        person: String,
        location: String
    ): Int {

        val rep = database.getNextRep(studyId, entryId, traitId.toString())

        val obsId = database.insertObservation(
            entryId,
            traitId.toString(),
            uriId.toString(),
            person,
            location,
            "",
            studyId,
            null,
            null,
            null,
            rep
        )

        return obsId.toInt()
    }

    private fun insertSpectralFact(
        viewModel: SpectralViewModel,
        protocolId: Int,
        uriId: Int,
        deviceId: Int,
        obsId: Int,
        encodedByteArray: ByteArray,
        color: String,
        comment: String?,
        createdAt: String
    ): SpectralFact {

        val fact = SpectralFact(
            id = -1,     //fake id, will be auto-generated
            protocolId = protocolId,
            uriId = uriId,
            deviceId = deviceId,
            observationId = obsId.toInt(),
            data = encodedByteArray,
            color = color,
            comment = comment,
            createdAt
        )

        //insert spectral fact
        val factId = viewModel.insertSpectralFact(fact)

        return fact.also {
            it.id = factId.toInt()
        }
    }

    class NixDatabaseSaveException: Exception("An error occurred while saving the Nix data")

    data class RequiredData(
        val viewModel: SpectralViewModel,
        val deviceAddress: String,
        val deviceName: String,
        val studyId: String,
        val person: String,
        val location: String,
        val comment: String?,
        val createdAt: String,
        val frame: SpectralFrame,
        val color: String,
        val uri: String,
        val entryId: String,
        val traitId: String
    )
}