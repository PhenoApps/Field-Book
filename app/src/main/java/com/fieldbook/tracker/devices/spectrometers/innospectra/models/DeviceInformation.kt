package com.fieldbook.tracker.devices.spectrometers.innospectra.models

data class DeviceInformation(
    val manufacturer: String,
    val modelNum: String,
    val serialNum: String,
    val hardwareRev: String,
    val tivRev: String
)
