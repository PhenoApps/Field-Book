package com.fieldbook.tracker.devices.spectrometers

import org.apache.xmlbeans.impl.util.Base64

/**
 * SpectralFrame represents a single frame of spectral data.
 * An intermediate representation for FieldBook to process spectral data from different spectrometers.
 *
 * @property values The spectral values as a space-separated string.
 * @property wavelengths The corresponding wavelengths as a space-separated string.
 * @property timestamp The timestamp when the data was captured.
 * @property entryId The ID of the entry associated with this spectral frame.
 * @property traitId The ID of the trait associated with this spectral frame.
 */
data class SpectralFrame(
    var values: String = "",
    var wavelengths: String = "",
    var color: String,
    val timestamp: String,
    val entryId: String,
    val traitId: String
) {
    fun toByteArray(): ByteArray {
        return Base64.encode("$values;$wavelengths".toByteArray())
    }

    fun getWavelengthList(): List<Float> {
        return wavelengths.split(" ").map { it.toFloat() }
    }

    fun getValueList(): List<Float> {
        return values.split(" ").map { it.toFloat() }
    }

    companion object {

        fun placeholder(): SpectralFrame {
            return SpectralFrame(
                values = "",
                wavelengths = "",
                color = "#000000", // Default color
                timestamp = "",
                entryId = "",
                traitId = ""
            )
        }
    }
}
