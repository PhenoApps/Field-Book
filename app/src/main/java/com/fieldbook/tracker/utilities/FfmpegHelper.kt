package com.fieldbook.tracker.utilities

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
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
     * Starts FFMPEG background coroutine that creates udp substream for Android/Exoplayer to interpret.
     */
    private fun startFfmpegCommand() {

        stop()

        ffmpegJob = scope.launch {

            withContext(Dispatchers.IO) {

                val streamInputUri = "udp://:8554" // maybe different depending on gopro modelID?

                val command =
                    "-fflags nobuffer -flags low_delay -f:v mpegts -an -probesize 100000 -i $streamInputUri -f mpegts -vcodec copy udp://localhost:8555?pkt_size=1316" // -probesize 100000 is minimum for Hero 10

                Log.d(TAG, "Executing FFMPEG Kit: $command")

                FFmpegKit.execute(command)

            }
        }
    }
}