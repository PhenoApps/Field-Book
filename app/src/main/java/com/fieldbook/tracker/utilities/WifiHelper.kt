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

        /**
         * The bound network went away (camera powered off, moved out of range, AP timed out).
         * Default no-op so requesters that don't care are unaffected.
         */
        fun onNetworkLost() = Unit

        /**
         * The network request timed out or was rejected, so [onNetworkBound] will never arrive.
         */
        fun onNetworkUnavailable() = Unit
    }

    companion object {
        const val TAG = "WifiHelper"

        /**
         * Without a timeout the request stays pending forever if the user never accepts the
         * system connect dialog, which silently blocks every later request.
         */
        const val REQUEST_TIMEOUT_MS = 45000
    }

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var boundNetwork: Network? = null

    fun startWifiSearch(ssid: String, pass: String, requester: WifiRequester) {

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(pass)
            .build()

        requestNetwork(specifier.toRequest(), requester)
    }

    fun startWifiSearch(format: String, requester: WifiRequester) {

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsidPattern(
                PatternMatcher(
                    ".*$format.*",
                    PatternMatcher.PATTERN_SIMPLE_GLOB
                )
            )
            //adding BSSID will remove the need for the "connect" dialog
            //.setBssid(MacAddress.fromString(bssid!!))
            .build()

        requestNetwork(specifier.toRequest(), requester)
    }

    private fun WifiNetworkSpecifier.toRequest(): NetworkRequest =
        NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(this)
            .build()

    /**
     * Registers a single network request. Registered callbacks live at the framework level and
     * outlive the activity, so the previous one must always be unregistered first: otherwise they
     * accumulate until [ConnectivityManager.requestNetwork] throws TooManyRequestsException and
     * the device can only recover by toggling wifi.
     */
    private fun requestNetwork(request: NetworkRequest, requester: WifiRequester) {

        //releases any previous request and unbinds the process
        disconnect()

        requester.onApRequested()

        val callback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                Log.d(TAG, "Network Available")

                boundNetwork = network

                connectivityManager.bindProcessToNetwork(network)

                requester.onNetworkBound(network)
            }

            override fun onLost(network: Network) {
                super.onLost(network)

                if (network != boundNetwork) return

                Log.d(TAG, "Network Lost")

                boundNetwork = null

                try {
                    connectivityManager.bindProcessToNetwork(null)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to unbind process network", e)
                }

                requester.onNetworkLost()
            }

            override fun onUnavailable() {
                super.onUnavailable()

                Log.d(TAG, "Network Unavailable")

                requester.onNetworkUnavailable()
            }
        }

        networkCallback = callback

        try {
            connectivityManager.requestNetwork(request, callback, REQUEST_TIMEOUT_MS)
        } catch (e: RuntimeException) {
            //TooManyRequestsException is a RuntimeException and is not part of the public api
            Log.e(TAG, "Network request rejected", e)
            networkCallback = null
            requester.onNetworkUnavailable()
        }
    }

    fun disconnect() {

        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to unregister network callback", e)
            }
        }

        networkCallback = null
        boundNetwork = null

        try {
            connectivityManager.bindProcessToNetwork(null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unbind process network", e)
        }
    }
}
