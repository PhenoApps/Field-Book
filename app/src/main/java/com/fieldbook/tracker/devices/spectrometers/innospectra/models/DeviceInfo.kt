package com.fieldbook.tracker.devices.spectrometers.innospectra.models

data class DeviceInfo(
    var softwareVersion: String = "",
    var hardwareVersion: String = "",
    var deviceId: String = "",
    var alias: String = "",
    var opMode: String = "",
    var deviceType: String = "",
    var serialNumber: String = "",
    var humidity: String = "",
    var temperature: String = "",
    var uuid: String = "",
)