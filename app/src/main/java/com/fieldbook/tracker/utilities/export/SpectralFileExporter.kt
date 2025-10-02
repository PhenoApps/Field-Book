package com.fieldbook.tracker.utilities.export

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.database.models.spectral.SpectralFact
import com.fieldbook.tracker.database.repository.SpectralRepository
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.utilities.StringUtil.escape
import dagger.hilt.android.qualifiers.ActivityContext
import java.io.IOException
import javax.inject.Inject

class SpectralFileExporter @Inject constructor(
    @ActivityContext private val context: Context,
    private val spectralRepository: SpectralRepository,
    private val databaseHelper: DataHelper
) {

    companion object {
        private const val TAG = "SpectralFileExporter"
        private const val SPECTRAL_DATA_FILE_NAME = "spectral_data.csv"
    }

    fun exportSpectralFile(
        studyId: Int
    ): Result<Unit> = runCatching {
        val observations = databaseHelper.getAllObservations(studyId.toString())
        val spectralTraits = databaseHelper.allTraitObjects?.filter { it.format in Formats.getSpectralFormats().map { it.getDatabaseName() } }
        val spectralTraitIds = (spectralTraits ?: emptyList()).map { it.id }

        val spectralFactObservations = observations.filter { observation ->
            observation.observation_variable_db_id.toString() in spectralTraitIds && observation.study_id == studyId.toString()
        }

        spectralFactObservations.groupBy { it.observation_variable_db_id }
            .forEach { (traitId, observationsForTrait) ->
                exportSpectralDataForTrait(spectralTraits, traitId, observationsForTrait)
            }
    }

    private fun exportSpectralDataForTrait(
        spectralTraits: List<TraitObject?>?,
        traitId: Int,
        observationsForTrait: List<ObservationModel>
    ) {
        val facts =
            spectralRepository.getSpectralFacts(observationsForTrait.map { it.internal_id_observation })

        val frames = facts.map { f ->
            val uri = spectralRepository.getUri(f.uriId)
                ?: throw IOException("URI not found for spectral fact: ${f.id}")
            uri.toUri() to f
        }.groupBy { pair ->
            pair.first
        }

        val deviceSpecificHeader = when (spectralTraits?.firstOrNull { it?.id == traitId.toString() }?.format) {
            Formats.GREEN_SEEKER.getDatabaseName() -> "average"
            else -> "color"
        }

        frames.keys.forEach { uri ->

            frames[uri]?.let { framesForUri ->

                if (framesForUri.isNotEmpty()) {

                    val frameList = framesForUri.map { it.second }

                    writeSpectralFile(deviceSpecificHeader, uri, frameList)

                }
            }
        }
    }

    private fun writeSpectralFile(
        deviceSpecificHeader: String,
        uri: Uri,
        spectralFacts: List<SpectralFact>,
    ) {
        if (spectralFacts.isEmpty()) {
            // Handle the case where there are no spectral facts to write, e.g., log a warning or create an empty file with only the header.
            // This prevents a crash due to accessing firstFact on an empty list.
            return
        }

        val metadataHeader = "sample_name,device_id,device_name,comments,created_at,$deviceSpecificHeader"

        val firstFact = spectralFacts.maxBy {
            it.data.size
        }

        val observation = databaseHelper.getObservationById(firstFact.observationId.toString())
            ?: throw IOException("Observation not found for spectral fact: ${firstFact.id}")
        val frameReference = firstFact.toSpectralFrame(
            entryId = observation.observation_unit_id,
            traitId = observation.observation_variable_db_id.toString()
        )

        val header = "$metadataHeader,${frameReference.wavelengths.replace(" ", ",")}\n"

        try {
            context.contentResolver.openOutputStream(uri, "wt")?.bufferedWriter()?.use { writer ->
                writer.write(header)

                for (fact in spectralFacts) {
                    val currentObservation =
                        databaseHelper.getObservationById(fact.observationId.toString())
                            ?: continue // Skip to the next fact if the observation is not found. Log an error if needed.
                    val device = spectralRepository.getDevice(fact.deviceId)

                    val entryId = currentObservation.observation_unit_id
                    val deviceAddress = device?.address ?: ""
                    val deviceName = device?.name ?: ""
                    val comment = fact.comment ?: ""
                    val createdAt = fact.createdAt
                    val color = fact.color

                    val frame = fact.toSpectralFrame(
                        entryId,
                        currentObservation.observation_variable_db_id.toString()
                    )
                    val values = frame.values.replace(" ", ",")

                    if (values.isEmpty())
                        continue

                    writer.write("\"${entryId.escape()}\",$deviceAddress,\"${deviceName.escape()}\",\"${comment.escape()}\",$createdAt,$color,$values\n")
                }
            }
        } catch (e: IOException) {
            throw IOException(
                "Failed to write to spectral file: $uri",
                e
            ) // Add the original exception as the cause
        }
    }
}