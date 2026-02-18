package com.fieldbook.tracker.devices.spectrometers.innospectra.models

data class Frame(
    var length: Int,
    var lightSource: Byte = 0,
    var frameNo: Int,
    var deviceType: Int,
    var data: FloatArray,
    var rawData: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Frame

        if (length != other.length) return false
        if (lightSource != other.lightSource) return false
        if (frameNo != other.frameNo) return false
        if (deviceType != other.deviceType) return false
        if (!data.contentEquals(other.data)) return false
        if (!rawData.contentEquals(other.rawData)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = length
        result = 31 * result + lightSource
        result = 31 * result + frameNo
        result = 31 * result + deviceType
        result = 31 * result + data.contentHashCode()
        result = 31 * result + rawData.contentHashCode()
        return result
    }
}