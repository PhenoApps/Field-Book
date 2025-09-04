package com.fieldbook.tracker.devices.camera.gopro

import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGattCallback
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.MacAddress
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkInfo
import android.net.NetworkRequest
import android.net.wifi.ScanResult
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ProgressBar
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import com.arthenica.ffmpegkit.FFmpegKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI

/**
 * Helper class for various GoPro utility.
 * References:
 * https://github.com/sepp89117/GoEasyPro_Android/blob/master/app/src/main/java/com/sepp89117/goeasypro_android
 * https://gopro.github.io/OpenGoPro/
 */
@RequiresApi(Build.VERSION_CODES.M)
open class GoProHelper(val context: Context, val onReady: OnGoProStreamReady) : GoProController {

    data class GoProImage(val fileDir: String, val fileName: String, val mod: Long, val byteSize: Long, val url: String)

    interface OnGoProStreamReady {
        fun onStreamReady()
        fun onImageRequestReady(bitmap: Bitmap, data: Map<String, String>)
        fun onImageUrlSaved(image: GoProImage)
    }

    companion object {
        const val TAG = "GoProHelper"

        //used to filter bluetooth devices with this string
        const val GoProDeviceIdentifier = "GoPro"
        const val GoProWifiSsidPrefix = "GP"
        const val BLUETOOTH_SEARCH_DELAY = 3000L
        const val WIFI_SEARCH_DELAY = 3000L
        const val KEEP_ALIVE_MESSAGE_PACKET_DELAY = 5000L
        const val NETWORK_CONNECTION_TIMEOUT = 30000L
        const val UDP_SOCKET_TIMEOUT = 60000L
    }

    //coroutine scope and various jobs
    private var scope = MainScope()
    var networkSearchJob: Job? = null
    var bluetoothSearchJob: Job? = null
    var keepAliveJob: Job? = null
    var ffmpegJob: Job? = null
    var connectWifiJob: Job? = null

    //global cache of go pro bluetooth devices and scanned wifi networks
    var goProDevices: ArrayList<BluetoothDevice> = ArrayList()
    var wifiNetworks: ArrayList<ScanResult> = ArrayList()

    private var udpSocket: DatagramSocket? = null

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

                                Log.d(TAG, device.name)

                                if (GoProDeviceIdentifier in device.name) goProDevices.add(device)
                            }
                        }
                }
            }
        }
    }

    //wifi broadcast receiver for scanning networks
    private val wifiReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                WifiManager.SCAN_RESULTS_AVAILABLE_ACTION -> {

                    wifiManager.scanResults?.let { results ->

                        wifiNetworks.clear()

                        wifiNetworks.addAll(results)

                    }
                }

                WifiManager.NETWORK_STATE_CHANGED_ACTION -> {

                    val networkInfo =
                        intent.getParcelableExtra<NetworkInfo>(WifiManager.EXTRA_NETWORK_INFO)

                    if (networkInfo?.isConnected == true) {

                        Log.d(TAG, "Network state changed ${wifiManager.connectionInfo.ssid}")

                        if (wifiManager.connectionInfo.ssid.toString()
                                .startsWith(GoProWifiSsidPrefix)
                        ) {

                            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                                requestStream()
                            }

                            Log.d(TAG, "Wifi connected! ${networkInfo.extraInfo}")
                        }
                    }
                }
            }
        }
    }

    private val httpClient by lazy {
        OkHttpClient()
    }

    val wifiManager by lazy {

        context.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager

    }

    val connectivityManager by lazy {

        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    }

    private fun unregister(receiver: BroadcastReceiver) {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun unregisterReceivers() {
        unregister(receiver)
        unregister(wifiReceiver)
    }

    override fun registerReceivers() {
        context.registerReceiver(receiver, IntentFilter(BluetoothDevice.ACTION_FOUND))
        context.registerReceiver(
            wifiReceiver,
            IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
        )
        context.registerReceiver(wifiReceiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            .also { it.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION) })
    }

    override fun isBluetoothEnabled(adapter: BluetoothAdapter): Boolean {
        return adapter.isEnabled
    }

    /**
     * Creates a background coroutine to constantly search for GoPro bt devices
     */
    fun searchForBluetoothGoPro(activity: Activity, dialog: AlertDialog) {

        bluetoothSearchJob?.cancel()

        bluetoothSearchJob = scope.launch {

            withContext(Dispatchers.IO) {

                do {

                    ensureActive()

                    activity.runOnUiThread {

                        dialog.listView.adapter = ArrayAdapter(context,
                            android.R.layout.simple_list_item_single_choice,
                            goProDevices.map { it.name }.distinct())
                    }

                    delay(BLUETOOTH_SEARCH_DELAY)

                } while (true)
            }
        }
    }

    /**
     * Optional dialog linked to searchForBluetoothGoPro
     */
    override fun scanForGoPros(activity: Activity, onSelected: (BluetoothDevice?) -> Unit) {

        val dialog = AlertDialog.Builder(context)
            .setTitle("Scanning for GoPros")
            .setCancelable(true)
            .setSingleChoiceItems(
                goProDevices.map { it.name }.distinct().toTypedArray(),
                -1
            ) { dialog, which ->
                onSelected(goProDevices[which])
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                onSelected(null)
                dialog.dismiss()
            }
            .setOnDismissListener {
                bluetoothSearchJob?.cancel()
            }
            .create()

        dialog.show()

        searchForBluetoothGoPro(activity, dialog)
    }

    /**
     * Starts gatt connection
     */
    override fun connectToGoPro(device: BluetoothDevice, callback: BluetoothGattCallback) {

        device.connectGatt(context, false, callback)

    }

    override fun checkWifiEnabled(): Boolean {

        return wifiManager.isWifiEnabled

    }

    override fun enableWifi(): Boolean {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            context.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))

        } else {

            wifiManager.setWifiEnabled(true)

        }

        return wifiManager.isWifiEnabled

    }

    fun openWifiSettings() {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            context.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))

        } else {

            context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))

        }
    }

    fun getWifiSsid(): String {
        return wifiManager.connectionInfo.ssid
    }

    /**
     * Optional dialog linked to connectToGoProWifi
     */
    override fun awaitGoProAp(ssid: String?, pass: String?, bssid: String, onConnected: (Network) -> Unit) {

        ssid?.let { s ->

            pass?.let { p ->

                val dialog = AlertDialog.Builder(context)
                    .setTitle("Awaiting GoPro AP")
                    .setMessage("Connecting to network, this may take a minute. \nSSID: $s Password: $p")
                    .setCancelable(true)
                    .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                        dialog.dismiss()
                    }
                    .setNeutralButton("Manual Connect") { dialog, _ ->
                        openWifiSettings()
                    }
                    .setOnDismissListener {
                        Log.d(TAG, "$s connection attempting...")
                        //networkSearchJob?.cancel()
                    }
                    .create()

                dialog.setView(ProgressBar(context).also {
                    it.isIndeterminate = true
                    it.layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    )
                    it.layout(16, 16, 16, 16)
                })

                dialog.show()

                connectToGoProWifi(dialog, s, p, bssid, onConnected)
            }
        }
    }

    /**
     * Uses deprecated API to attempt force connect a network on devices lower than Q
     */
    private fun connectToGoProWifiUnderQ(ssid: String, password: String, dialog: AlertDialog, onConnected: (Network) -> Unit) {

        val config = WifiConfiguration().also {
            it.SSID = "\"${ssid}\""
            it.preSharedKey = "\"${password}\""
        }

        val netId = wifiManager.addNetwork(config)

        wifiManager.disconnect()

        wifiManager.enableNetwork(netId, true)

        wifiManager.reconnect()

        dialog.dismiss()

        Handler(Looper.getMainLooper()).postDelayed({

            requestStream()

        }, 5000L)

    }

    /**
     * Background network job that uses network specifier to connect to GoPro AP
     * This delegates functionality based on Android version
     * On Android Q, the network specifier times out after 30 seconds, where a system dialog might show
     */
    override fun connectToGoProWifi(dialog: AlertDialog, ssid: String, password: String, bssid: String?, onConnected: (Network) -> Unit) {

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {

            bssid?.let { b ->

                Log.d(TAG, "Attempting to connect to $ssid $password > Q")

                var connected = false

                connectWifiJob?.cancel()

                connectWifiJob = scope.launch {

                    withContext(Dispatchers.IO) {

                        val wifiNetworkSpecifier = WifiNetworkSpecifier.Builder()
                            .setSsid(ssid)
                            .setBssid(MacAddress.fromString(b))
                            .setWpa2Passphrase(password)
                            .build()

                        val networkRequest = NetworkRequest.Builder()
                            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                            .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_RESTRICTED)
                            .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                            .removeCapability(NetworkCapabilities.NET_CAPABILITY_TRUSTED)
                            .setNetworkSpecifier(wifiNetworkSpecifier)
                            .build()

                        do {

                            //unregister existing callbacks
                            try {
                                connectivityManager.unregisterNetworkCallback(networkCallback!!)
                            } catch (ignore: Exception) {}

                            networkCallback = dismissOnNetworkAvailable(dialog) { net ->

                                connected = true

                                onConnected(net)
                            }

                            connectivityManager.requestNetwork(
                                networkRequest,
                                networkCallback!!,
                                NETWORK_CONNECTION_TIMEOUT.toInt()
                            )

                            delay(NETWORK_CONNECTION_TIMEOUT)

                        } while (!connected)
                    }
                }
            }

        } else {

            connectToGoProWifiUnderQ(ssid, password, dialog, onConnected)

        }
    }

    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private fun dismissOnNetworkAvailable(dialog: AlertDialog, onConnected: (Network) -> Unit): ConnectivityManager.NetworkCallback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                super.onAvailable(network)
                Log.i(TAG, "Network binding...")
                connectivityManager.bindProcessToNetwork(network)
                dialog.dismiss()
                onConnected(network)
            }

            override fun onUnavailable() {
                super.onUnavailable()
                Log.i(TAG, "Network unavailable...")
            }

            override fun onBlockedStatusChanged(network: Network, blocked: Boolean) {
                super.onBlockedStatusChanged(network, blocked)
                Log.i(TAG, "Network blocked status changed...")
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                super.onCapabilitiesChanged(network, networkCapabilities)
                Log.i(TAG, "Network on capabilities changed...")
            }

            override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                super.onLinkPropertiesChanged(network, linkProperties)
                Log.i(TAG, "Network on link properties changed...")

            }

            override fun onLosing(network: Network, maxMsToLive: Int) {
                super.onLosing(network, maxMsToLive)
                Log.i(TAG, "Network onLosing...")

            }

            override fun onLost(network: Network) {
                super.onLost(network)
                Log.i(TAG, "Network onLost...")

            }
        }

    /**
     * Makes http request to start go pro stream.
     * First makes an attempt to stop the stream in case it is already running.
     */
    fun requestStream() {

        Log.d(TAG, "Request stream stop to start.")

        //stop stream first, on fail or success start stream again:
        val stopPreview: Request = Request.Builder()
            .url(URI.create("http://10.5.5.9:8080/gopro/camera/stream/stop").toHttpUrlOrNull()!!)
            .build()

        httpClient.newCall(stopPreview).enqueue(object : Callback {

            override fun onFailure(call: Call, e: okio.IOException) {
                Log.e(TAG, "Request stop failed.")
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    requestStartStream()
                    Log.e(TAG, "Request stop preview response = not success")
                } else {
                    requestStartStream()
                    Log.i(TAG, "Request stop preview response = success")
                }
                response.close()
            }
        })
    }

    /**
     * Makes http request to start go pro stream.
     * If request is successfull it starts the keep alive background routine
     */
    fun requestStartStream() {

        Log.d(TAG, "Request stream start.")

        val startPreview: Request = Request.Builder()
            .url(URI.create("http://10.5.5.9:8080/gopro/camera/stream/start").toHttpUrlOrNull()!!)
            .build()

        httpClient.newCall(startPreview).enqueue(object : Callback {

            override fun onFailure(call: Call, e: okio.IOException) {
                Log.e(TAG, "Failed to make network request to GoPro AP")
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request response = not success ${response.code}")
                    initRequestTimer()
                } else {
                    Log.i(TAG, "Request response = success")
                    initRequestTimer()
                    onReady.onStreamReady()
                }
                response.close()
            }
        })
    }

    /**
     * Makes http request to stop stream and cancel necessary threads.
     */
    override fun stopStream() {

        Log.d(TAG, "Attempting stop preview request.")

        val stopPreview: Request = Request.Builder()
            .url(URI.create("http://10.5.5.9:8080/gopro/camera/stream/stop").toHttpUrlOrNull()!!)
            .build()

        httpClient.newCall(stopPreview).enqueue(object : Callback {

            override fun onFailure(call: Call, e: okio.IOException) {
                Log.e(TAG, "Request stop failed.")
                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request stop preview response = not success")
                } else {
                    Log.i(TAG, "Request stop preview response = success")
                }
                response.close()
            }
        })

        ffmpegJob?.cancel()

        FFmpegKit.cancel()

        keepAliveJob?.cancel()
    }


    override fun startStream() {

        startFfmpegBackgroundLooper()

    }

    /**
     * Starts FFMPEG background coroutine that creates udp substream for Android/Exoplayer to interpret.
     */
    private fun startFfmpegBackgroundLooper() {

        ffmpegJob?.cancel()

        FFmpegKit.cancel()

        ffmpegJob = scope.launch {

            withContext(Dispatchers.IO) {

                val streamInputUri = "udp://:8554" // maybe different depending on gopro modelID?

                try {

                    val command =
                        "-fflags nobuffer -flags low_delay -f:v mpegts -an -probesize 100000 -i $streamInputUri -f mpegts -vcodec copy udp://localhost:8555?pkt_size=1316" // -probesize 100000 is minimum for Hero 10

                    Log.d(TAG, "Executing FFMPEG Kit: $command")

                    FFmpegKit.execute(command)

                } catch (e: java.lang.Exception) {

                    Log.e("FFmpeg", "Exception on command execution: $e")

                }

                do {

                    ensureActive()

                    delay(3000)

                } while (true)
            }
        }
    }

    override fun shutterOff() {
        TODO("Not yet implemented")
    }

    /**
     * http request to read media list (files on gopro device)
     */
    override fun queryMedia(data: Map<String, String>, requestAndSaveImage: Boolean) {

        Log.d(TAG, "Attempting media list query.")

        //stop stream first, on fail or success start stream again:
        val mediaQuery: Request = Request.Builder()
            .url(URI.create("http://10.5.5.9:8080/gopro/media/list").toHttpUrlOrNull()!!)
            .build()

        httpClient.newCall(mediaQuery).enqueue(object : Callback {

            override fun onFailure(call: Call, e: okio.IOException) {

                Log.e(TAG, "Media query failed.")

                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {

                if (!response.isSuccessful) {

                    Log.e(TAG, "Media query not success")

                } else {

                    parseMediaQueryResponse(response.body?.string() ?: "{}", data, requestAndSaveImage)

                    Log.i(TAG, "Media query success.")

                }

                response.close()
            }
        })
    }

    /**
     * parses media list response and returns the last requests the most recent file
     */
    private fun parseMediaQueryResponse(responseBody: String, data: Map<String, String>, requestAndSaveImage: Boolean = true) {

        try {

            val json = JSONObject(responseBody)

            Log.d(TAG, json.toString(1))

            val mediaArray = json.getJSONArray("media")

            val size = mediaArray.length()

            val images = arrayListOf<GoProImage>()

            for (i in 0 until size) {

                val media = mediaArray.getJSONObject(i)

                val dir = media.getString("d")

                val files = media.getJSONArray("fs")

                val numFiles = files.length()

                for (j in 0 until numFiles) {

                    val file = files.getJSONObject(j)

                    val fileName = file.getString("n")

                    images.add(
                        GoProImage(
                        dir,
                        fileName,
                        file.getString("mod").toLong(),
                        file.getString("s").toLong(),
                        "http://10.5.5.9:8080/videos/DCIM/$dir/$fileName"
                    )
                    )
                }
            }

            val latest = images.maxBy { it.mod }

            if (requestAndSaveImage) {
                requestFileUrl(latest.url, data)
            } else {
                onReady.onImageUrlSaved(latest)
            }

        } catch (e: JSONException) {

            e.printStackTrace()

        }
    }

    /**
     * requests the image at the given url, calls onReady interface when image is downloaded
     */
    private fun requestFileUrl(url: String, data: Map<String, String>) {

        Log.d(TAG, "Image request: $url")

        //stop stream first, on fail or success start stream again:
        val requestImage: Request = Request.Builder()
            .url(URI.create(url).toHttpUrlOrNull()!!)
            .build()

        httpClient.newCall(requestImage).enqueue(object : Callback {

            override fun onFailure(call: Call, e: okio.IOException) {

                Log.e(TAG, "Request image failed.")

                e.printStackTrace()
            }

            override fun onResponse(call: Call, response: Response) {

                if (!response.isSuccessful) {

                    Log.e(TAG, "Request image response = not success")

                } else {

                    Log.i(TAG, "Request image response = success")

                    response.body?.byteStream()?.use { inputStream ->

                        onReady.onImageRequestReady(BitmapFactory.decodeStream(inputStream), data)

                    }
                }

                response.close()
            }
        })
    }

    fun onDestroy() {

        stopStream()

        udpSocket?.disconnect()

        unregisterReceivers()

        networkCallback?.let {
            connectivityManager.unregisterNetworkCallback(it)
        }

        networkCallback = null
        connectivityManager.bindProcessToNetwork(null)

        networkSearchJob?.cancel()
        keepAliveJob?.cancel()
        bluetoothSearchJob?.cancel()
        ffmpegJob?.cancel()

        FFmpegKit.cancel()
    }

    /**
     * starts a background thread to send keep alive messages
     */
    fun initRequestTimer() {

        startFfmpegBackgroundLooper()

        val keepStreamAliveData = "_GPHD_:1:0:2:0.000000\n".toByteArray()

        try {

            val inetAddress = InetAddress.getByName("10.5.5.9")

            try {

                udpSocket?.disconnect()

                if (udpSocket == null) {
                    udpSocket = DatagramSocket().also {
                        it.reuseAddress = true
                        it.soTimeout = UDP_SOCKET_TIMEOUT.toInt()
                    }
                }

                udpSocket?.bind(InetSocketAddress(8554))

            } catch (ignore: Exception) { }

            keepAliveJob?.cancel()

            keepAliveJob = scope.launch {

                withContext(Dispatchers.IO) {

                    while (true) {

                        try {

                            val keepStreamAlivePacket = DatagramPacket(
                                keepStreamAliveData,
                                keepStreamAliveData.size,
                                inetAddress,
                                8554
                            )

                            udpSocket?.send(keepStreamAlivePacket)

                            Log.i(TAG, "Keep Alive sent")

                        } catch (e: Exception) {

                            e.printStackTrace()

                        }

                        delay(KEEP_ALIVE_MESSAGE_PACKET_DELAY)
                    }
                }
            }

            Log.i(TAG, "requestTimer init successfully")

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }
}