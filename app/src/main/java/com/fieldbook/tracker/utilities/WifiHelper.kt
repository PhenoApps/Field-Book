package com.fieldbook.tracker.utilities

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiManager
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
        const val REQUEST_TIMEOUT_MS = 60000L

        /**
         * Once the deadline passes while the access point is visible in recent scan results, the
         * user most likely has it listed in the connect dialog and just has not accepted yet, so
         * the wait is extended in these steps, up to [MAX_REQUEST_WAIT_MS] in total.
         */
        private const val VISIBLE_EXTENSION_MS = 30000L
        private const val MAX_REQUEST_WAIT_MS = 180000L

        //scan results older than this no longer count as the access point being in range
        private const val SCAN_RESULT_MAX_AGE_MS = 30000L
    }

    private val connectivityManager by lazy {
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    }

    private val wifiManager by lazy {
        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
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

        requestNetwork(specifier.toRequest(), requester, ssid) { it == ssid }
    }

    fun startWifiSearch(format: String, requester: WifiRequester) {

        val pattern = PatternMatcher(".*$format.*", PatternMatcher.PATTERN_SIMPLE_GLOB)

        val specifier = WifiNetworkSpecifier.Builder()
            .setSsidPattern(pattern)
            //adding BSSID will remove the need for the "connect" dialog
            //.setBssid(MacAddress.fromString(bssid!!))
            .build()

        requestNetwork(specifier.toRequest(), requester, "*$format*") { pattern.match(it) }
    }

    /**
     * Whether a network matching [matches] showed up in the system's recent scan results. Only
     * reads what the system already has, the connect dialog scans on its own while it is open.
     * Returns false when the results are unavailable, e.g. location permission or services off.
     */
    private fun isVisibleInScan(matches: (String) -> Boolean): Boolean {
        return try {
            val nowMicros = SystemClock.elapsedRealtime() * 1000
            wifiManager.scanResults.any { result ->
                @Suppress("DEPRECATION")
                val ssid = result.SSID
                !ssid.isNullOrEmpty()
                        && nowMicros - result.timestamp < SCAN_RESULT_MAX_AGE_MS * 1000
                        && matches(ssid)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Unable to read wifi scan results", e)
            false
        }
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
    private fun requestNetwork(
        request: NetworkRequest,
        requester: WifiRequester,
        label: String,
        matches: (String) -> Boolean
    ) {

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

        timeoutRunnable = object : Runnable {
            override fun run() {

                if (networkCallback !== callback || boundNetwork != null) return

                //the access point is in range, so the user is most likely looking at it in the
                //connect dialog: keep waiting for them rather than failing underneath it
                if (elapsed() + VISIBLE_EXTENSION_MS <= MAX_REQUEST_WAIT_MS && isVisibleInScan(matches)) {
                    Log.d(TAG, "$label is visible after ${elapsed()}ms, extending wait")
                    timeoutHandler.postDelayed(this, VISIBLE_EXTENSION_MS)
                    return
                }

                Log.e(TAG, "Timed out joining $label after ${elapsed()}ms")
                disconnect()
                requester.onNetworkUnavailable()
            }
        }.also { timeoutHandler.postDelayed(it, REQUEST_TIMEOUT_MS) }
    }

    /**
     * Unbinds the process from the access point while keeping the request, and with it the
     * connection, alive. Traffic that is not tied to the network explicitly returns to the
     * default route, so the rest of the app is not stuck behind a network with no internet.
     */
    fun releaseProcessBinding() {
        try {
            connectivityManager.bindProcessToNetwork(null)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to unbind process network", e)
        }
    }

    /**
     * Rebinds the process after [releaseProcessBinding]. Returns false if [network] is no longer
     * the one this helper holds, i.e. it was lost or replaced by another request in the meantime.
     */
    fun restoreProcessBinding(network: Network): Boolean {

        if (boundNetwork != network) return false

        return try {
            connectivityManager.bindProcessToNetwork(network)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to rebind process network", e)
            false
        }
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
