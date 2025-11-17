package com.fieldbook.tracker.devices.camera

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.fieldbook.tracker.interfaces.CollectController
import com.fieldbook.tracker.objects.RangeObject
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.WifiHelper
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import org.phenoapps.fragments.gopro.GoProGatt
import org.phenoapps.fragments.gopro.GoProGattInterface
import org.phenoapps.interfaces.gatt.GattCallbackInterface
import java.net.SocketException
import java.net.URI
import javax.inject.Inject

@RequiresApi(Build.VERSION_CODES.Q)
@UnstableApi
class GoProApi @Inject constructor(
    @ActivityContext private val context: Context
) :
    GattCallbackInterface,
    GoProGattInterface,
    GoProGatt.GoProGattController,
    WifiHelper.WifiRequester {

    data class GoProImage(
        val fileDir: String,
        val fileName: String,
        val mod: Long,
        val byteSize: Long,
        val url: String
    )

    data class ImageRequestData(
        val studyId: String,
        val range: RangeObject,
        val trait: TraitObject,
        val time: String
    )

    interface Callbacks {
        fun onConnected()
        fun onInitializeGatt()
        fun onStreamReady()
        fun onStreamRequested()
        fun onImageRequestReady(bytes: ByteArray, data: ImageRequestData, model: GoProImage? = null)
        fun onBusyStateChanged(isBusy: Int, isEncoding: Int)
    }

    //state id refers to https://gopro.github.io/OpenGoPro/http#tag/Query/operation/OGP_GET_STATE
    enum class GoProStateKeys(val key: String) {
        BUSY("8"),
        IS_ENCODING("10")
    }

    companion object {
        const val TAG = "GoProApi"
        private const val ffmpegOutputUri = "udp://@localhost:8555"
        private const val REQUEST_TIMEOUT_MS = 4000
        private const val MAX_REQUEST_RETRIES = 3
    }

    private val gatt by lazy {
        GoProGatt(this)
    }

    private val controller by lazy {
        context as CollectController
    }

    private var httpClient: OkHttpClient? = OkHttpClient()

    private val ioScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val playerListener: Player.Listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            when (playbackState) {
                Player.STATE_IDLE, Player.STATE_ENDED -> {
                    Log.d(
                        TAG, "Player Idle/Ended"
                    )
                    streamStarted = false
                }

                Player.STATE_BUFFERING -> if (!streamStarted) {
                    Log.d(TAG, "Player Buffering")
                    Log.d(TAG, "Requesting start stream.")
                    streamStarted = true
                }

                Player.STATE_READY -> {
                    Log.d(TAG, "Player Ready ${if (range.isNotEmpty()) range[0].range.uniqueId else ""}")

                    callbacks?.onStreamReady()
                }
            }
        }
    }

    private var requestedUrls = arrayListOf<String>()
    private var player: ExoPlayer? = null
    private var callbacks: Callbacks? = null

    var streamStarted = false
    var range: ArrayList<ImageRequestData> = arrayListOf()
    var lastMoved: ImageRequestData? = null

    /**
     * Coroutine based execution with timeout + retry/backoff.
     * Throws exception on final failure.
     */
    private suspend fun executeWithRetrySuspend(request: Request): Response {
        var lastException: Exception? = null

        for (attempt in 1..MAX_REQUEST_RETRIES) {
            val call = try {
                httpClient?.newCall(request) ?: throw IllegalStateException("No http client")
            } catch (e: Exception) {
                lastException = e
                break
            }

            try {
                //if it times out it will cancel the call via the catch/finally
                val response = try {
                    withTimeout(REQUEST_TIMEOUT_MS.toLong()) {
                        call.execute()
                    }
                } catch (t: Throwable) {
                    //ensure call is cancelled if timeout/coroutine cancelled
                    try { call.cancel() } catch (_: Exception) {}
                    throw t
                }

                //if we have response then return it to caller (caller must close)
                return response
            } catch (e: Exception) {
                lastException = e as? Exception ?: Exception(e)
                //attempt to reset client/network bindings
                if (e is SocketException || (e.message?.contains("Binding socket to network") == true)) {
                    resetStaleNetworkBindingIfNeeded(e)
                }
                if (attempt < MAX_REQUEST_RETRIES) {
                    //backoff before next attempt but respect coroutine cancellation
                    val backoff = REQUEST_TIMEOUT_MS.toLong() * attempt
                    try {
                        delay(backoff)
                    } catch (cancelled: Throwable) {
                        // propagate cancellation
                        throw cancelled
                    }
                } else {
                    throw lastException ?: Exception("Unknown network error")
                }
            }
        }

        throw lastException ?: Exception("Failed to execute request")
    }

    private fun closeAndEvictClientConnections(client: OkHttpClient?) {
        try {
            client?.dispatcher?.cancelAll()
        } catch (_: Exception) {}
        try {
            client?.connectionPool?.evictAll()
        } catch (_: Exception) {}
        try {
            client?.dispatcher?.executorService?.shutdown()
        } catch (_: Exception) {}
    }

    private fun resetStaleNetworkBindingIfNeeded(e: Throwable) {
        try {
            val msg = e.message ?: ""
            if (e is SocketException && msg.contains("Binding socket to network")) {
                Log.w(TAG, "Detected stale network binding: \"$msg\" — cancelling HTTP work and resetting client.")

                try {
                    closeAndEvictClientConnections(httpClient)
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to cancel OkHttp calls: ${t.message}")
                }

                try {
                    val cm = context.getSystemService(ConnectivityManager::class.java)
                    cm?.bindProcessToNetwork(null)
                } catch (t: Throwable) {
                    Log.w(TAG, "Failed to unbind process network: ${t.message}")
                }

                httpClient = OkHttpClient()
            }
        } catch (t: Throwable) {
            Log.w(TAG, "Error handling stale network binding: ${t.message}")
        }
    }

    fun getBusyState() {

        val getState: Request = Request.Builder()
            .url(URI.create("http://10.5.5.9:8080/gopro/camera/state").toHttpUrlOrNull()!!)
            .build()

        ioScope.launch {
            try {
                val response = executeWithRetrySuspend(getState)
                response.use {
                    if (!it.isSuccessful) {
                        Log.e(TAG, "Request state response = not success ${it.code}")
                    } else {
                        parseState(it.body?.string() ?: "{}")
                        Log.i(TAG, "Request state response = success")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Request state failed.")
                resetStaleNetworkBindingIfNeeded(e)
                e.printStackTrace()
            }
        }
    }

    private fun parseState(responseBody: String) {
        try {
            val json = JSONObject(responseBody)
            //Log.d(TAG, json.toString(1))
            val state = json.getJSONObject("status")
            //Log.d(TAG, "GoPro state: $state")
            val busy = state.getInt(GoProStateKeys.BUSY.key)
            val isEncoding = state.getInt(GoProStateKeys.IS_ENCODING.key)
            //Log.d(TAG, "Camera is busy: ${busy == 1}")
            //Log.d(TAG, "Camera is encoding: ${isEncoding == 1}")

            callbacks?.onBusyStateChanged(busy, isEncoding)

        } catch (e: JSONException) {
            e.printStackTrace()
        }
    }

    fun requestStream() {

        //stop stream first, on fail or success start stream again:
        val stopPreview: Request = Request.Builder()
            .url(URI.create("http://10.5.5.9:8080/gopro/camera/stream/stop").toHttpUrlOrNull()!!)
            .build()

        httpClient?.newCall(stopPreview)?.enqueue(object : Callback {

            override fun onFailure(call: okhttp3.Call, e: okio.IOException) {
                Log.e(TAG, "Request stop failed.")
                e.printStackTrace()
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Request stop preview response = not success")
                    } else {
                        Log.i(TAG, "Request stop preview response = success")
                    }
                    requestStartStream()
                } finally {
                    response.close()
                }
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

        ioScope.launch {
            try {

                withTimeoutOrNull(2000L) {
                    while (httpClient == null) {
                        delay(100L)
                    }
                }

                if (httpClient == null) {
                    Log.w(TAG, "httpClient still null after wait; aborting start stream request")
                    return@launch
                }

                val response = executeWithRetrySuspend(startPreview)
                response.use {
                    if (!it.isSuccessful) {
                        Log.e(TAG, "Request response = not success ${it.code}")
                    } else {
                        Log.i(TAG, "Request response = success")
                    }
                    controller.getFfmpegHelper().initRequestTimer()
                    callbacks?.onStreamRequested()

                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to make network request to GoPro AP")
                e.printStackTrace()
            }
        }
    }

    /**
     * http request to read media list (files on gopro device)
     */
    fun queryMedia(requestAndSaveImage: Boolean = true) {

        val model = if (range.isNotEmpty()) range.removeAt(0) else lastMoved

        Log.d(TAG, "Attempting media list query.")

        //stop stream first, on fail or success start stream again:
        val mediaQuery: Request = Request.Builder()
            .url(URI.create("http://10.5.5.9:8080/gopro/media/list").toHttpUrlOrNull()!!)
            .build()

        httpClient?.newCall(mediaQuery)?.enqueue(object : Callback {

            override fun onFailure(call: okhttp3.Call, e: okio.IOException) {

                Log.e(TAG, "Media query failed.")

                e.printStackTrace()
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {

                try {
                    if (!response.isSuccessful) {

                        Log.e(TAG, "Media query not success")

                    } else {

                        parseMediaQueryResponse(response.body?.string() ?: "{}", model!!, requestAndSaveImage)

                        Log.i(TAG, "Media query success.")

                    }
                } finally {
                    response.close()
                }
            }
        })
    }

    /**
     * Makes http request to stop stream and cancel necessary threads.
     */
    private fun stopStream() {

        Log.d(TAG, "Attempting stop preview request.")

        val stopPreview: Request = Request.Builder()
            .url(URI.create("http://10.5.5.9:8080/gopro/camera/stream/stop").toHttpUrlOrNull()!!)
            .build()

        httpClient?.newCall(stopPreview)?.enqueue(object : Callback {

            override fun onFailure(call: okhttp3.Call, e: okio.IOException) {
                Log.e(TAG, "Request stop failed.")
                e.printStackTrace()
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                try {
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Request stop preview response = not success")
                    } else {
                        Log.i(TAG, "Request stop preview response = success")
                    }
                } finally {
                    response.close()
                }
            }
        })
    }

    fun onDestroy() {

        stopStream()

        disableAp()

        controller.getFfmpegHelper().cancel()

        try {
            httpClient?.dispatcher?.cancelAll()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to cancel OkHttp calls: ${e.message}")
        }

        controller.getWifiHelper().disconnect()

        // recreate httpClient and evict prior sockets
        try {
            closeAndEvictClientConnections(httpClient)
        } catch (_: Exception) {}
        httpClient = OkHttpClient()

        gatt.clear()

        //reset ui component states
        player?.stop()
        player?.release()
        player?.clearVideoSurface()
        player = null
        //reset global flags
        this.streamStarted = false

        ioScope.cancel()
    }

    fun onConnect(device: BluetoothDevice, callbacks: Callbacks) {

        this.callbacks = callbacks

        gatt.clear()

        device.connectGatt(context, false, gatt.callback)

        callbacks.onInitializeGatt()
    }

    fun isStreamStarted(): Boolean = streamStarted

    fun createPlayer(): ExoPlayer {

        //Max. Buffer: The maximum duration, in milliseconds, of the media the player is attempting to buffer. Once the buffer reaches Max Buffer, it will stop filling it up.
        //min Buffer: The minimum length of media that the player will ensure is buffered at all times, in milliseconds.
        //Playback Buffer: The default amount of time, in milliseconds, of media that needs to be buffered in order for playback to start or resume after a user action such as a seek.
        //Buffer for playback after rebuffer: The duration of the media that needs to be buffered in order for playback to continue after a rebuffer, in milliseconds.
        player?.removeListener(playerListener)
        player?.stop()
        player?.release()
        player = null

        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context)
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBufferDurationsMs(2500, 5000, 1500, 2000)
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                if (mimeType == MimeTypes.VIDEO_MV_HEVC) emptyList() else MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            }

        player = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderersFactory)
            .build()

        player?.addListener(playerListener)

        val mediaSource: androidx.media3.exoplayer.source.MediaSource =
            androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(
                androidx.media3.datasource.DefaultDataSource.Factory(context)
            ).createMediaSource(
                androidx.media3.common.MediaItem.fromUri(
                    ffmpegOutputUri.toUri()
                )
            )

        player?.setMediaSource(mediaSource)
        player?.playWhenReady = true
        player?.prepare()

        return player as ExoPlayer

    }

    /**
     * parses media list response and returns the last requests the most recent file
     */
    private fun parseMediaQueryResponse(
        responseBody: String,
        model: ImageRequestData,
        requestAndSaveImage: Boolean = true
    ) {

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

            val pattern = Regex("^([a-zA-Z]*)([0-9]*).([a-zA-Z]*)$")

            val latest = images.maxByOrNull {
                pattern.matchEntire(it.fileName)?.destructured?.let { (_, number, _) ->
                    number.toInt()
                } ?: -1
            }

            if (latest != null) {
                if (latest.url !in requestedUrls) {
                    requestedUrls.add(latest.url)

                    if (requestAndSaveImage) {
                        requestFileUrl(latest.url, model)
                    } else {
                        saveImageName(latest, model)
                    }

                    requestStream()
                }
            }

        } catch (e: JSONException) {

            e.printStackTrace()

        } catch (e: NoSuchElementException) {
            e.printStackTrace()
        }
    }

    private fun saveImageName(latest: GoProImage, model: ImageRequestData) {

        callbacks?.onImageRequestReady(
            byteArrayOf(),
            model,
            latest
        )
    }

    /**
     * requests the image at the given url, calls onReady interface when image is downloaded
     */
    private fun requestFileUrl(url: String, model: ImageRequestData) {

        Log.d(TAG, "Image request: $url for model: ${model.range.uniqueId}")

        //stop stream first, on fail or success start stream again:
        val requestImage: Request = Request.Builder()
            .url(URI.create(url).toHttpUrlOrNull()!!)
            .build()

        httpClient?.newCall(requestImage)?.enqueue(object : Callback {

            override fun onFailure(call: okhttp3.Call, e: okio.IOException) {

                Log.e(TAG, "Request image failed.")

                e.printStackTrace()
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {

                try {
                    if (!response.isSuccessful) {
                        Log.e(TAG, "Request image response = not success")
                        return
                    }

                    Log.i(TAG, "Request image response = success")

                    response.body?.byteStream()?.use { inputStream ->

                        val bytes = inputStream.readBytes()
                        Log.d(TAG, "Found image response with: ${bytes.size} bytes")

                        callbacks?.onImageRequestReady(bytes, model)
                    }

                    requestStream()

                } catch (t: Throwable) {
                    Log.e(TAG, "Error processing image response", t)
                } finally {
                    response.close()
                }
            }
        })
    }

    override fun onApRequested() {}

    override fun onBoardType(boardType: String) {}

    override fun onBssid(wifiBSSID: String) {}

    /**
     * Collect activity callback region
     */
    override fun onCredentialsAcquired() {

        try {

            Log.d(TAG, "onCredentialsAcquired ${gatt.ssid} ${gatt.password}")

            gatt.ssid?.let { ssid ->

                gatt.password?.let { pass ->

                    enableAp()

                    controller.getWifiHelper().startWifiSearch(ssid, pass, this)

                }
            }

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }

    override fun onFirmware(firmware: String) {}

    override fun onModelId(modelID: Int) {}

    override fun onModelName(modelName: String) {}

    override fun onSerialNumber(serialNumber: String) {}

    override fun onSsid(wifiSSID: String) {}

    override fun disableAp() {
        gatt.disableAp()
    }

    override fun enableAp() {
        gatt.enableAp()
    }

    override fun shutterOff() {
        gatt.shutterOff()
    }

    override fun shutterOn() {
        gatt.shutterOn()
    }

    override fun onNetworkBound(network: Network) {

        ioScope.launch {
            // Assign a fresh client bound to the network. Evict old connections first.
            try {
                closeAndEvictClientConnections(httpClient)
            } catch (_: Exception) {}

            httpClient = OkHttpClient.Builder()
                .socketFactory(network.socketFactory)
                .build()

            try {
                delay(400L)
            } catch (_: Exception) {}

            callbacks?.onConnected()
        }
    }
}