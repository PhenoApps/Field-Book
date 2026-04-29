package com.fieldbook.tracker.devices.spectrometers

//device-specific functions that the child UI class must implement
interface Spectrometer {

    fun isSpectralCompatible(device: Device): Boolean

    //used when a device is not connected yet, uses preferences or other mechanism to recall
    //and establish a connection, may return False if no device is found or setup
    fun establishConnection(): Boolean

    fun startDeviceSearch()

    fun getDeviceList(): List<Device>

    fun connectDevice(device: Device)

    fun disconnectAndEraseDevice(device: Device)

    fun saveDevice(device: Device)

    fun capture(device: Device, entryId: String, traitId: String, callback: ResultCallback)

    //each device must implement a database saver that takes some internal model and converts it
    //to field book's SpectralFact model for database representation
    //each write requires linkage to an obsUnitId (entryId), and traitId
    fun writeSpectralDataToDatabase(
        frame: SpectralFrame,
        color: String,
        uri: String,
        entryId: String,
        traitId: String
    )

    fun interface ResultCallback {
        fun onResult(result: Boolean)
    }
}