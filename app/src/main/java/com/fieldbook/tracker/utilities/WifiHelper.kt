package com.fieldbook.tracker.utilities

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
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WifiHelper @Inject constructor(@ApplicationContext private val context: Context) {

    interface WifiRequester {
        fun getSsidName(): String
        fun onNetworkBound()
    }

    private var requester: WifiRequester? = null

    private val wifiManager by lazy {

        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    }

    fun disconnect() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            connectivityManager.bindProcessToNetwork(null)
        }
    }

    fun startWifiSearch(requester: WifiRequester) {

        this.requester = requester

        val intentFilter = IntentFilter()
        intentFilter.addAction(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        intentFilter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION)

        try {
            context.unregisterReceiver(wifiReceiver)
        } catch (_: Exception) { }

        context.registerReceiver(wifiReceiver, intentFilter)

        val success = wifiManager.startScan()
        if (!success) {
            // scan failure handling
            scanFailure()
        }
    }

    fun stopWifiSearch() {

        try {

            context.unregisterReceiver(wifiReceiver)

        } catch (_: Exception) {}
    }

    private fun scanSuccess() {

        val results = wifiManager.scanResults

        results.forEach { result ->

            val name = this.requester?.getSsidName() ?: ""

            if (name in result.SSID) {

                startWifiSpecifier(result.SSID?.toString(), result.BSSID?.toString())
            }
        }
    }

    private fun scanFailure() {

        val results = wifiManager.scanResults

    }

    private fun startWifiSpecifier(ssid: String?, bssid: String?) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            val specifier =
                WifiNetworkSpecifier.Builder()
                    .setSsid(ssid!!)
                    //adding BSSID will remove the need for the "connect" dialog
                    //.setBssid(MacAddress.fromString(bssid!!))
                    .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            val connectivityManager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

            val networkCallback = object : ConnectivityManager.NetworkCallback() {

                override fun onAvailable(network: Network) {
                    super.onAvailable(network)

                    val bound = connectivityManager.bindProcessToNetwork(network)

                    requester?.onNetworkBound()

                    stopWifiSearch()
                }
            }

            connectivityManager.requestNetwork(
                request,
                networkCallback,
                Handler(Looper.getMainLooper()),
                10000
            )
        }
    }

    private val wifiReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

                val success = intent.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false)

                if (success) {

                    scanSuccess()

                } else {

                    scanFailure()

                }
            }
        }
    }
}