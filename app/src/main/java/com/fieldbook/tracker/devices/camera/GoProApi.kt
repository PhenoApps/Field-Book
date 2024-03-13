package com.fieldbook.tracker.devices.camera

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.fieldbook.tracker.interfaces.CollectController
import com.fieldbook.tracker.objects.RangeObject
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.utilities.WifiHelper
import dagger.hilt.android.qualifiers.ActivityContext
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
import java.net.URI
import javax.inject.Inject


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
        fun onImageRequestReady(bitmap: Bitmap, data: ImageRequestData)
    }

    companion object {
        const val TAG = "GoProApi"
        private const val ffmpegOutputUri = "udp://@localhost:8555"
    }

    private val gatt by lazy {
        GoProGatt(this)
    }

    private val controller by lazy {
        context as CollectController
    }

    private val httpClient by lazy {
        OkHttpClient()
    }

    private val loadControl: androidx.media3.exoplayer.DefaultLoadControl =
        androidx.media3.exoplayer.DefaultLoadControl.Builder()
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBufferDurationsMs(500, 1000, 500, 500)
            .build()

    private val mediaSource: androidx.media3.exoplayer.source.MediaSource =
        androidx.media3.exoplayer.source.ProgressiveMediaSource.Factory(
            androidx.media3.datasource.DefaultDataSource.Factory(context)
        ).createMediaSource(
            androidx.media3.common.MediaItem.fromUri(
                Uri.parse(ffmpegOutputUri)
            )
        )

    private val trackSelector: androidx.media3.exoplayer.trackselection.DefaultTrackSelector =
        androidx.media3.exoplayer.trackselection.DefaultTrackSelector(context)

    private val playerListener: Player.Listener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            super.onPlaybackStateChanged(playbackState)
            when (playbackState) {
                Player.STATE_IDLE, Player.STATE_ENDED -> Log.d(
                    TAG, "Player Idle/Ended"
                )

                Player.STATE_BUFFERING -> if (!streamStarted) {
                    Log.d(TAG, "Player Buffering")
                    Log.d(TAG, "Requesting start stream.")
                    streamStarted = true
                }

                Player.STATE_READY -> {
                    Log.d(TAG, "Player Ready")
                    callbacks?.onStreamReady()
                }
            }
        }
    }

    private var player: ExoPlayer? = null
    private var callbacks: Callbacks? = null

    var streamStarted = false

    fun requestStream() {

        //stop stream first, on fail or success start stream again:
        val stopPreview: Request = Request.Builder()
            .url(URI.create("http://10.5.5.9:8080/gopro/camera/stream/stop").toHttpUrlOrNull()!!)
            .build()

        httpClient.newCall(stopPreview).enqueue(object : Callback {

            override fun onFailure(call: okhttp3.Call, e: okio.IOException) {
                Log.e(TAG, "Request stop failed.")
                e.printStackTrace()
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
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

            override fun onFailure(call: okhttp3.Call, e: okio.IOException) {
                Log.e(TAG, "Failed to make network request to GoPro AP")
                e.printStackTrace()
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request response = not success ${response.code}")
                    controller.getFfmpegHelper().initRequestTimer()
                    callbacks?.onStreamRequested()
                } else {
                    Log.i(TAG, "Request response = success")
                    controller.getFfmpegHelper().initRequestTimer()
                    callbacks?.onStreamRequested()
                }
                response.close()
            }
        })
    }

    /**
     * http request to read media list (files on gopro device)
     */
    fun queryMedia(model: ImageRequestData) {

        Log.d(TAG, "Attempting media list query.")

        //stop stream first, on fail or success start stream again:
        val mediaQuery: Request = Request.Builder()
            .url(URI.create("http://10.5.5.9:8080/gopro/media/list").toHttpUrlOrNull()!!)
            .build()

        httpClient.newCall(mediaQuery).enqueue(object : Callback {

            override fun onFailure(call: okhttp3.Call, e: okio.IOException) {

                Log.e(TAG, "Media query failed.")

                e.printStackTrace()
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {

                if (!response.isSuccessful) {

                    Log.e(TAG, "Media query not success")

                } else {

                    parseMediaQueryResponse(response.body?.string() ?: "{}", model)

                    Log.i(TAG, "Media query success.")

                }

                response.close()
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

        httpClient.newCall(stopPreview).enqueue(object : Callback {

            override fun onFailure(call: okhttp3.Call, e: okio.IOException) {
                Log.e(TAG, "Request stop failed.")
                e.printStackTrace()
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {
                if (!response.isSuccessful) {
                    Log.e(TAG, "Request stop preview response = not success")
                } else {
                    Log.i(TAG, "Request stop preview response = success")
                }
                response.close()
            }
        })

        controller.getFfmpegHelper().cancel()

    }

    fun onDestroy() {

        stopStream()

        controller.getFfmpegHelper().cancel()

        controller.getWifiHelper().disconnect()

        gatt.clear()

    }

    fun onConnect(device: BluetoothDevice, callbacks: Callbacks) {

        this.callbacks = callbacks

        device.connectGatt(context, false, gatt.callback)

        callbacks.onInitializeGatt()
    }

    fun initialize() {
        //reset ui component states
        player?.stop()
        player?.release()
        player?.clearMediaItems()
        player?.clearVideoSurface()
        player = null
        //reset global flags
        this.streamStarted = false

    }

    fun isStreamStarted(): Boolean = streamStarted

    fun createPlayer(): ExoPlayer {

        //Max. Buffer: The maximum duration, in milliseconds, of the media the player is attempting to buffer. Once the buffer reaches Max Buffer, it will stop filling it up.
        //min Buffer: The minimum length of media that the player will ensure is buffered at all times, in milliseconds.
        //Playback Buffer: The default amount of time, in milliseconds, of media that needs to be buffered in order for playback to start or resume after a user action such as a seek.
        //Buffer for playback after rebuffer: The duration of the media that needs to be buffered in order for playback to continue after a rebuffer, in milliseconds.
        player?.stop()
        player?.release()
        player = null

        player = ExoPlayer.Builder(context)
            .setTrackSelector(trackSelector)
            .setLoadControl(loadControl)
            .build()

        player?.addListener(playerListener)
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
        model: ImageRequestData
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

                val fileName = files.getJSONObject(files.length() - 1).getString("n")

                requestFileUrl("http://10.5.5.9:8080/videos/DCIM/$dir/$fileName", model)

                break
//                println(files)
//
//                for (j in 0 until numFiles) {
//
//                    val file = files.getJSONObject(j)
//
//                    val fileName = file.getString("n")
//
//                    images.add(
//                        GoProImage(
//                            dir,
//                            fileName,
//                            file.getString("mod").toLong(),
//                            file.getString("s").toLong(),
//                            "http://10.5.5.9:8080/videos/DCIM/$dir/$fileName"
//                        )
//                    )
//                }
            }

            //val latest = images.maxBy { it.mod }.url

            //requestFileUrl(latest, model)

        } catch (e: JSONException) {

            e.printStackTrace()

        }
    }

    /**
     * requests the image at the given url, calls onReady interface when image is downloaded
     */
    private fun requestFileUrl(url: String, model: ImageRequestData) {

        Log.d(TAG, "Image request: $url")

        //stop stream first, on fail or success start stream again:
        val requestImage: Request = Request.Builder()
            .url(URI.create(url).toHttpUrlOrNull()!!)
            .build()

        httpClient.newCall(requestImage).enqueue(object : Callback {

            override fun onFailure(call: okhttp3.Call, e: okio.IOException) {

                Log.e(TAG, "Request image failed.")

                e.printStackTrace()
            }

            override fun onResponse(call: okhttp3.Call, response: Response) {

                if (!response.isSuccessful) {

                    Log.e(TAG, "Request image response = not success")

                } else {

                    Log.i(TAG, "Request image response = success")

                    response.body?.byteStream()?.use { inputStream ->

                        callbacks?.onImageRequestReady(
                            BitmapFactory.decodeStream(inputStream),
                            model
                        )

                    }
                }

                response.close()
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

    override fun onModelName(modelName: String) {
        if ("HERO11 Black" !in modelName) {
//            activity?.runOnUiThread {
//                Toast.makeText(
//                    context,
//                    activity?.getString(R.string.go_pro_layout_black_11_not_detected),
//                    Toast.LENGTH_LONG
//                ).show()
//            }
        }
    }

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

    override fun onNetworkBound() {

        callbacks?.onConnected()

    }
}