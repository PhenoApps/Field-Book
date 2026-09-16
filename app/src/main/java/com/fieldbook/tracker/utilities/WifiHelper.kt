package com.fieldbook.tracker.utilities

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.PatternMatcher
import android.os.SystemClock
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
         * How long to wait for the camera's access point before giving up. Generous: the user may
         * still be reading the system connect dialog.
         */
        const val REQUEST_TIMEOUT_MS = 45000L
    }

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val timeoutHandler = Handler(Looper.getMainLooper())
    private var timeoutRunnable: Runnable? = null

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var boundNetwork: Network? = null

    fun startWifiSearch(ssid: String, pass: String, requester: WifiRequester) {

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsid(ssid)
            .setWpa2Passphrase(pass)
            .build()

        requestNetwork(specifier.toRequest(), requester, ssid)
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

        requestNetwork(specifier.toRequest(), requester, "*$format*")
    }

    private fun WifiNetworkSpecifier.toRequest(): NetworkRequest =
        NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .setNetworkSpecifier(this)
            .build()

    /**
     * Registers a single network request for a camera's access point.
     *
     * Registered callbacks live at the framework level and outlive the activity, so the previous
     * one must always be released first: otherwise they accumulate until
     * [ConnectivityManager.requestNetwork] throws TooManyRequestsException and the device can only
     * recover by toggling wifi.
     */
    private fun requestNetwork(request: NetworkRequest, requester: WifiRequester, label: String) {

        //releases any previous request and unbinds the process
        disconnect()

        requester.onApRequested()

        val startedAt = SystemClock.elapsedRealtime()

        fun elapsed() = SystemClock.elapsedRealtime() - startedAt

        val callback = object : ConnectivityManager.NetworkCallback() {

            override fun onAvailable(network: Network) {
                super.onAvailable(network)

                cancelTimeout()

                boundNetwork = network

                val bound = try {
                    connectivityManager.bindProcessToNetwork(network)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to bind process to $label", e)
                    false
                }

                Log.d(TAG, "Joined $label in ${elapsed()}ms, process bound = $bound")

                requester.onNetworkBound(network)
            }

            override fun onLost(network: Network) {
                super.onLost(network)

                if (network != boundNetwork) return

                Log.w(TAG, "Lost $label after ${elapsed()}ms")

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

                cancelTimeout()

                Log.e(TAG, "$label unavailable after ${elapsed()}ms")

                requester.onNetworkUnavailable()
            }
        }

        networkCallback = callback

        Log.d(TAG, "Requesting network $label")

        try {
            //deliberately the untimed overload. The timeout variant's behaviour with a
            //WifiNetworkSpecifier is inconsistent across vendors, and when it does not deliver
            //onUnavailable the connect flow stalls with no way out, so the deadline is enforced
            //here where it is guaranteed to fire.
            connectivityManager.requestNetwork(request, callback)
        } catch (e: RuntimeException) {
            //TooManyRequestsException is a RuntimeException and is not part of the public api
            Log.e(TAG, "Network request for $label rejected", e)
            networkCallback = null
            requester.onNetworkUnavailable()
            return
        }

        timeoutRunnable = Runnable {
            if (networkCallback === callback && boundNetwork == null) {
                Log.e(TAG, "Timed out joining $label after ${elapsed()}ms")
                disconnect()
                requester.onNetworkUnavailable()
            }
        }.also { timeoutHandler.postDelayed(it, REQUEST_TIMEOUT_MS) }
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { timeoutHandler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    fun disconnect() {

        cancelTimeout()

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
