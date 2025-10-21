package com.fieldbook.tracker.utilities

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.PatternMatcher
import android.util.Log
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ActivityContext
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.Q)
class WifiHelper @Inject constructor(
    @ActivityContext private val context: Context,
) {

    interface WifiRequester {
        fun onApRequested()
        fun onNetworkBound(network: Network)
    }

    companion object {
        const val TAG = "WifiHelper"
    }

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    fun startWifiSearch(ssid: String, pass: String, requester: WifiRequester) {

        requester.onApRequested()

        val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(pass)
            .build()

        val networkRequest = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(wifiNetworkSpecifier)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                Log.d(TAG, "Network Available")

                connectivityManager.bindProcessToNetwork(network)

                requester.onNetworkBound(network)
            }
        }

        networkCallback?.let {
            connectivityManager.requestNetwork(networkRequest, it)
        }
    }

    fun startWifiSearch(format: String, requester: WifiRequester) {

        requester.onApRequested()

        val specifier =
            WifiNetworkSpecifier.Builder()
                .setSsidPattern(
                    PatternMatcher(
                        ".*$format.*",
                        PatternMatcher.PATTERN_SIMPLE_GLOB
                    )
                )
                //.setSsid(ssid!!)
                //adding BSSID will remove the need for the "connect" dialog
                //.setBssid(MacAddress.fromString(bssid!!))
                .build()

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                Log.d(TAG, "Network Available")

                connectivityManager.bindProcessToNetwork(network)

                requester.onNetworkBound(network)
            }
        }

        networkCallback?.let {
            connectivityManager.requestNetwork(request, it)
        }

    }

    fun disconnect() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
                connectivityManager.bindProcessToNetwork(null)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}