package com.fieldbook.tracker.database.models.spectral

import com.fieldbook.tracker.devices.spectrometers.SpectralFrame
import org.apache.xmlbeans.impl.util.Base64

/**
 * The database model for storing Spectral Fact samples.
 */
data class SpectralFact(
    var id: Int,
    val protocolId: Int,
    val uriId: Int,
    val deviceId: Int,
    val observationId: Int,
    val data: ByteArray,
    val color: String,
    var comment: String? = null,
    val createdAt: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as SpectralFact

        if (id != other.id) return false
        if (protocolId != other.protocolId) return false
        if (uriId != other.uriId) return false
        if (deviceId != other.deviceId) return false
        if (observationId != other.observationId) return false
        if (!data.contentEquals(other.data)) return false
        if (color != other.color) return false
        if (comment != other.comment) return false
        if (createdAt != other.createdAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + protocolId
        result = 31 * result + uriId
        result = 31 * result + deviceId
        result = 31 * result + observationId
        result = 31 * result + data.contentHashCode()
        result = 31 * result + color.hashCode()
        result = 31 * result + (comment?.hashCode() ?: 0)
        result = 31 * result + createdAt.hashCode()
        return result
    }

    /**
     * Helper function to generate an intermediate spectral frame from the model.
     */
    fun toSpectralFrame(entryId: String, traitId: String): SpectralFrame {
        val data = Base64.decode(this.data)
        val decodedData = String(data)
        val values = decodedData.split(";")[0]
        val wavelengths = decodedData.split(";")[1]
        return SpectralFrame(
            values = values,
            wavelengths = wavelengths,
            color = color,
            timestamp = createdAt,
            entryId = entryId,
            traitId = traitId
        )
    }
}


