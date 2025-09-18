package com.fieldbook.tracker.utilities.export

import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.repository.SpectralRepository
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.utilities.export.ObservationValueProcessor.ProcessException
import org.apache.xmlbeans.impl.util.Base64
import javax.inject.Inject

class SpectralFileProcessor @Inject constructor(private val database: DataHelper, private val spectralRepository: SpectralRepository): ObservationValueProcessor {

    companion object {
        const val COLOR_DATA_SIZE = 4
    }

    override fun processValue(value: String?): Result<String> = runCatching {

        if (value == null) throw ProcessException.MissingValue
        if (value.isEmpty() || value == "null") throw ProcessException.InvalidValue

        val id = value.toIntOrNull()

        if (id != null) {
            val fact = spectralRepository.getSpectralFactById(id) ?: throw ProcessException.MissingValue
            val obs = database.getObservationById(fact.observationId.toString()) ?: throw ProcessException.MissingValue
            val trait = database.getTraitById(obs.observation_variable_db_id.toString()) ?: throw ProcessException.MissingValue
            val uri = spectralRepository.getUri(fact.uriId) ?: throw ProcessException.MissingValue
            val data = Base64.decode(fact.data)
            val decodedData = String(data)
            val values = decodedData.split(";")[0]
            return Result.success(when {
                values.length == COLOR_DATA_SIZE -> fact.color
                trait.format == Formats.GREEN_SEEKER.getDatabaseName() -> fact.color
                else -> uri
            })
        } else throw ProcessException.InvalidValue
    }
}