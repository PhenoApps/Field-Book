package com.fieldbook.tracker.devices.camera

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.net.Network
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.UdpDataSource
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
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

        /** True once the session owns a preview player, whether or not frames have arrived yet. */
        val hasPlayer: Boolean
            get() = this == CONNECTED || this == STREAMING || this == CAPTURING
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

        /**
         * Budget for the whole bluetooth handshake. Generous on purpose: phenolib's credential
         * sequence alone spends about ten seconds in fixed delays after service discovery, on top
         * of connection and discovery, so a tighter budget tears down links that were about to
         * succeed.
         */
        private const val BLE_CONNECT_TIMEOUT_MS = 45000L
        private const val BLE_DISCONNECT_TIMEOUT_MS = 800L

        //how long to wait for the first preview frame before declaring the session failed
        private const val STREAM_READY_TIMEOUT_MS = 30000L

        //settle time between clearing a stuck preview stream and asking for a new one
        private const val STREAM_RECOVERY_DELAY_MS = 600L

        //Live preview buffering. Every millisecond held here is visible lag, so the window is kept
        //small and, more importantly, capped: the old 5s maximum let latency grow and never shrink.
        private const val MIN_BUFFER_MS = 250
        private const val MAX_BUFFER_MS = 1000
        private const val BUFFER_FOR_PLAYBACK_MS = 150
        private const val BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS = 250

        //must outlast a photo download, otherwise the player tears its socket down mid capture
        private const val UDP_SOCKET_TIMEOUT_MS = 30000

        //the player opens the udp socket before the camera has sent anything, so early failures
        //are expected and retried rather than fatal. The retries have to keep going for as long as
        //ffmpeg is still retrying its own side, otherwise the player has given up by the time a
        //later ffmpeg attempt finally produces output.
        private const val MAX_PLAYER_RETRIES = 9
        private const val PLAYER_RETRY_DELAY_MS = 3000L

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

                    streamWatchdog?.cancel()
                    streamWatchdog = null
                    playerRetries = 0

                    if (connectionState != ConnectionState.CAPTURING) {
                        setState(ConnectionState.STREAMING)
                    }

                    callbacks?.onStreamReady()
                }
            }
        }

        /**
         * The player is opened as soon as ffmpeg is launched, so it routinely reaches the udp
         * socket before the camera has sent anything: the camera only starts streaming once a keep
         * alive lands, and those are on a five second cadence. media3 gives up on the source after
         * a few seconds and parks in STATE_IDLE, and nothing used to re-prepare it - the preview
         * then never arrived and the connect flow sat on its spinner indefinitely.
         */
        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)

            Log.e(TAG, "Player error (${error.errorCodeName})", error)

            //Retry for as long as the session owns a player, not just while the first frame is
            //still awaited. A preview that dies after it has started - which is exactly what a
            //capture can cause - otherwise stays dead for the rest of the session.
            if (!connectionState.hasPlayer) return

            if (playerRetries >= MAX_PLAYER_RETRIES) {
                Log.e(TAG, "Giving up on preview after $playerRetries retries")
                return
            }

            playerRetries++

            ioScope.launch {
                delay(PLAYER_RETRY_DELAY_MS)
                withContext(Dispatchers.Main) {
                    if (connectionState.hasPlayer) {
                        Log.d(TAG, "Re-preparing preview, attempt $playerRetries")
                        player?.prepare()
                    }
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

    private var stateChangedAt = 0L

    /** Fails the session if the first preview frame never arrives. */
    private var streamWatchdog: Job? = null
    private var playerRetries = 0

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

        //elapsed time per stage makes a stalled connect readable straight from logcat
        val now = SystemClock.elapsedRealtime()
        val sinceLast = if (stateChangedAt == 0L) 0L else now - stateChangedAt
        stateChangedAt = now

        Log.d(TAG, "Connection state: $connectionState -> $state (+${sinceLast}ms)")

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

            //if the camera was switched off, a teardown is likely still in flight. Let it finish,
            //otherwise its tail unbinds the network and resets the state of the session we are
            //about to start, and the reconnect silently fails.
            shutdownJob?.takeIf { it.isActive }?.join()

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
        streamWatchdog?.cancel()
        streamWatchdog = null
        playerRetries = 0
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
                teardownAsync()
            }
        }
    }

    private suspend fun startStreamSuspend() {

        Log.d(TAG, "Request stream start.")

        var started = requestStreamStart()

        if (!started) {
            //The camera refuses to start a preview while it believes one is already running, and
            //that belief survives a power cycle when the previous session was not closed cleanly.
            //Clearing it and asking again is the recovery. Without it the camera sends nothing at
            //all while ffmpeg and the player sit waiting on an empty socket.
            Log.w(TAG, "Start stream refused, clearing the existing stream and retrying")

            try {
                stopStreamSuspend()
            } catch (e: Exception) {
                Log.w(TAG, "Stop stream failed during recovery", e)
            }

            delay(STREAM_RECOVERY_DELAY_MS)

            started = requestStreamStart()
        }

        //never hand an unstarted stream to ffmpeg: it blocks on the socket forever rather than
        //exiting, so neither its own retries nor the player's would ever fire
        if (!started) {
            throw IOException("Camera refused to start the preview stream")
        }

        controller.getFfmpegHelper().initRequestTimer()

        callbacks?.onStreamRequested()

        armStreamWatchdog()
    }

    private suspend fun requestStreamStart(): Boolean =
        executeWithRetrySuspend(url("/gopro/camera/stream/start")).use {
            if (it.isSuccessful) {
                Log.i(TAG, "Start stream response = success")
                true
            } else {
                Log.e(TAG, "Start stream response = not success ${it.code}")
                false
            }
        }

    /**
     * Nothing else bounds the wait for the first preview frame. Without this the trait sits on its
     * "waiting for live preview" spinner forever whenever ffmpeg or the camera's stream fails to
     * come up, with no error and no way back to the connect button.
     */
    private fun armStreamWatchdog() {

        streamWatchdog?.cancel()

        streamWatchdog = ioScope.launch {

            delay(STREAM_READY_TIMEOUT_MS)

            if (connectionState == ConnectionState.CONNECTED) {
                Log.e(TAG, "No preview frame within ${STREAM_READY_TIMEOUT_MS}ms, failing session")
                setState(ConnectionState.ERROR, R.string.gopro_error_stream_timeout)
                teardownAsync()
            }
        }
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

    /** Returns false if the camera would not give the preview back, leaving the session broken. */
    private suspend fun restartPreview(): Boolean {
        try {
            stopStreamSuspend()
        } catch (e: Exception) {
            Log.w(TAG, "Stop stream failed while restarting preview", e)
        }
        return try {
            startStreamSuspend()
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restart preview", e)
            false
        }
    }

    /**
     * Returns the preview player, building it only once per session.
     *
     * Rebuilding it is what broke the preview after every capture: the ffmpeg output port is bound
     * by the player, and a replacement instance tried to bind it while the outgoing one was still
     * closing, which surfaced as EADDRINUSE. Reusing the instance also keeps the socket bound
     * across a capture, so the preview simply resumes when ffmpeg starts sending again.
     */
    fun createPlayer(): ExoPlayer {

        player?.let { existing ->

            //ENDED counts too: a live source that hit end of stream is just as stuck as an idle one
            if (existing.playbackState == Player.STATE_IDLE ||
                existing.playbackState == Player.STATE_ENDED
            ) {
                Log.d(TAG, "Re-preparing existing preview player")
                existing.prepare()
            }

            existing.playWhenReady = true

            return existing
        }

        val trackSelector = androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context)

        //Buffering is kept short on purpose. These are a live preview: anything the player holds
        //on to is latency the user sees between moving the camera and the screen catching up, and
        //a large maximum lets that gap grow without ever recovering.
        val loadControl = androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                BUFFER_FOR_PLAYBACK_MS,
                BUFFER_FOR_PLAYBACK_AFTER_REBUFFER_MS
            )
            .build()

        val renderersFactory = DefaultRenderersFactory(context)
            .setEnableDecoderFallback(true)
            .setMediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                if (mimeType == MimeTypes.VIDEO_MV_HEVC) emptyList() else MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
            }

        val created = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .setRenderersFactory(renderersFactory)
            .build()

        created.addListener(playerListener)

        //A udp source is built directly rather than going through DefaultDataSource so its socket
        //timeout can be raised. The default gives up after eight seconds, which a photo download
        //routinely exceeds, and the resulting teardown and rebind is what the preview used to trip
        //over on the way back.
        val dataSourceFactory = DataSource.Factory {
            UdpDataSource(UdpDataSource.DEFAULT_MAX_PACKET_SIZE, UDP_SOCKET_TIMEOUT_MS)
        }

        val mediaSource: androidx.media3.exoplayer.source.MediaSource =
            androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(
                    androidx.media3.common.MediaItem.fromUri(
                        ffmpegOutputUri.toUri()
                    )
                )

        created.setMediaSource(mediaSource)
        created.playWhenReady = true
        created.prepare()

        player = created

        return created
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
                    if (restartPreview()) {
                        setState(ConnectionState.STREAMING)
                    } else {
                        //do not report a live session over a preview the camera refused to give
                        //back: that reads as working while nothing updates on screen
                        setState(ConnectionState.ERROR, R.string.gopro_error_stream_failed)
                        teardownAsync()
                    }
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

            val ssid = gatt.ssid
            val pass = gatt.password

            //the ssid must match what the camera shows under Connections > Camera Info exactly.
            //Log the length rather than the passphrase itself so a blank or truncated read is
            //still diagnosable from a bug report.
            Log.d(
                TAG,
                "Credentials acquired: ssid=[$ssid] passphrase length=${pass?.length ?: 0}"
            )

            if (ssid.isNullOrBlank() || pass.isNullOrBlank()) {
                Log.e(TAG, "Camera did not report usable wifi credentials")
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
