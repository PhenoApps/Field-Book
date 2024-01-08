package com.fieldbook.tracker.canon.models

data class DeviceInformation(
    val manufacturer: String,
    val productname: String,
    val guid: String,
    val serialnumber: String,
    val macaddress: String,
    val firmwareversion: String
)
