package com.fieldbook.tracker.interfaces

import android.content.Context
import android.location.Location
import android.os.Handler
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.devices.camera.UsbCameraApi
import com.fieldbook.tracker.devices.camera.GoProApi
import com.fieldbook.tracker.devices.camera.CanonApi
import com.fieldbook.tracker.location.GPSTracker
import com.fieldbook.tracker.traits.SpectralController
import com.fieldbook.tracker.utilities.CameraXFacade
import com.fieldbook.tracker.utilities.BluetoothHelper
import com.fieldbook.tracker.utilities.FfmpegHelper
import com.fieldbook.tracker.utilities.GeoNavHelper
import com.fieldbook.tracker.utilities.GnssThreadHelper
import com.fieldbook.tracker.utilities.NixSensorHelper
import com.fieldbook.tracker.utilities.SensorHelper
import com.fieldbook.tracker.utilities.SoundHelperImpl
import com.fieldbook.tracker.utilities.VibrateUtil
import com.fieldbook.tracker.utilities.WifiHelper
import com.fieldbook.tracker.views.CollectInputView
import com.fieldbook.tracker.views.RangeBoxView
import com.fieldbook.tracker.views.RepeatedValuesView
import com.fieldbook.tracker.views.TraitBoxView
import com.serenegiant.widget.UVCCameraTextureView
import org.phenoapps.interfaces.security.SecureBluetooth
import org.phenoapps.security.SecureBluetoothActivityImpl

interface CollectController: FieldController, SpectralController, RepeatedValuesView.RepeatedValuesController {
    fun getContext(): Context
    fun getGps(): GPSTracker
    fun getLocation(): Location?
    fun getRangeBox(): RangeBoxView
    fun getTraitBox(): TraitBoxView
    fun getInputView(): CollectInputView
    fun getSoundHelper(): SoundHelperImpl
    fun getVibrator(): VibrateUtil
    fun resetGeoNavMessages()
    fun getGeoNavHelper(): GeoNavHelper
    fun getSecurityChecker(): SecureBluetoothActivityImpl
    fun getGnssThreadHelper(): GnssThreadHelper
    fun getAverageHandler(): Handler?
    fun moveToSearch(command: String,
                     plotIndices: IntArray?,
                     rangeId: String?,
                     plotId: String?,
                     data: String?,
                     traitIndex: Int): Boolean
    fun isFieldAudioRecording(): Boolean
    fun getUsbApi(): UsbCameraApi
    fun getUvcView(): UVCCameraTextureView
    fun getCameraXFacade(): CameraXFacade
    fun getWifiHelper(): WifiHelper
    fun getBluetoothHelper(): BluetoothHelper
    fun getGoProApi(): GoProApi
    fun advisor(): SecureBluetooth
    fun getFfmpegHelper(): FfmpegHelper
    fun getCanonApi(): CanonApi
    fun takePicture()
    fun getCurrentObservation(): ObservationModel?
    fun getRotationRelativeToDevice(): SensorHelper.RotationModel?
    fun getDeviceTilt(): SensorHelper.RotationModel?
    fun getNixSensorHelper(): NixSensorHelper
}