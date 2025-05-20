package com.fieldbook.tracker.utilities.export

import android.util.Log
import com.fieldbook.tracker.database.repository.SpectralRepository
import com.fieldbook.tracker.utilities.export.ObservationValueProcessor.ProcessException
import javax.inject.Inject

class SpectralFileProcessor @Inject constructor(private val spectralRepository: SpectralRepository): ObservationValueProcessor {

    companion object {
        const val COLOR_DATA_SIZE = 4
    }

    override fun processValue(value: String?): Result<String> = runCatching {

        if (value == null) throw ProcessException.MissingValue
        if (value.isEmpty() || value == "null") throw ProcessException.InvalidValue

        val id = value.toIntOrNull()

        if (id != null) {
            val fact = spectralRepository.getSpectralFactById(id) ?: throw ProcessException.MissingValue
            val uri = spectralRepository.getUri(fact.uriId) ?: throw ProcessException.MissingValue
            return Result.success(if (fact.data.size == COLOR_DATA_SIZE) fact.color else uri.toString())
        } else throw ProcessException.InvalidValue
    }
}