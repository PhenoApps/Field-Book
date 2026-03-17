package com.fieldbook.tracker.devices.spectrometers.innospectra.interfaces

import com.ISCSDK.ISCNIRScanSDK
import com.fieldbook.tracker.devices.spectrometers.innospectra.models.DeviceStatus
import com.fieldbook.tracker.devices.spectrometers.innospectra.models.Frame
import com.fieldbook.tracker.devices.spectrometers.innospectra.receivers.DeviceInfoReceiver

/**
 * Interface for receiving Inno Spectra Nano device events.
 */
interface NanoEventListener {

    fun onNotifyReceived() = Unit

    fun onRefDataReady() = Unit

    fun onScanDataReady(spectral: Frame) = Unit

    fun onScanStarted() = Unit

    fun onGetDeviceInfo(info: DeviceInfoReceiver.NanoDeviceInfo) = Unit

    fun onGetConfig(config: ISCNIRScanSDK.ScanConfiguration) = Unit

    fun onGetActiveConfig(index: Int) = Unit

    fun onConfigSaveStatus(status: Boolean) = Unit

    fun onGetConfigSize(size: Int) = Unit

    fun onGetUuid(uuid: String) = Unit

    fun onGetDeviceStatus(status: DeviceStatus) = Unit
}