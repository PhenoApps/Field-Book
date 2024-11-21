package com.fieldbook.tracker.utilities

import android.util.Log
import com.arthenica.ffmpegkit.FFmpegKit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.phenoapps.fragments.gopro.GoProHelper
import java.lang.Exception
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import javax.inject.Inject

class FfmpegHelper @Inject constructor() {

    private val scope = MainScope()

    private var ffmpegJob: Job? = null
    private var keepAliveJob: Job? = null

    private var udpSocket: DatagramSocket? = null

    fun cancel() {

        ffmpegJob?.cancel()

        keepAliveJob?.cancel()

        udpSocket?.disconnect()

        udpSocket?.close()

        FFmpegKit.cancel()
    }

    /**
     * starts a background thread to send keep alive messages
     */
    fun initRequestTimer() {

        startFfmpegCommand()

        val keepStreamAliveData = "_GPHD_:1:0:2:0.000000\n".toByteArray()

        try {

            val inetAddress = InetAddress.getByName("10.5.5.9")

            try {

                udpSocket?.disconnect()

                if (udpSocket == null) {
                    udpSocket = DatagramSocket().also {
                        it.reuseAddress = true
                        it.soTimeout = GoProHelper.UDP_SOCKET_TIMEOUT.toInt()
                    }
                }

                udpSocket?.bind(InetSocketAddress(8554))

            } catch (ignore: Exception) {
            }

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

                            Log.i(GoProHelper.TAG, "Keep Alive sent")

                        } catch (e: Exception) {

                            e.printStackTrace()

                        }

                        delay(GoProHelper.KEEP_ALIVE_MESSAGE_PACKET_DELAY)
                    }
                }
            }

            Log.i(GoProHelper.TAG, "requestTimer init successfully")

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }

    fun stop() {

        ffmpegJob?.cancel()

        FFmpegKit.cancel()
    }

    /**
     * Starts FFMPEG background coroutine that creates udp substream for Android/Exoplayer to interpret.
     */
    private fun startFfmpegCommand() {

        stop()

        scope.launch {

            withContext(Dispatchers.IO) {

                val streamInputUri = "udp://:8554" // maybe different depending on gopro modelID?

                val command =
                    "-fflags nobuffer -flags low_delay -f:v mpegts -an -probesize 100000 -i $streamInputUri -f mpegts -vcodec copy udp://localhost:8555?pkt_size=1316" // -probesize 100000 is minimum for Hero 10

                Log.d(GoProHelper.TAG, "Executing FFMPEG Kit: $command")

                FFmpegKit.execute(command)

            }
        }
    }
}