package com.fieldbook.tracker.utilities

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFmpegSession
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import javax.inject.Inject

class FfmpegHelper @Inject constructor() {

    companion object {
        const val TAG = "FFMPEG"

        const val KEEP_ALIVE_MESSAGE_PACKET_DELAY = 5000L
        const val UDP_SOCKET_TIMEOUT = 60000L

        /**
         * Socket timeout in microseconds. Without it ffmpeg blocks on the socket indefinitely when
         * the camera sends nothing, so it never exits and the retry below never gets a chance to
         * run. The preview is torn down and rebuilt around each capture anyway, so a generous
         * value cannot cut a healthy stream short.
         */
        private const val INPUT_TIMEOUT_US = 15000000

        private const val STREAM_INPUT_URI = "udp://:8554?timeout=$INPUT_TIMEOUT_US"
        private const val STREAM_OUTPUT_URI = "udp://localhost:8555?pkt_size=1316"

        /**
         * Deliberately modest. The failure mode is missing the stream's program map table when
         * joining part way through, and no probe window fixes that - it only decides how long
         * ffmpeg waits before admitting it. A short window fails in a second or two and lets the
         * retry below rejoin the stream, and each rejoin is a fresh chance at the table. A large
         * window instead stalls in a single doomed attempt until the session watchdog kills it.
         */
        private const val PROBE_SIZE = 500000
        private const val ANALYZE_DURATION = 3000000

        //quick successive attempts beat one long one, and must fit inside the session watchdog
        private const val MAX_ATTEMPTS = 8
        private const val RETRY_DELAY = 1000L

        //Transcoded preview. Every frame is still decoded in software, these only bound the cost
        //of scaling, encoding and drawing it, which is plenty for an e-ink panel.
        private const val TRANSCODE_FPS = 6
        private const val TRANSCODE_WIDTH = 640
        private const val TRANSCODE_QUALITY = 7

        //larger kernel receive buffer so a decoder that briefly falls behind drops fewer packets
        private const val TRANSCODE_RECEIVE_BUFFER = 4194304

        //Explicitly IPv4. InetAddress.getLoopbackAddress() is ::1 on Android, and a server bound
        //there refuses ffmpeg's connection to 127.0.0.1.
        private const val FRAME_SERVER_HOST = "127.0.0.1"

        //how long a restart waits for the session it replaced to let go of the camera's port
        private const val EXIT_WAIT_MS = 1000L
        private const val EXIT_WAIT_ATTEMPTS = 3
    }

    private val scope = MainScope()

    private var ffmpegJob: Job? = null
    private var keepAliveJob: Job? = null

    private var udpSocket: DatagramSocket? = null

    /** The session currently or most recently executing, kept so it can be cancelled by id. */
    @Volatile
    private var session: FFmpegSession? = null

    /** Loopback server the transcoded preview is written to, null while remuxing. */
    private var frameServer: ServerSocket? = null
    private var frameJob: Job? = null

    /**
     * Stops the keep alive datagrams without touching the socket or the ffmpeg process.
     *
     * Used while a photo is downloaded from the camera so the transfer does not compete with the
     * preview traffic. [cancel] is deliberately not used here: it closes the socket bound to port
     * 8554 and rebinding immediately afterwards races the socket teardown.
     */
    fun pauseKeepAlive() {
        try {
            keepAliveJob?.cancel()
            keepAliveJob = null
        } catch (_: Exception) {}
    }

    /**
     * Restarts the keep alive loop after [pauseKeepAlive]. No-op if the socket is gone, in which
     * case [initRequestTimer] will rebuild everything when the stream is requested again.
     */
    fun resumeKeepAlive() {
        if (keepAliveJob != null) return
        val socket = udpSocket ?: return
        keepAliveJob = startKeepAliveLoop(socket)
    }

    fun cancel() {

        try {
            //cancelled but kept, the next start joins it so it does not race the old process
            ffmpegJob?.cancel()
        } catch (_: Exception) {}

        try {
            keepAliveJob?.cancel()
            keepAliveJob = null
        } catch (_: Exception) {}

        udpSocket?.let {
            try {
                // allow reuse (best-effort)
                it.reuseAddress = true
            } catch (_: Exception) {}
            try {
                it.disconnect()
            } catch (e: Exception) {
                Log.w(TAG, "Error disconnecting UDP socket: ${e.message}")
            }
            try {
                it.close()
            } catch (e: Exception) {
                Log.w(TAG, "Error closing UDP socket: ${e.message}")
            }
        }

        udpSocket = null

        try {
            cancelSession()
        } catch (e: Exception) {
            Log.w(TAG, "Error during cancel: ${e.message}")
        }

        closeFrameServer()
    }

    /**
     * Starts the preview pipeline and a background thread to send keep alive messages.
     *
     * With [frameListener] null the camera's stream is remuxed for ExoPlayer. Otherwise it is
     * decoded in software and handed back as bitmaps, for devices that have no decoder for the
     * stream - the HERO13 sends HEVC, and some devices such as the Boox Palma ship with every
     * HEVC decoder disabled.
     */
    fun initRequestTimer(frameListener: ((Bitmap) -> Unit)? = null) {

        startFfmpegCommand(frameListener)

        try {

            //stop the old loop before its socket is pulled out from under it, otherwise it spends
            //the gap sending on a closed socket and logging the failures
            keepAliveJob?.cancel()
            keepAliveJob = null

            //the previous socket is closed here, so it must always be replaced: reusing the closed
            //instance made every keep alive after the first stream restart fail silently
            udpSocket?.let {
                try { it.disconnect() } catch (_: Exception) {}
                try { it.close() } catch (_: Exception) {}
            }

            udpSocket = try {
                DatagramSocket().also {
                    it.reuseAddress = true
                    it.soTimeout = UDP_SOCKET_TIMEOUT.toInt()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Unable to open keep alive socket", e)
                null
            }

            try {
                udpSocket?.bind(InetSocketAddress(8554))
            } catch (ignore: Exception) {
            }

            keepAliveJob?.cancel()

            keepAliveJob = udpSocket?.let { startKeepAliveLoop(it) }

            Log.i(TAG, "requestTimer init successfully")

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }

    private fun startKeepAliveLoop(socket: DatagramSocket): Job {

        val keepStreamAliveData = "_GPHD_:1:0:2:0.000000\n".toByteArray()

        return scope.launch {

            withContext(Dispatchers.IO) {

                val inetAddress = try {
                    InetAddress.getByName("10.5.5.9")
                } catch (e: Exception) {
                    Log.w(TAG, "Unable to resolve camera address", e)
                    return@withContext
                }

                while (true) {

                    try {

                        val keepStreamAlivePacket = DatagramPacket(
                            keepStreamAliveData,
                            keepStreamAliveData.size,
                            inetAddress,
                            8554
                        )

                        socket.send(keepStreamAlivePacket)

                        Log.i(TAG, "Keep Alive sent")

                    } catch (e: Exception) {

                        e.printStackTrace()

                    }

                    delay(KEEP_ALIVE_MESSAGE_PACKET_DELAY)
                }
            }
        }
    }

    fun stop() {
        try {
            ffmpegJob?.cancel()
            cancelSession()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping FFMPEG: ${e.message}")
        }
        closeFrameServer()
    }

    /**
     * Cancels the running session by id as well as globally. The global cancel alone is a flag
     * that the next session clears when it starts, so a restart issued straight after it could
     * wipe the request before the old session saw it. The old session then kept port 8554 and
     * every new attempt failed with EADDRINUSE.
     */
    private fun cancelSession() {
        session?.let { FFmpegKit.cancel(it.sessionId) }
        FFmpegKit.cancel()
    }

    /**
     * Waits for a stopped session to really exit before another is started, since the camera's
     * port is only free once it has. The job only completes when ffmpeg returns, the cancel
     * itself is asynchronous.
     */
    private suspend fun awaitExit(job: Job?, session: FFmpegSession?) {

        job ?: return

        repeat(EXIT_WAIT_ATTEMPTS) {

            if (withTimeoutOrNull(EXIT_WAIT_MS) { job.join() } != null) return

            Log.w(TAG, "Previous FFMPEG still running, cancelling it again")

            session?.let { FFmpegKit.cancel(it.sessionId) }
        }

        Log.e(TAG, "Previous FFMPEG did not exit, starting the next one anyway")
    }

    private fun closeFrameServer() {
        frameJob?.cancel()
        frameJob = null
        //closing the server is what actually unblocks a reader waiting in accept
        try {
            frameServer?.close()
        } catch (_: Exception) {}
        frameServer = null
    }

    /**
     * Remuxes the camera's mpegts stream into something ExoPlayer can open.
     *
     * Whether this succeeds comes down to catching the stream's tables on the way in. Joining a
     * live udp stream part way through can miss the program map table, and the camera's stream
     * types are not ones ffmpeg recognises on their own, so the streams end up as "Unknown: none"
     * and it exits with "Output file does not contain any stream". That is a coin flip per attempt
     * rather than something a bigger probe window can fix, which is why the window is small and
     * the caller retries instead.
     */
    private fun streamCommand(): String =
        //Nothing is added on the input side beyond the probe limits. "-avioflags direct" turns off
        //buffering, which a packetised udp source needs, and "-fflags +discardcorrupt" throws away
        //any packet flagged with a transport error - over wifi that can include the program map
        //table, leaving ffmpeg with a program and no streams in it.
        "-f:v mpegts -an -probesize $PROBE_SIZE -analyzeduration $ANALYZE_DURATION " +
                "-i $STREAM_INPUT_URI " +
                //mpegts defaults to holding 0.7s of output back, which is pure added latency for a
                //preview. flush_packets pushes each packet out instead of filling a buffer first.
                //These are output side only and cannot affect how the input is probed.
                "-f mpegts -vcodec copy -muxdelay 0 -muxpreload 0 -flush_packets 1 " +
                STREAM_OUTPUT_URI

    /**
     * Decodes the camera's stream and re-encodes it as a sequence of jpegs on a loopback tcp
     * connection. MJPEG is used because this ffmpeg build has no H.264 encoder, and jpegs need no
     * platform video decoder at all to display.
     */
    private fun transcodeCommand(port: Int): String =
        "-f:v mpegts -an -probesize $PROBE_SIZE -analyzeduration $ANALYZE_DURATION " +
                //deblocking is the cheapest thing to drop from a software decode, and its
                //artifacts are invisible once the frame is scaled down
                "-skip_loop_filter all " +
                "-i $STREAM_INPUT_URI&buffer_size=$TRANSCODE_RECEIVE_BUFFER " +
                "-map 0:v:0 -vf fps=$TRANSCODE_FPS,scale=$TRANSCODE_WIDTH:-2 " +
                "-c:v mjpeg -pix_fmt yuvj420p -q:v $TRANSCODE_QUALITY -flush_packets 1 " +
                "-f mjpeg tcp://$FRAME_SERVER_HOST:$port?tcp_nodelay=1"

    /**
     * Opens the loopback server the transcoded preview is written to and decodes each jpeg that
     * arrives on it. ffmpeg reconnects on every retry, so connections are accepted until the
     * server is closed.
     */
    private fun openFrameServer(frameListener: (Bitmap) -> Unit): ServerSocket? {

        val server = try {
            ServerSocket(0, 1, InetAddress.getByName(FRAME_SERVER_HOST))
        } catch (e: IOException) {
            Log.e(TAG, "Unable to open preview frame server", e)
            return null
        }

        frameServer = server

        frameJob = scope.launch {

            withContext(Dispatchers.IO) {

                while (isActive) {

                    val connection = try {
                        server.accept()
                    } catch (e: IOException) {
                        break
                    }

                    try {
                        connection.use {
                            readFrames(BufferedInputStream(it.getInputStream()), frameListener)
                        }
                    } catch (e: IOException) {
                        Log.w(TAG, "Preview frame connection closed: ${e.message}")
                    }
                }
            }
        }

        return server
    }

    /**
     * Splits the stream into jpegs on their start and end of image markers. Neither can occur
     * inside the entropy coded data, where 0xFF is always stuffed, and ffmpeg embeds no thumbnail.
     */
    private fun readFrames(input: InputStream, frameListener: (Bitmap) -> Unit) {

        val options = BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.RGB_565
        }

        val frame = ByteArrayOutputStream(64 * 1024)
        var inFrame = false
        var previous = -1

        while (true) {

            val current = input.read()

            if (current < 0) return

            if (!inFrame) {
                if (previous == 0xFF && current == 0xD8) {
                    inFrame = true
                    frame.reset()
                    frame.write(0xFF)
                    frame.write(0xD8)
                }
            } else {
                frame.write(current)
                if (previous == 0xFF && current == 0xD9) {
                    inFrame = false
                    BitmapFactory.decodeByteArray(frame.toByteArray(), 0, frame.size(), options)
                        ?.let(frameListener)
                }
            }

            previous = current
        }
    }

    /**
     * Runs the remux that turns the camera's udp stream into something ExoPlayer can read, or the
     * transcode into bitmaps when [frameListener] is set.
     *
     * ffmpeg is restarted if it exits. It returns immediately when it cannot identify the codec,
     * and the camera only begins sending once a keep alive lands, so losing that race on the first
     * attempt is routine. The result used to be ignored entirely, which left the preview dead with
     * no indication of why.
     */
    private fun startFfmpegCommand(frameListener: ((Bitmap) -> Unit)? = null) {

        val previousJob = ffmpegJob
        val previousSession = session

        stop()

        val server = frameListener?.let { openFrameServer(it) ?: return }

        ffmpegJob = scope.launch {

            withContext(Dispatchers.IO) {

                awaitExit(previousJob, previousSession)

                var attempt = 0

                while (isActive && attempt < MAX_ATTEMPTS) {

                    attempt++

                    val command = server?.let { transcodeCommand(it.localPort) } ?: streamCommand()

                    Log.d(TAG, "Executing FFMPEG Kit (attempt $attempt): $command")

                    val session = try {
                        //created up front rather than through FFmpegKit.execute so its id is
                        //known while it runs, which is what lets stop() cancel this one session
                        FFmpegSession.create(FFmpegKitConfig.parseArguments(command)).also {
                            this@FfmpegHelper.session = it
                            FFmpegKitConfig.ffmpegExecute(it)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "FFMPEG execution threw", e)
                        null
                    }

                    if (!isActive) break

                    val returnCode = session?.returnCode

                    if (returnCode != null && ReturnCode.isCancel(returnCode)) {
                        Log.d(TAG, "FFMPEG cancelled")
                        break
                    }

                    Log.w(
                        TAG,
                        "FFMPEG exited on attempt $attempt with $returnCode, " +
                                "retrying in ${RETRY_DELAY}ms"
                    )

                    session?.failStackTrace?.takeIf { it.isNotBlank() }?.let {
                        Log.w(TAG, "FFMPEG failure: $it")
                    }

                    delay(RETRY_DELAY)
                }

                if (attempt >= MAX_ATTEMPTS) {
                    Log.e(TAG, "FFMPEG gave up after $attempt attempts")
                }
            }
        }
    }
}