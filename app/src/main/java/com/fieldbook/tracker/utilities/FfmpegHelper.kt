package com.fieldbook.tracker.utilities

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.ReturnCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
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
    }

    private val scope = MainScope()

    private var ffmpegJob: Job? = null
    private var keepAliveJob: Job? = null

    private var udpSocket: DatagramSocket? = null

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
            ffmpegJob?.cancel()
            ffmpegJob = null
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
            FFmpegKit.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error during cancel: ${e.message}")
        }
    }

    /**
     * starts a background thread to send keep alive messages
     */
    fun initRequestTimer() {

        startFfmpegCommand()

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
            ffmpegJob = null
            FFmpegKit.cancel()
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping FFMPEG: ${e.message}")
        }
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
     * Runs the remux that turns the camera's udp stream into something ExoPlayer can read.
     *
     * ffmpeg is restarted if it exits. It returns immediately when it cannot identify the codec,
     * and the camera only begins sending once a keep alive lands, so losing that race on the first
     * attempt is routine. The result used to be ignored entirely, which left the preview dead with
     * no indication of why.
     */
    private fun startFfmpegCommand() {

        stop()

        ffmpegJob = scope.launch {

            withContext(Dispatchers.IO) {

                var attempt = 0

                while (isActive && attempt < MAX_ATTEMPTS) {

                    attempt++

                    val command = streamCommand()

                    Log.d(TAG, "Executing FFMPEG Kit (attempt $attempt): $command")

                    val session = try {
                        FFmpegKit.execute(command)
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