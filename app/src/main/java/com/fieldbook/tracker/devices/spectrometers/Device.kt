package com.fieldbook.tracker.devices.spectrometers

//simple class to represent any device type for persisting connections and interfacing
class Device(val deviceImplementation: Any) {
    var displayableName: String? = null
}