package com.fieldbook.tracker.devices.spectrometers.innospectra.models

data class DeviceInfo(
    val softwareVersion: String,
    val hardwareVersion: String,
    val deviceId: String,
    val alias: String,
    val opMode: String,
    val deviceType: String,
    val serialNumber: String? = null,
    val humidity: String? = null,
    val temperature: String? = null
)