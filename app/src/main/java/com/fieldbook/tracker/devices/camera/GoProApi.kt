package com.fieldbook.tracker.devices.camera

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.graphics.Bitmap
import android.net.Network
import android.os.Build
import android.os.SystemClock
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.core.net.toUri
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.UdpDataSource
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
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
        val created: Long,
        val mod: Long,
        val byteSize: Long,
        val url: String,
        val cameraSerial: String? = null
    ) {
        /**
         * Value saved for a file kept on the camera, written like a path under the camera's serial.
         * File numbering restarts when a card is formatted and is identical across cameras, so the
         * serial is what makes the name unique.
         */
        val observationName: String
            get() = cameraSerial?.let { "$it/$fileName" } ?: fileName
    }

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
        RECORDING,

        /** Links kept open with the preview stopped while the user is on another trait. */
        PARKED,
        DISCONNECTING,
        ERROR;

        /** True while a session is being established or is live. */
        val isActive: Boolean
            get() = this != DISCONNECTED && this != DISCONNECTING && this != ERROR

        /** True once the session owns a preview player, whether or not frames have arrived yet. */
        val hasPlayer: Boolean
            get() = this == CONNECTED || this == STREAMING || this == CAPTURING || this == RECORDING
    }

    interface Callbacks {
        fun onConnectionStateChanged(state: ConnectionState, @StringRes messageRes: Int?)
        fun onInitializeGatt()
        fun onConnected()
        fun onStreamReady()
        fun onStreamRequested()
        fun onPreviewFrame(frame: Bitmap)
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
        IS_ENCODING("10"),
        PHOTO_COUNT("38")
    }

    /** One read of the camera state, [photoCount] is null if the camera did not report it. */
    private data class CameraState(val busy: Int, val encoding: Int, val photoCount: Int?) {
        val isBusy get() = busy == 1 || encoding == 1
    }

    companion object {
        const val TAG = "GoProApi"
        private const val ffmpegOutputUri = "udp://@localhost:8555"
        private const val BASE_URL = "http://10.5.5.9:8080"

        private const val REQUEST_TIMEOUT_MS = 4000L
        private const val MAX_REQUEST_RETRIES = 3

        //the timeout covers the whole body, and the media list of a full card runs to megabytes
        private const val MEDIA_LIST_TIMEOUT_MS = 15000L

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

        //https://gopro.github.io/OpenGoPro/http#tag/Presets/operation/OGP_PRESET_SET_GROUP
        private const val PRESET_GROUP_VIDEO = 1000
        private const val PRESET_GROUP_PHOTO = 1001

        //how long to wait for the camera to report it is encoding once the shutter is pressed
        private const val RECORD_START_TIMEOUT_MS = 5000L

        //budget for stopping a recording and saving its name before the session is parked or
        //torn down
        private const val RECORD_STOP_TIMEOUT_MS = 30000L

        //how long a parked session survives while the user is on other traits
        private const val PARK_TIMEOUT_MS = 5 * 60 * 1000L

        //Open GoPro recommends a keep alive every three seconds, otherwise auto power down can
        //put the camera to sleep in the middle of a session
        private const val KEEP_ALIVE_INTERVAL_MS = 3000L

        //player failures that mean the device cannot decode the stream, not that it is late
        private val DECODER_ERROR_CODES = setOf(
            PlaybackException.ERROR_CODE_DECODER_INIT_FAILED,
            PlaybackException.ERROR_CODE_DECODER_QUERY_FAILED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
            PlaybackException.ERROR_CODE_DECODING_FORMAT_EXCEEDS_CAPABILITIES
        )
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

                    //a preview restored by a capture that finished after the session was parked
                    if (!connectionState.hasPlayer || transcodePreview) return

                    streamWatchdog?.cancel()
                    streamWatchdog = null
                    playerRetries = 0

                    //only the first frame makes a session live. The preview also comes back ready
                    //mid session, e.g. when the camera starts recording, and that must not drop a
                    //recording or capture back to streaming while the camera is still busy
                    if (connectionState == ConnectionState.CONNECTED) {
                        setState(ConnectionState.STREAMING)
                    }

                    callbacks?.onStreamReady()
                }
            }
        }

        /**
         * A video track no decoder on the device can play is reported here rather than as an
         * error: the player selects nothing and goes straight to STATE_ENDED. The HERO13 sends an
         * HEVC preview and the Boox Palma has every HEVC decoder disabled, which used to leave the
         * connect flow waiting on the stream watchdog.
         */
        override fun onTracksChanged(tracks: Tracks) {
            super.onTracksChanged(tracks)

            val undecodable = tracks.groups.firstOrNull {
                it.type == C.TRACK_TYPE_VIDEO && !it.isSupported(true)
            } ?: return

            switchToTranscodedPreview("no decoder for ${undecodable.getTrackFormat(0).sampleMimeType}")
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

            //a decoder that exists but cannot handle the stream, typically its resolution
            if (error.errorCode in DECODER_ERROR_CODES) {
                switchToTranscodedPreview(error.errorCodeName)
                return
            }

            //Retry for as long as the session owns a player, not just while the first frame is
            //still awaited. A preview that dies after it has started - which is exactly what a
            //capture can cause - otherwise stays dead for the rest of the session.
            if (!connectionState.hasPlayer || transcodePreview) return

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
    private var keepAliveJob: Job? = null

    private var parkJob: Job? = null
    private var parkTimeoutJob: Job? = null
    private var resumeJob: Job? = null
    private var switchJob: Job? = null

    /** The camera network the session joined, checked on resume so a replaced one is not reused. */
    private var cameraNetwork: Network? = null

    private var stateChangedAt = 0L

    /** Fails the session if the first preview frame never arrives. */
    private var streamWatchdog: Job? = null
    private var playerRetries = 0

    /**
     * Set once the player has shown it cannot decode the camera's stream. From then on, for the
     * rest of the session, ffmpeg decodes the preview and delivers it through
     * [Callbacks.onPreviewFrame] instead of the player.
     */
    @Volatile
    private var transcodePreview = false

    val isPreviewTranscoded: Boolean
        get() = transcodePreview

    /**
     * Urls of every file on the card the session has already accounted for: what was there when it
     * went live, plus everything seen by a capture since. Only a file outside this set can be
     * filed against a plot, see [GoProMediaSelector.newest]. Null until the card has been listed,
     * in which case a speculative harvest only takes the listing rather than filing anything.
     */
    @Volatile
    private var knownMedia: Set<String>? = null

    /** Entry/trait to attach the next photo to, kept current as the user navigates plots. */
    var currentEntry: ImageRequestData? = null

    /** Whether the session collects video rather than photos, fixed when it connects. */
    private var videoMode = false

    /** Entry the running recording is filed against, null while nothing is recording. */
    private var recordingEntry: ImageRequestData? = null

    /** Serial of the connected camera, reported over BLE before the wifi credentials. */
    @Volatile
    private var serialNumber: String? = null

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

            //withTimeout below cannot interrupt the blocking execute, so on its own a stalled
            //request ran until the client's 30 second read timeout. The call timeout is enforced
            //by okhttp itself and covers reading the body as well.
            call.timeout().timeout(timeoutMs, TimeUnit.MILLISECONDS)

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
    fun onConnect(device: BluetoothDevice, callbacks: Callbacks, videoMode: Boolean) {

        this.callbacks = callbacks
        this.videoMode = videoMode

        ioScope.launch {

            //if the camera was switched off, a teardown is likely still in flight. Let it finish,
            //otherwise its tail unbinds the network and resets the state of the session we are
            //about to start, and the reconnect silently fails.
            shutdownJob?.takeIf { it.isActive }?.join()

            //always start from a clean slate, a half open link is the usual reason a reconnect fails
            closeGatt()

            //a different camera or card, listed again once wifi is up
            knownMedia = null

            //never label this camera's files with the previous camera's serial
            serialNumber = null

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

        //A recording outlives the session on the camera, so it is stopped and its name saved
        //while the link can still carry the commands. Skipped after a failure, when that link is
        //already gone and the camera can only be stopped by hand.
        if (videoMode && connectionState != ConnectionState.ERROR) {
            withTimeoutOrNull(RECORD_STOP_TIMEOUT_MS) {

                //let a start or stop already in flight settle, cancelling it could leave the
                //camera recording or lose the name of a finished recording
                captureJob?.takeIf { it.isActive }?.join()

                if (recordingEntry != null) {
                    captureMutex.withLock { finishRecording(resumePreview = false) }
                }
            }
        }

        setState(ConnectionState.DISCONNECTING)

        parkJob?.cancel()
        parkTimeoutJob?.cancel()
        resumeJob?.cancel()
        switchJob?.cancel()

        try { captureJob?.cancel() } catch (_: Exception) {}
        streamWatchdog?.cancel()
        streamWatchdog = null
        playerRetries = 0
        stopPolling()
        stopKeepAlive()

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
        cameraNetwork = null

        withContext(Dispatchers.Main) {
            player?.removeListener(playerListener)
            player?.stop()
            player?.clearVideoSurface()
            player?.release()
            player = null
        }

        knownMedia = null
        currentEntry = null
        recordingEntry = null

        //the next session may be a different camera or device, so detect it again
        transcodePreview = false

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
     * Park region
     */

    /**
     * Called when the user leaves the trait. A live session keeps its bluetooth link and wifi
     * request but stops the preview and unbinds the process, so other traits are not stuck behind
     * the camera's access point. Coming back through [resume] then only restarts the preview
     * instead of repeating the whole handshake and the system connect dialog. A session that is
     * still connecting is torn down as before, and a recording is stopped and its name saved first.
     */
    fun park() {

        when (connectionState) {
            ConnectionState.STREAMING, ConnectionState.CAPTURING, ConnectionState.RECORDING -> Unit
            ConnectionState.PARKED -> return
            else -> {
                teardownAsync()
                return
            }
        }

        if (parkJob?.isActive == true) return

        parkJob = ioScope.launch {

            //let a capture in flight finish and restore the preview before it is stopped
            captureMutex.withLock {

                //A recording does not outlive the trait. Not cancellable, so a quick return to the
                //trait cannot lose the name, and the preview is restored so a park cancelled by
                //that return still leaves a live session.
                if (recordingEntry != null) {
                    withContext(NonCancellable) {
                        withTimeoutOrNull(RECORD_STOP_TIMEOUT_MS) {
                            finishRecording(resumePreview = true)
                        }
                    }
                }

                //the user came back while the recording was saved, the session stays live
                if (!isActive) return@launch

                if (connectionState != ConnectionState.STREAMING) return@launch

                //set first so player errors raised by the shutdown below are not retried
                setState(ConnectionState.PARKED)

                stopPolling()
                streamWatchdog?.cancel()
                streamWatchdog = null
                playerRetries = 0

                try {
                    stopStreamSuspend()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(TAG, "Stop stream request failed while parking", e)
                }

                controller.getFfmpegHelper().cancel()

                //stop rather than release: the instance is reused on resume, see createPlayer
                withContext(Dispatchers.Main) { player?.stop() }

                controller.getWifiHelper().releaseProcessBinding()

                armParkTimeout()
            }
        }
    }

    private fun armParkTimeout() {

        parkTimeoutJob?.cancel()

        parkTimeoutJob = ioScope.launch {

            delay(PARK_TIMEOUT_MS)

            if (connectionState == ConnectionState.PARKED) {
                Log.d(TAG, "Parked for ${PARK_TIMEOUT_MS}ms, releasing the camera")
                teardownAsync()
            }
        }
    }

    /**
     * Brings a parked session back when the user returns to the trait, through the same
     * [ConnectionState.CONNECTED] path a fresh connection takes once wifi is joined. Safe to call
     * on every layout load, it does nothing unless a park is pending or in place.
     *
     * @param videoMode the mode of the trait being returned to, which can differ from the one the
     * session was parked by when the user moves between a GoPro photo and a GoPro video trait
     */
    fun resume(videoMode: Boolean) {

        if (connectionState != ConnectionState.PARKED && parkJob?.isActive != true) return

        if (resumeJob?.isActive == true) return

        parkTimeoutJob?.cancel()
        parkTimeoutJob = null

        resumeJob = ioScope.launch {

            val modeChanged = this@GoProApi.videoMode != videoMode

            parkJob?.let { job ->
                //back before a pending park got past the capture in flight: keep the session,
                //unless it has to change mode, which the park and resume below take care of
                if (connectionState != ConnectionState.PARKED && !modeChanged) job.cancel()
                //a quick switch back can also land while the park is still stopping the stream
                job.join()
            }

            if (connectionState != ConnectionState.PARKED) return@launch

            val network = cameraNetwork

            if (network == null || !controller.getWifiHelper().restoreProcessBinding(network)) {
                Log.e(TAG, "Camera network gone while parked")
                setState(ConnectionState.ERROR, R.string.gopro_error_wifi_lost)
                teardownAsync()
                return@launch
            }

            Log.d(TAG, "Resuming parked session, video mode $videoMode")

            //the camera may also have been switched by hand while parked, so always reload it
            this@GoProApi.videoMode = videoMode
            loadPresetGroup()

            //files made on the camera while parked have no plot to be filed against
            listKnownMedia()

            setState(ConnectionState.CONNECTED)

            callbacks?.onConnected()
        }
    }

    /**
     * Called when the user moves straight from one GoPro trait to another. Every GoPro trait shares
     * one layout, so such a move never parks the session, and this does what the park and resume
     * would have: a recording is stopped and saved against the trait it started on, and the camera
     * is switched to the mode of the trait now on screen.
     */
    fun switchTrait(videoMode: Boolean) {

        //a parked session is switched by resume, a closed one picks the mode up when it connects
        if (!connectionState.isActive || connectionState == ConnectionState.PARKED) return

        switchJob = ioScope.launch {
            captureMutex.withLock {

                //not cancellable, as in park, so a teardown cannot lose the name
                if (recordingEntry != null) {
                    withContext(NonCancellable) {
                        withTimeoutOrNull(RECORD_STOP_TIMEOUT_MS) {
                            finishRecording(resumePreview = true)
                        }
                    }
                }

                if (this@GoProApi.videoMode != videoMode) {

                    Log.d(TAG, "Switching session to video mode $videoMode")

                    this@GoProApi.videoMode = videoMode

                    //before the network is bound, onNetworkBound loads the preset for the new mode.
                    //The poller is held off because the mode change raises the busy flag.
                    if (connectionState.hasPlayer) {
                        pollPaused = true
                        try {
                            loadPresetGroup()
                        } finally {
                            pollPaused = false
                        }
                    }
                }
            }
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

        startPreviewPipeline()

        callbacks?.onStreamRequested()

        armStreamWatchdog()
    }

    private fun startPreviewPipeline() {
        if (transcodePreview) {
            controller.getFfmpegHelper().initRequestTimer(::onPreviewFrame)
        } else {
            controller.getFfmpegHelper().initRequestTimer()
        }
    }

    /**
     * Moves the session to the transcoded preview. The player is stopped rather than released so
     * nothing else has to know it is unused, and ffmpeg is restarted to decode the stream itself.
     * Called on the player's thread.
     */
    private fun switchToTranscodedPreview(reason: String) {

        if (transcodePreview || !connectionState.hasPlayer) return

        Log.w(TAG, "Player cannot decode the preview ($reason), transcoding it instead")

        transcodePreview = true
        playerRetries = 0

        player?.stop()

        ioScope.launch {
            startPreviewPipeline()
            //decoding in software takes longer to produce a first frame than remuxing
            armStreamWatchdog()
        }
    }

    /**
     * A frame of the transcoded preview, on ffmpeg's reader thread. The first one plays the part
     * STATE_READY plays for the player.
     */
    private fun onPreviewFrame(frame: Bitmap) {

        if (!connectionState.hasPlayer) return

        if (connectionState == ConnectionState.CONNECTED) {

            streamWatchdog?.cancel()
            streamWatchdog = null

            setState(ConnectionState.STREAMING)

            callbacks?.onStreamReady()
        }

        callbacks?.onPreviewFrame(frame)
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
                //the camera may have been switched to video by hand, where the shutter would start
                //a recording that nothing stops
                loadPresetGroup()
                runCapture(data, saveImage, shutter = this@GoProApi::shutterOn)
            }
        }
    }

    /**
     * Handles a photo or video the user took with the camera's own shutter button: the capture
     * already happened, so only the media list and the transfer are needed.
     */
    private fun harvest(data: ImageRequestData, saveImage: Boolean) {

        if (captureMutex.isLocked) return

        captureJob = ioScope.launch {
            captureMutex.withLock {
                runCapture(data, saveImage, shutter = null)
            }
        }
    }

    /**
     * Starts recording a video for [data]. The recording is filed against the entry it started
     * on, the trait blocks plot navigation until [stopRecording].
     */
    fun startRecording(data: ImageRequestData) {

        if (!connectionState.isActive) {
            callbacks?.onCaptureFailed(R.string.gopro_error_not_connected)
            return
        }

        if (captureMutex.isLocked || connectionState == ConnectionState.RECORDING) {
            callbacks?.onCaptureFailed(R.string.gopro_error_record_busy)
            return
        }

        currentEntry = data

        captureJob = ioScope.launch {
            captureMutex.withLock {

                //keeps the poller from reading the recording's busy edge as a camera button capture
                pollPaused = true

                try {

                    //the camera may have been switched to photo by hand, where the shutter only
                    //takes a photo
                    loadPresetGroup()

                    shutterOn()

                    if (awaitEncoding()) {

                        recordingEntry = data

                        setState(ConnectionState.RECORDING)

                    } else {

                        //stop a recording that may have started too late to be confirmed rather
                        //than leave the camera running with nothing to stop it
                        shutterOff()

                        callbacks?.onCaptureFailed(R.string.gopro_error_record_failed)

                        //such a recording still leaves a short clip. List it as known so the
                        //poller does not file it as a capture made on the camera's own button.
                        awaitCaptureIdle()
                        listKnownMedia()
                    }

                } catch (e: CancellationException) {
                    throw e
                } catch (t: Throwable) {
                    Log.e(TAG, "Start recording failed", t)
                    callbacks?.onCaptureFailed(R.string.gopro_error_record_failed)
                } finally {
                    //resumed even while recording: the poller is what notices the recording
                    //being stopped on the camera's own button
                    pollPaused = false
                    if (recordingEntry == null) {
                        callbacks?.onCaptureFinished()
                    }
                }
            }
        }
    }

    /**
     * Stops the running recording and saves the name of its first chapter against the entry it
     * started on.
     */
    fun stopRecording() {

        if (connectionState != ConnectionState.RECORDING || captureMutex.isLocked) {
            callbacks?.onCaptureFailed(R.string.gopro_error_record_busy)
            return
        }

        captureJob = ioScope.launch {
            captureMutex.withLock {
                //the camera's own button may have ended it while this waited for the lock
                if (recordingEntry == null) callbacks?.onCaptureFinished()
                else finishRecording(resumePreview = true)
            }
        }
    }

    /**
     * Treats a recording started on the camera's own button like one started in the app: it is
     * filed against the current entry, and the trait shows stop and blocks navigation until it
     * ends.
     */
    private fun adoptCameraRecording() {

        val entry = currentEntry ?: return

        if (captureMutex.isLocked || recordingEntry != null) return

        Log.d(TAG, "Recording started on the camera for ${entry.range.uniqueId}")

        recordingEntry = entry

        setState(ConnectionState.RECORDING)
    }

    /**
     * Saves a recording that was stopped on the camera's own button. The camera has already
     * finished writing, so there is no shutter to press or wait on.
     */
    private fun finishCameraRecording() {

        if (captureMutex.isLocked) return

        captureJob = ioScope.launch {
            captureMutex.withLock { finishRecording(resumePreview = true, pressShutter = false) }
        }
    }

    /** Caller must hold [captureMutex]. */
    private suspend fun finishRecording(resumePreview: Boolean, pressShutter: Boolean = true) {

        val entry = recordingEntry ?: return

        recordingEntry = null

        runCapture(
            entry,
            saveImage = false,
            shutter = if (pressShutter) this::shutterOff else null,
            resumePreview = resumePreview,
            reportFailures = true
        )
    }

    /**
     * Polls the camera until it reports it is encoding, which confirms a recording has started.
     */
    private suspend fun awaitEncoding(): Boolean {

        val start = System.currentTimeMillis()

        while (System.currentTimeMillis() - start < RECORD_START_TIMEOUT_MS) {

            val state = fetchBusyState()

            if (state != null) {

                callbacks?.onBusyStateChanged(state.busy, state.encoding)

                if (state.encoding == 1) return true
            }

            delay(CAPTURE_POLL_INTERVAL_MS)
        }

        return false
    }

    /**
     * Presses [shutter], waits for the camera to finish writing and then saves the new file:
     * shutter on takes a photo, shutter off ends a recording, and no shutter collects a capture
     * already made on the camera.
     *
     * @param resumePreview false while tearing down, when the preview is about to be released
     * @param reportFailures a harvest is speculative, so by default only a capture the user pressed
     * the shutter for reports failures. A recording the user is watching reports them either way.
     */
    private suspend fun runCapture(
        data: ImageRequestData,
        saveImage: Boolean,
        shutter: (() -> Unit)?,
        resumePreview: Boolean = true,
        reportFailures: Boolean = shutter != null
    ) {

        setState(ConnectionState.CAPTURING)

        pollPaused = true

        val fail: (Int) -> Unit = { messageRes ->
            Log.w(TAG, "Capture unsuccessful: ${context.getString(messageRes)}")
            if (reportFailures) {
                callbacks?.onCaptureFailed(messageRes)
            }
        }

        try {

            if (shutter != null) {

                shutter()

                if (!awaitCaptureIdle()) {
                    fail(R.string.gopro_error_capture_timeout)
                    return
                }
            }

            val media = queryMedia()

            val known = knownMedia

            val latest = media?.let { GoProMediaSelector.newest(it, known.orEmpty(), videoMode) }

            //everything on the card has now been seen, so none of it can be filed by a later capture
            if (media != null) knownMedia = media.mapTo(HashSet()) { it.url }

            when {
                latest == null -> {
                    fail(if (videoMode) R.string.gopro_error_no_new_video else R.string.gopro_error_no_new_photo)
                }

                //a harvest is only a guess that something was captured, and with no earlier listing
                //to compare against, the newest file may well be from another session
                known == null && !reportFailures -> {
                    Log.d(TAG, "No earlier media listing, not filing ${latest.fileName}")
                }

                else -> {
                    //videos are never copied off the camera, only their name is kept
                    if (saveImage && !videoMode) {
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
                if (resumePreview && connectionState == ConnectionState.CAPTURING) {
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

                callbacks?.onBusyStateChanged(state.busy, state.encoding)

                if (state.isBusy) {
                    sawBusy = true
                } else if (sawBusy || System.currentTimeMillis() - start > CAPTURE_BUSY_WINDOW_MS) {
                    return true
                }
            }

            delay(CAPTURE_POLL_INTERVAL_MS)
        }

        return false
    }

    private suspend fun fetchBusyState(): CameraState? {

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

    private fun parseState(responseBody: String): CameraState? {
        return try {
            val state = JSONObject(responseBody).getJSONObject("status")
            CameraState(
                state.getInt(GoProStateKeys.BUSY.key),
                state.getInt(GoProStateKeys.IS_ENCODING.key),
                state.optInt(GoProStateKeys.PHOTO_COUNT.key, -1).takeIf { it >= 0 }
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
            var lastPhotoCount: Int? = null

            while (isActive) {

                if (pollPaused || !connectionState.isActive) {

                    //drop the edge and count we were tracking, otherwise resuming after an app
                    //driven capture looks like a fresh photo taken on the camera
                    wasBusy = false
                    lastPhotoCount = null

                } else {

                    val state = fetchBusyState()

                    if (state != null) {

                        callbacks?.onBusyStateChanged(state.busy, state.encoding)

                        //a single photo is usually written well inside one poll interval, so the
                        //busy flag alone misses most shutter presses. A rising photo count does
                        //not, the busy -> idle edge stays as a fallback for cameras without it.
                        val countRose = lastPhotoCount != null && state.photoCount != null
                                && state.photoCount > lastPhotoCount
                        val busyEdge = wasBusy && !state.isBusy

                        when {

                            //the recording, started in the app or on the camera, was stopped on
                            //the camera's own button
                            videoMode && connectionState == ConnectionState.RECORDING -> {
                                if (!state.isBusy) finishCameraRecording()
                            }

                            //a recording was started on the camera's own button
                            videoMode && state.encoding == 1 &&
                                    connectionState == ConnectionState.STREAMING -> {
                                adoptCameraRecording()
                            }

                            //wait out a capture in progress so the media list already holds the
                            //photo. In video mode this catches a recording too short to be seen
                            //between polls.
                            (countRose || busyEdge) && !state.isBusy -> {
                                Log.d(TAG, "Camera shutter capture detected, count=${state.photoCount}")
                                currentEntry?.let { entry ->
                                    harvest(entry, entry.trait.saveImage)
                                }
                            }
                        }

                        wasBusy = state.isBusy

                        //hold the old count while busy so the rise is still seen once it settles
                        if (!state.isBusy) {
                            lastPhotoCount = state.photoCount
                        }
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
     * Keeps the camera awake for the whole session, parked included, since nothing else resets
     * its keep alive timer. Skipped during a capture: the shutter already resets auto power down,
     * and the download should have the access point to itself.
     */
    private fun startKeepAlive() {

        if (keepAliveJob?.isActive == true) return

        keepAliveJob = ioScope.launch {

            while (isActive) {

                if (!pollPaused && connectionState.isActive) {
                    try {
                        executeWithRetrySuspend(url("/gopro/camera/keep_alive"), retries = 1).use {
                            if (!it.isSuccessful) {
                                Log.w(TAG, "Keep alive response = not success ${it.code}")
                            }
                        }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        Log.w(TAG, "Keep alive request failed", e)
                    }
                }

                delay(KEEP_ALIVE_INTERVAL_MS)
            }
        }
    }

    private fun stopKeepAlive() {
        keepAliveJob?.cancel()
        keepAliveJob = null
    }

    /**
     * Media region
     */
    /** Returns every single file on the card, or null if the camera did not give a usable list. */
    private suspend fun queryMedia(): List<GoProImage>? {

        Log.d(TAG, "Attempting media list query.")

        val response = executeWithRetrySuspend(url("/gopro/media/list"), MEDIA_LIST_TIMEOUT_MS)

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
     * Records what is already on the card, so that only files made from here on can be filed
     * against a plot. Without it the first harvest of a session saved whatever was newest, which
     * could belong to another session or plot. Not fatal: captures fall back to the newest file.
     */
    private suspend fun listKnownMedia() {

        knownMedia = try {
            queryMedia()?.mapTo(HashSet()) { it.url }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Unable to list the media already on the card", e)
            null
        }
    }

    /**
     * Parses the media list, see [GoProMediaSelector] for how the file a capture produced is
     * picked out of it.
     *
     * Grouped media (burst, time lapse, night lapse) is skipped here for both. Group names do not
     * share the photo numbering - G0010008.JPG outranks GOPR0123.JPG - so a burst was attached to
     * every plot once one was on the card. That became easy to hit on the HERO13, whose photo
     * preset can be switched to burst.
     */
    private fun parseMediaQueryResponse(responseBody: String): List<GoProImage>? {

        return try {

            val mediaArray = JSONObject(responseBody).getJSONArray("media")

            val images = arrayListOf<GoProImage>()

            val serial = serialNumber

            for (i in 0 until mediaArray.length()) {

                val media = mediaArray.getJSONObject(i)

                val dir = media.getString("d")

                val files = media.getJSONArray("fs")

                for (j in 0 until files.length()) {

                    val file = files.getJSONObject(j)

                    val fileName = file.getString("n")

                    //group items carry a group id, single photos and videos do not
                    if (file.has("g")) continue

                    val mod = file.optString("mod").toLongOrNull() ?: 0L

                    images.add(
                        GoProImage(
                            dir,
                            fileName,
                            file.optString("cre").toLongOrNull() ?: mod,
                            mod,
                            file.optString("s").toLongOrNull() ?: 0L,
                            "$BASE_URL/videos/DCIM/$dir/$fileName",
                            serial
                        )
                    )
                }
            }

            images

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
            knownMedia = knownMedia?.minus(model.url)
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

    override fun onSerialNumber(serialNumber: String) {
        this.serialNumber = serialNumber.trim().takeIf { it.isNotEmpty() }
    }

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

        cameraNetwork = network

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

            startKeepAlive()

            loadPresetGroup()

            listKnownMedia()

            setState(ConnectionState.CONNECTED)

            callbacks?.onConnected()
        }
    }

    /**
     * Puts the camera in the mode the trait collects, before the preview starts. A camera left in
     * the other mode produces the wrong kind of file and the capture finds nothing new. Not fatal:
     * the mode can still be switched on the camera by hand.
     */
    private suspend fun loadPresetGroup() {

        val group = if (videoMode) PRESET_GROUP_VIDEO else PRESET_GROUP_PHOTO

        try {

            executeWithRetrySuspend(url("/gopro/camera/presets/set_group?id=$group"), retries = 1).use {
                if (!it.isSuccessful) {
                    Log.w(TAG, "Camera refused preset group $group with ${it.code}")
                }
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load preset group $group", e)
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
