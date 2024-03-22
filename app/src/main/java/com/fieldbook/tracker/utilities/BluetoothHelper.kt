package com.fieldbook.tracker.utilities

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.PatternMatcher
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import org.phenoapps.fragments.gopro.GoProHelper
import javax.inject.Inject

class BluetoothHelper @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        const val TAG = "BluetoothHelper"
    }

    interface BluetoothRequester {
        fun onStart()
        fun onStop()
        fun onPause()
        fun onDestroy()
    }

    var goProDevices: ArrayList<BluetoothDevice> = ArrayList()

    // Create a BroadcastReceiver for ACTION_FOUND. bluetooth devices
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                BluetoothDevice.ACTION_FOUND -> {
                    // Discovery has found a device. Get the BluetoothDevice
                    // object and its info from the Intent.
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                        ?.let { device ->

                            if (device.name != null) {

                                Log.d(GoProHelper.TAG, device.name)

                                if (GoProHelper.GoProDeviceIdentifier in device.name) goProDevices.add(device)
                            }
                        }
                }
            }
        }
    }

    private fun unregister(receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

//    override fun isBluetoothEnabled(adapter: BluetoothAdapter): Boolean {
//        return adapter.isEnabled
//    }

    private fun registerReceivers() {
        context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
    }

    fun onStart() {
        registerReceivers()
    }

    fun onPause() {

    }

    fun onStop() {

    }

    fun onResume() {

    }

    fun onDestroy() {
        unregister(receiver)
    }
}
