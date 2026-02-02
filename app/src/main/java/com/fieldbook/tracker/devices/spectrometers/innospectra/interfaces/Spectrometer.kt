package com.fieldbook.tracker.devices.spectrometers.innospectra.interfaces

import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.LiveData
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.devices.spectrometers.innospectra.models.DeviceInfo
import com.fieldbook.tracker.devices.spectrometers.innospectra.models.Frame

/**
 * Generic Spectrometer App interface to be extended by external apps using their
 * provided API.
 */
interface Spectrometer {

    fun manager(context: Context): SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

    suspend fun connect(context: Context)

    fun disconnect(context: Context): Int?

    fun isConnected(): Boolean

    fun reset(context: Context?) = Unit

    fun getDeviceError(): String?

    fun getDeviceInfo(): DeviceInfo

    fun setEventListener(onClick: () -> Unit): LiveData<String>

    fun scan(context: Context, manual: Boolean? = false): LiveData<List<Frame>?>
}