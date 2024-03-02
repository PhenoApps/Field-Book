package com.fieldbook.tracker.utilities

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.PatternMatcher
import androidx.annotation.RequiresApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

class WifiHelper @Inject constructor(@ApplicationContext private val context: Context) {

    companion object {
        const val TAG = "WifiHelper"
    }

    interface WifiRequester {
        fun getSsidName(): String
        fun onNetworkBound()
    }

    private var requester: WifiRequester? = null

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onAvailable(network: Network) {
            super.onAvailable(network)

            val bound = connectivityManager.bindProcessToNetwork(network)

            requester?.onNetworkBound()

        }

        @RequiresApi(Build.VERSION_CODES.M)
        override fun onLost(network: Network) {
            super.onLost(network)
            connectivityManager.bindProcessToNetwork(null)
        }
    }

    fun disconnect() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {

            connectivityManager.bindProcessToNetwork(null)

            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }

    fun startWifiSearch(format: String, requester: WifiRequester) {

        this.requester = requester

        val specifier =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
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
            } else {
                TODO("VERSION.SDK_INT < Q")
            }

        val request = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(specifier)
            .build()

        //connectivityManager.registerNetworkCallback(request, networkCallback)

        connectivityManager.requestNetwork(
            request,
            networkCallback
        )

    }
}