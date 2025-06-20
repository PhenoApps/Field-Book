package com.fieldbook.tracker.traits

import android.content.Context
import android.util.AttributeSet
import com.fieldbook.tracker.devices.spectrometers.Device
import com.fieldbook.tracker.devices.spectrometers.SpectralFrame
import com.fieldbook.tracker.devices.spectrometers.Spectrometer.ResultCallback

/**
 * TODO
 * Temporary class that shows the inheritance of the base spectral trait
 * Later this will be filled with inno spectra specific code
 */
class InnoSpectraTraitLayout : SpectralTraitLayout {

    companion object {
        const val TAG = "InnoSpectraLayout"
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context, attrs, defStyleAttr
    )

    override fun establishConnection(): Boolean {
        TODO("Not yet implemented")
    }

    override fun startDeviceSearch() {
        TODO("Not yet implemented")
    }

    override fun getDeviceList(): List<Device> {
        TODO("Not yet implemented")
    }

    override fun connectDevice(device: Device) {
        TODO("Not yet implemented")
    }

    override fun disconnectAndEraseDevice(device: Device) {
        TODO("Not yet implemented")
    }

    override fun saveDevice(device: Device) {
        TODO("Not yet implemented")
    }

    override fun capture(device: Device, entryId: String, traitId: String, callback: ResultCallback) {
        TODO("Not yet implemented")
    }

    override fun writeSpectralDataToDatabase(
        frame: SpectralFrame,
        color: String,
        uri: String,
        entryId: String,
        traitId: String
    ) {
        TODO("Not yet implemented")
    }
}