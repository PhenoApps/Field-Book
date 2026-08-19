package com.fieldbook.tracker.devices.camera

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.net.Network
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import com.fieldbook.tracker.R
import com.fieldbook.tracker.interfaces.CollectController
import com.fieldbook.tracker.objects.RangeObject
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.WifiHelper
import dagger.hilt.android.qualifiers.ActivityContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONException
import org.json.JSONObject
import org.phenoapps.fragments.gopro.GoProGatt
import org.phenoapps.fragments.gopro.GoProGattInterface
import org.phenoapps.interfaces.bridge.GattBridge
import org.phenoapps.interfaces.gatt.GattCallbackInterface
import java.io.IOException
import java.io.OutputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Drives a GoPro camera over BLE (control) and its wifi access point (preview stream + media).
 *
 * The class owns every resource the session acquires - the [BluetoothGatt] handle, the wifi
 * network binding, the http client, the ffmpeg pipeline and the ExoPlayer - and releases all of
 * them through [shutdown]. Anything that leaks here survives the activity: gatt client interfaces
 * and network callbacks are held by the framework, which is why leaking them used to force users
 * to toggle their radios before they could reconnect.
 */
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

    /**
     * Destination for a streamed image download. The api never buffers a whole photo in memory,
     * it asks the caller for a sink and either [commit]s or [discard]s it, so a failed transfer
     * cannot leave a partial file behind with a valid observation pointing at it.
     */
    interface ImageSink {
        fun openStream(): OutputStream?
        fun commit(bytesWritten: Long)
        fun discard()
    }

    /**
     * Lifecycle of a session. Every ui decision is driven from this rather than from the player's
     * playback flags, so a dropped access point returns the user to a usable connect button.
     */
    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING_BLE,
        AWAITING_AP,
        CONNECTING_WIFI,
        CONNECTED,
        STREAMING,
        CAPTURING,
        DISCONNECTING,
        ERROR;

        /** True while a session is being established or is live. */
        val isActive: Boolean
            get() = this != DISCONNECTED && this != DISCONNECTING && this != ERROR
    }

    interface Callbacks {
        fun onConnectionStateChanged(state: ConnectionState, @StringRes messageRes: Int?)
        fun onInitializeGatt()
        fun onConnected()
        fun onStreamReady()
        fun onStreamRequested()
        fun onCaptureFinished()
        fun onCaptureFailed(@StringRes messageRes: Int)
        fun onImageSinkRequested(data: ImageRequestData, model: GoProImage): ImageSink?
        fun onImageSaved(data: ImageRequestData, model: GoProImage)
        fun onImageNameReady(data: ImageRequestData, model: GoProImage)
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
        private const val BASE_URL = "http://10.5.5.9:8080"

        private const val REQUEST_TIMEOUT_MS = 4000L
        private const val MAX_REQUEST_RETRIES = 3

        //a multi megabyte photo over the camera's access point needs far more headroom
        private const val DOWNLOAD_TIMEOUT_MS = 60000L
        private const val DOWNLOAD_RETRIES = 2

        private const val BLE_CONNECT_TIMEOUT_MS = 20000L
        private const val BLE_DISCONNECT_TIMEOUT_MS = 800L

        //how long to wait for the camera to finish writing a photo
        private const val CAPTURE_TIMEOUT_MS = 20000L

        //how long to wait for the busy flag to rise before assuming the capture was instantaneous
        private const val CAPTURE_BUSY_WINDOW_MS = 3000L
        private const val CAPTURE_POLL_INTERVAL_MS = 500L

        //idle cadence for detecting captures triggered by the camera's own shutter button
        private const val IDLE_POLL_INTERVAL_MS = 2000L
    }

    private val gatt by lazy {
        GoProGatt(this)
    }

    /**
     * Our own gatt callback. [GoProGatt] exposes one too, but it neither reports link loss nor
     * owns the handle, so we sit in front of it and forward.
     */
    private val gattObserver by lazy {
        GattBridge.gattBridge(this)
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
                    Log.d(TAG, "Player Idle/Ended")
                }

                Player.STATE_BUFFERING -> {
                    Log.d(TAG, "Player Buffering")
                }

                Player.STATE_READY -> {
                    Log.d(TAG, "Player Ready")

                    if (connectionState != ConnectionState.CAPTURING) {
                        setState(ConnectionState.STREAMING)
                    }

                    callbacks?.onStreamReady()
                }
            }
        }
    }

    private var player: ExoPlayer? = null
    private var callbacks: Callbacks? = null

    private var bluetoothGatt: BluetoothGatt? = null
    private var disconnectSignal: CompletableDeferred<Unit>? = null
    private var credentialsSignal: CompletableDeferred<Unit>? = null

    /** Suppresses link-loss handling while we are the ones tearing the link down. */
    private var expectingDisconnect = false

    private var connectionState = ConnectionState.DISCONNECTED

    private val captureMutex = Mutex()
    private var captureJob: Job? = null
    private var pollJob: Job? = null
    private var pollPaused = false

    /**
     * Newest media url already consumed. A single value is enough to dedupe and, unlike the
     * unbounded list this replaced, it does not silently swallow a re-taken photo after a
     * reconnect.
     */
    private var lastRequestedUrl: String? = null

    /** Entry/trait to attach the next photo to, kept current as the user navigates plots. */
    var currentEntry: ImageRequestData? = null

    fun state(): ConnectionState = connectionState

    private fun setState(state: ConnectionState, @StringRes messageRes: Int? = null) {

        if (connectionState == state && messageRes == null) return

        Log.d(TAG, "Connection state: $connectionState -> $state")

        connectionState = state

        callbacks?.onConnectionStateChanged(state, messageRes)
    }

    /**
     * Coroutine based execution with timeout + retry/backoff.
     * Throws exception on final failure. Caller must close the returned response.
     */
    private suspend fun executeWithRetrySuspend(
        request: Request,
        timeoutMs: Long = REQUEST_TIMEOUT_MS,
        retries: Int = MAX_REQUEST_RETRIES
    ): Response {

        var lastException: Exception? = null

        for (attempt in 1..retries) {

            val call = try {
                httpClient?.newCall(request) ?: throw IllegalStateException("No http client")
            } catch (e: Exception) {
                lastException = e
                break
            }

            try {
                //if it times out it will cancel the call via the catch/finally
                val response = try {
                    withTimeout(timeoutMs) {
                        call.execute()
                    }
                } catch (t: Throwable) {
                    //ensure call is cancelled if timeout/coroutine cancelled
                    try { call.cancel() } catch (_: Exception) {}
                    throw t
                }

                //if we have response then return it to caller (caller must close)
                return response

            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastException = e
                if (attempt < retries) {
                    //backoff before next attempt but respect coroutine cancellation
                    delay(REQUEST_TIMEOUT_MS * attempt)
                } else {
                    throw e
                }
            }
        }

        throw lastException ?: IOException("Failed to execute request")
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

    private fun url(path: String): Request =
        Request.Builder().url(URI.create("$BASE_URL$path").toHttpUrlOrNull()!!).build()

    /**
     * Connection region
     */
    fun onConnect(device: BluetoothDevice, callbacks: Callbacks) {

        this.callbacks = callbacks

        ioScope.launch {

            //always start from a clean slate, a half open link is the usual reason a reconnect fails
            closeGatt()

            lastRequestedUrl = null

            setState(ConnectionState.CONNECTING_BLE)

            callbacks.onInitializeGatt()

            disconnectSignal = CompletableDeferred()

            val credentials = CompletableDeferred<Unit>()
            credentialsSignal = credentials

            val handle = try {
                device.connectGatt(context, false, gattObserver, BluetoothDevice.TRANSPORT_LE)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to open gatt connection", e)
                null
            }

            if (handle == null) {
                setState(ConnectionState.ERROR, R.string.gopro_error_ble_failed)
                return@launch
            }

            //keep our own reference: GoProGatt only assigns one once services are discovered,
            //so an early failure would otherwise leak the client interface for good
            bluetoothGatt = handle
            gatt.gatt = handle

            val acquired = withTimeoutOrNull(BLE_CONNECT_TIMEOUT_MS) {
                credentials.await()
            }

            if (acquired == null && connectionState == ConnectionState.CONNECTING_BLE) {
                Log.e(TAG, "Timed out waiting for GoPro credentials")
                closeGatt()
                setState(ConnectionState.ERROR, R.string.gopro_error_ble_timeout)
            }
        }
    }

    /**
     * Releases the gatt client interface. [BluetoothGatt.close] on its own aborts an in flight
     * disconnect and leaves the camera believing it still has a central attached, so the link is
     * dropped first and the closure waits briefly for the stack to confirm.
     */
    private suspend fun closeGatt() {

        val handle = bluetoothGatt ?: gatt.gatt

        expectingDisconnect = true

        try {

            try {
                handle?.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to disconnect gatt", e)
            }

            if (handle != null) {
                val signal = disconnectSignal
                if (signal != null) {
                    withTimeoutOrNull(BLE_DISCONNECT_TIMEOUT_MS) { signal.await() }
                } else {
                    //no session was established, still let the stack settle before closing
                    delay(BLE_DISCONNECT_TIMEOUT_MS)
                }
            }

            try {
                handle?.close()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to close gatt", e)
            }

            try {
                //cancels the credential read job and releases the packet buffer
                gatt.clear()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear gatt state", e)
            }

            gatt.gatt = null
            bluetoothGatt = null
            disconnectSignal = null

        } finally {
            expectingDisconnect = false
        }
    }

    private fun onLinkLost(status: Int) {

        if (expectingDisconnect) return

        if (!connectionState.isActive) return

        Log.e(TAG, "GoPro link lost with status $status")

        setState(ConnectionState.ERROR, R.string.gopro_error_ble_disconnected)

        teardownAsync()
    }

    /**
     * Teardown region
     */
    private var shutdownJob: Job? = null

    fun teardownAsync() {
        if (shutdownJob?.isActive == true) return
        if (connectionState == ConnectionState.DISCONNECTED) return
        shutdownJob = ioScope.launch { shutdown() }
    }

    /**
     * Releases everything in an order where each command can still reach the camera: the stream is
     * stopped while wifi is up and the access point is switched off while the gatt link is open.
     * The previous implementation fired both and immediately killed the transports underneath
     * them, which left the camera's radio on and its session half open.
     */
    suspend fun shutdown(): Unit = withContext(NonCancellable) {

        if (connectionState == ConnectionState.DISCONNECTING) return@withContext

        setState(ConnectionState.DISCONNECTING)

        try { captureJob?.cancel() } catch (_: Exception) {}
        stopPolling()

        withTimeoutOrNull(2000L) {
            try {
                stopStreamSuspend()
            } catch (e: Exception) {
                Log.w(TAG, "Stop stream request failed during shutdown", e)
            }
        }

        controller.getFfmpegHelper().cancel()

        try {
            disableAp()
            //give the write a chance to reach the camera before the link is closed
            delay(400L)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to disable access point", e)
        }

        closeGatt()

        closeAndEvictClientConnections(httpClient)
        httpClient = null

        controller.getWifiHelper().disconnect()

        withContext(Dispatchers.Main) {
            player?.removeListener(playerListener)
            player?.stop()
            player?.clearVideoSurface()
            player?.release()
            player = null
        }

        lastRequestedUrl = null
        currentEntry = null

        setState(ConnectionState.DISCONNECTED)
    }

    /**
     * Called when the activity is going away for good. The scope is only cancelled once the
     * teardown has actually run, otherwise it kills the coroutines delivering the commands.
     */
    fun onDestroy() {

        //reuse an in flight teardown rather than racing a second one against it
        val job = shutdownJob?.takeIf { it.isActive }
            ?: ioScope.launch { shutdown() }.also { shutdownJob = it }

        job.invokeOnCompletion {
            callbacks = null
            ioScope.cancel()
        }
    }

    /**
     * Stream region
     */
    fun requestStartStream() {
        ioScope.launch {
            try {
                startStreamSuspend()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to make network request to GoPro AP", e)
                setState(ConnectionState.ERROR, R.string.gopro_error_stream_failed)
            }
        }
    }

    private suspend fun startStreamSuspend() {

        Log.d(TAG, "Request stream start.")

        val response = executeWithRetrySuspend(url("/gopro/camera/stream/start"))

        response.use {
            if (!it.isSuccessful) {
                Log.e(TAG, "Start stream response = not success ${it.code}")
            } else {
                Log.i(TAG, "Start stream response = success")
            }
        }

        controller.getFfmpegHelper().initRequestTimer()

        callbacks?.onStreamRequested()
    }

    private suspend fun stopStreamSuspend() {

        Log.d(TAG, "Attempting stop preview request.")

        val response = executeWithRetrySuspend(url("/gopro/camera/stream/stop"), retries = 1)

        response.use {
            if (!it.isSuccessful) {
                Log.e(TAG, "Stop stream response = not success ${it.code}")
            }
        }
    }

    private suspend fun restartPreview() {
        try {
            stopStreamSuspend()
        } catch (e: Exception) {
            Log.w(TAG, "Stop stream failed while restarting preview", e)
        }
        try {
            startStreamSuspend()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart preview", e)
        }
    }

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
     * Capture region
     */

    /**
     * Takes a photo and, depending on the trait's copy setting, either downloads it or records the
     * name it was given on the camera.
     *
     * The whole sequence is serialized behind [captureMutex]: a shutter press used to schedule a
     * media query on a fixed delay *and* the busy poller fired a second one, so a single photo
     * could start two overlapping downloads over an already saturated link.
     */
    fun capture(data: ImageRequestData, saveImage: Boolean) {

        if (!connectionState.isActive) {
            callbacks?.onCaptureFailed(R.string.gopro_error_not_connected)
            return
        }

        if (captureMutex.isLocked) {
            callbacks?.onCaptureFailed(R.string.gopro_error_capture_busy)
            return
        }

        currentEntry = data

        captureJob = ioScope.launch {
            captureMutex.withLock {
                runCapture(data, saveImage, triggerShutter = true)
            }
        }
    }

    /**
     * Handles a photo the user took with the camera's own shutter button: the capture already
     * happened, so only the media list and the transfer are needed.
     */
    private fun harvest(data: ImageRequestData, saveImage: Boolean) {

        if (captureMutex.isLocked) return

        captureJob = ioScope.launch {
            captureMutex.withLock {
                runCapture(data, saveImage, triggerShutter = false)
            }
        }
    }

    private suspend fun runCapture(
        data: ImageRequestData,
        saveImage: Boolean,
        triggerShutter: Boolean
    ) {

        setState(ConnectionState.CAPTURING)

        pollPaused = true

        //a harvest is speculative, so only a capture the user explicitly asked for reports failures
        val fail: (Int) -> Unit = { messageRes ->
            Log.w(TAG, "Capture unsuccessful: ${context.getString(messageRes)}")
            if (triggerShutter) {
                callbacks?.onCaptureFailed(messageRes)
            }
        }

        try {

            if (triggerShutter) {

                shutterOn()

                if (!awaitCaptureIdle()) {
                    fail(R.string.gopro_error_capture_timeout)
                    return
                }
            }

            val latest = queryLatestMedia()

            when {
                latest == null || latest.url == lastRequestedUrl -> {
                    fail(R.string.gopro_error_no_new_photo)
                }

                else -> {
                    lastRequestedUrl = latest.url

                    if (saveImage) {
                        downloadImage(latest, data, fail)
                    } else {
                        callbacks?.onImageNameReady(data, latest)
                    }
                }
            }

        } catch (e: CancellationException) {
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Capture failed", t)
            fail(R.string.gopro_error_capture_failed)
        } finally {
            withContext(NonCancellable) {
                if (connectionState == ConnectionState.CAPTURING) {
                    restartPreview()
                    setState(ConnectionState.STREAMING)
                }
                pollPaused = false
                callbacks?.onCaptureFinished()
            }
        }
    }

    /**
     * Polls the camera until it reports it has finished writing the photo. Returns false if it
     * never settles within [CAPTURE_TIMEOUT_MS].
     */
    private suspend fun awaitCaptureIdle(): Boolean {

        val start = System.currentTimeMillis()
        var sawBusy = false

        while (System.currentTimeMillis() - start < CAPTURE_TIMEOUT_MS) {

            val state = fetchBusyState()

            if (state != null) {

                callbacks?.onBusyStateChanged(state.first, state.second)

                val busy = state.first == 1 || state.second == 1

                if (busy) {
                    sawBusy = true
                } else if (sawBusy || System.currentTimeMillis() - start > CAPTURE_BUSY_WINDOW_MS) {
                    return true
                }
            }

            delay(CAPTURE_POLL_INTERVAL_MS)
        }

        return false
    }

    private suspend fun fetchBusyState(): Pair<Int, Int>? {

        return try {

            val response = executeWithRetrySuspend(url("/gopro/camera/state"), retries = 1)

            response.use {
                if (!it.isSuccessful) {
                    Log.e(TAG, "Request state response = not success ${it.code}")
                    null
                } else {
                    parseState(it.body?.string() ?: "{}")
                }
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Request state failed.", e)
            null
        }
    }

    private fun parseState(responseBody: String): Pair<Int, Int>? {
        return try {
            val state = JSONObject(responseBody).getJSONObject("status")
            Pair(
                state.getInt(GoProStateKeys.BUSY.key),
                state.getInt(GoProStateKeys.IS_ENCODING.key)
            )
        } catch (e: JSONException) {
            Log.w(TAG, "Unable to parse camera state", e)
            null
        }
    }

    /**
     * Watches for captures triggered on the camera itself. Single instance: the previous
     * implementation started a fresh loop from the trait layout every time the preview became
     * ready, which happened on every plot navigation, so the polls stacked up and flooded the
     * access point.
     */
    fun startPolling() {

        if (pollJob?.isActive == true) return

        pollJob = ioScope.launch {

            var wasBusy = false

            while (isActive) {

                if (pollPaused || !connectionState.isActive) {

                    //drop the edge we were tracking, otherwise resuming after an app driven
                    //capture looks like a fresh busy -> idle transition
                    wasBusy = false

                } else {

                    val state = fetchBusyState()

                    if (state != null) {

                        callbacks?.onBusyStateChanged(state.first, state.second)

                        val busy = state.first == 1 || state.second == 1

                        //busy -> idle means a photo was just written
                        if (wasBusy && !busy) {
                            currentEntry?.let { entry ->
                                harvest(entry, entry.trait.saveImage)
                            }
                        }

                        wasBusy = busy
                    }
                }

                delay(IDLE_POLL_INTERVAL_MS)
            }
        }
    }

    fun stopPolling() {
        pollJob?.cancel()
        pollJob = null
        pollPaused = false
    }

    /**
     * Media region
     */
    private suspend fun queryLatestMedia(): GoProImage? {

        Log.d(TAG, "Attempting media list query.")

        val response = executeWithRetrySuspend(url("/gopro/media/list"))

        val body = response.use {
            if (!it.isSuccessful) {
                Log.e(TAG, "Media query not success ${it.code}")
                return null
            }
            it.body?.string() ?: "{}"
        }

        return parseMediaQueryResponse(body)
    }

    /**
     * Parses the media list and returns the most recently numbered file.
     */
    private fun parseMediaQueryResponse(responseBody: String): GoProImage? {

        return try {

            val mediaArray = JSONObject(responseBody).getJSONArray("media")

            val images = arrayListOf<GoProImage>()

            for (i in 0 until mediaArray.length()) {

                val media = mediaArray.getJSONObject(i)

                val dir = media.getString("d")

                val files = media.getJSONArray("fs")

                for (j in 0 until files.length()) {

                    val file = files.getJSONObject(j)

                    val fileName = file.getString("n")

                    images.add(
                        GoProImage(
                            dir,
                            fileName,
                            file.getString("mod").toLong(),
                            file.getString("s").toLong(),
                            "$BASE_URL/videos/DCIM/$dir/$fileName"
                        )
                    )
                }
            }

            val pattern = Regex("^([a-zA-Z]*)([0-9]*).([a-zA-Z]*)$")

            images.maxByOrNull {
                pattern.matchEntire(it.fileName)?.destructured?.let { (_, number, _) ->
                    number.toIntOrNull() ?: -1
                } ?: -1
            }

        } catch (e: JSONException) {
            Log.e(TAG, "Unable to parse media list", e)
            null
        }
    }

    /**
     * Streams the photo straight into the destination the caller supplies.
     *
     * The preview stream and its keep alive datagrams are stopped first. They share the camera's
     * access point with the transfer, and the contention was enough to drop the link every few
     * photos when copying images was enabled.
     */
    private suspend fun downloadImage(
        model: GoProImage,
        data: ImageRequestData,
        fail: (Int) -> Unit
    ) {

        val sink = callbacks?.onImageSinkRequested(data, model)

        if (sink == null) {
            Log.e(TAG, "No destination available for ${model.fileName}")
            fail(R.string.gopro_error_download_failed)
            return
        }

        Log.d(TAG, "Image request: ${model.url} for entry: ${data.range.uniqueId}")

        try {

            quietLink()

            val request = Request.Builder()
                .url(URI.create(model.url).toHttpUrlOrNull()!!)
                .build()

            val response = executeWithRetrySuspend(request, DOWNLOAD_TIMEOUT_MS, DOWNLOAD_RETRIES)

            val written = response.use { r ->

                if (!r.isSuccessful) throw IOException("Image request failed with ${r.code}")

                val body = r.body ?: throw IOException("Image request returned an empty body")

                val expected = body.contentLength()

                val output = sink.openStream() ?: throw IOException("Unable to open destination")

                val count = output.use { stream ->
                    body.byteStream().use { input -> input.copyTo(stream) }
                }

                if (expected > 0L && count != expected) {
                    throw IOException("Incomplete transfer $count/$expected")
                }

                count
            }

            Log.i(TAG, "Downloaded ${model.fileName} ($written bytes)")

            sink.commit(written)

            callbacks?.onImageSaved(data, model)

        } catch (e: CancellationException) {
            sink.discard()
            throw e
        } catch (t: Throwable) {
            Log.e(TAG, "Image download failed", t)
            sink.discard()
            //let the same file be retried on the next capture
            lastRequestedUrl = null
            fail(R.string.gopro_error_download_failed)
        } finally {
            withContext(NonCancellable) { unquietLink() }
        }
    }

    private suspend fun quietLink() {

        try {
            stopStreamSuspend()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to stop stream before download", e)
        }

        controller.getFfmpegHelper().pauseKeepAlive()

        withContext(Dispatchers.Main) { player?.pause() }
    }

    private suspend fun unquietLink() {

        controller.getFfmpegHelper().resumeKeepAlive()

        withContext(Dispatchers.Main) { player?.play() }
    }

    /**
     * Gatt callback region. Every method forwards to [GoProGatt] so the phenolib behaviour is
     * preserved, connection state handling is layered on top.
     */
    override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {

        if (newState == BluetoothProfile.STATE_DISCONNECTED) {
            disconnectSignal?.complete(Unit)
            onLinkLost(status)
        }

        this.gatt.onConnectionStateChange(gatt, status, newState)
    }

    override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
        this.gatt.onServicesDiscovered(gatt, status)
    }

    override fun onCharacteristicRead(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        this.gatt.onCharacteristicRead(gatt, characteristic, status)
    }

    override fun onCharacteristicWrite(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?,
        status: Int
    ) {
        this.gatt.onCharacteristicWrite(gatt, characteristic, status)
    }

    override fun onCharacteristicChanged(
        gatt: BluetoothGatt?,
        characteristic: BluetoothGattCharacteristic?
    ) {
        this.gatt.onCharacteristicChanged(gatt, characteristic)
    }

    override fun onDescriptorRead(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        this.gatt.onDescriptorRead(gatt, descriptor, status)
    }

    override fun onDescriptorWrite(
        gatt: BluetoothGatt?,
        descriptor: BluetoothGattDescriptor?,
        status: Int
    ) {
        this.gatt.onDescriptorWrite(gatt, descriptor, status)
    }

    /**
     * GoProGattController region
     */
    override fun onApRequested() {
        setState(ConnectionState.CONNECTING_WIFI)
    }

    override fun onBoardType(boardType: String) {}

    override fun onBssid(wifiBSSID: String) {}

    override fun onCredentialsAcquired() {

        credentialsSignal?.complete(Unit)

        try {

            Log.d(TAG, "onCredentialsAcquired ${gatt.ssid}")

            val ssid = gatt.ssid
            val pass = gatt.password

            if (ssid.isNullOrBlank() || pass.isNullOrBlank()) {
                setState(ConnectionState.ERROR, R.string.gopro_error_ble_failed)
                return
            }

            setState(ConnectionState.AWAITING_AP)

            enableAp()

            controller.getWifiHelper().startWifiSearch(ssid, pass, this)

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start wifi search", e)
            setState(ConnectionState.ERROR, R.string.gopro_error_wifi_unavailable)
        }
    }

    override fun onFirmware(firmware: String) {}

    override fun onModelId(modelID: Int) {}

    override fun onModelName(modelName: String) {}

    override fun onSerialNumber(serialNumber: String) {}

    override fun onSsid(wifiSSID: String) {}

    /**
     * GoProGattInterface region
     */
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

    /**
     * WifiRequester region
     */
    override fun onNetworkBound(network: Network) {

        ioScope.launch {
            // Assign a fresh client bound to the network. Evict old connections first.
            closeAndEvictClientConnections(httpClient)

            httpClient = OkHttpClient.Builder()
                .socketFactory(network.socketFactory)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .connectionPool(ConnectionPool(2, 30, TimeUnit.SECONDS))
                //the camera cannot serve a download, a media list and a state poll at once
                .dispatcher(Dispatcher().apply {
                    maxRequests = 2
                    maxRequestsPerHost = 2
                })
                .build()

            delay(400L)

            setState(ConnectionState.CONNECTED)

            callbacks?.onConnected()
        }
    }

    override fun onNetworkLost() {

        if (!connectionState.isActive) return

        Log.e(TAG, "GoPro access point lost")

        setState(ConnectionState.ERROR, R.string.gopro_error_wifi_lost)

        teardownAsync()
    }

    override fun onNetworkUnavailable() {

        if (!connectionState.isActive) return

        Log.e(TAG, "GoPro access point unavailable")

        setState(ConnectionState.ERROR, R.string.gopro_error_wifi_unavailable)

        teardownAsync()
    }
}
