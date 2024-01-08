package com.fieldbook.tracker.canon

import android.graphics.Bitmap
import android.util.Log
import com.fieldbook.tracker.canon.models.DeviceInformation
import com.fieldbook.tracker.canon.models.LiveViewSettings
import com.fieldbook.tracker.canon.models.MovieMode
import com.fieldbook.tracker.canon.models.ShutterAction
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.ConnectException

class Controller(private val api: CameraControlApi) {

    companion object {
        const val TAG = "CanonController"
        private const val NUMBER_OF_CONNECTION_ATTEMPTS = 32
    }

    interface ControllerBridge {
        fun onConnected()
        fun onStartCaptureUi()
        fun onReceiveStreamImage(bmp: Bitmap)
        fun onFail()
        fun saveBitmap(bmp: Bitmap)
    }
    
    private var scope = CoroutineScope(Dispatchers.IO)

    fun establishStream(bridge: ControllerBridge) {

        scope.launch(Dispatchers.IO) {

            var response: DeviceInformation? = null

            for (i in 0..NUMBER_OF_CONNECTION_ATTEMPTS) {

                response = getDeviceInformation()

                if (response != null) break

                delay(1000L)
            }

            withContext(Dispatchers.Main) {

                if (response == null) bridge.onFail()
                else bridge.onConnected()

            }

            if (response != null) {

                switchCameraToStillCapture(bridge)
            }
        }
    }

    private suspend fun switchCameraToStillCapture(bridge: ControllerBridge) {

        if (postMovieModeOff() == null) {

            withContext(Dispatchers.Main) {

                bridge.onFail()
            }

        } else switchCameraPreviewOn(bridge)

        delay(1000L)
    }

    private suspend fun switchCameraPreviewOn(bridge: ControllerBridge) {

        if (postLiveViewSettings() == null) {

            withContext(Dispatchers.Main) {

                bridge.onFail()
            }
        } else {

            withContext(Dispatchers.Main) {
                bridge.onStartCaptureUi()
            }

            startLiveStreamFeed(bridge)
        }

        delay(1000L)
    }

    private suspend fun startLiveStreamFeed(bridge: ControllerBridge) {

        while (true) {

            try {

                val bmp = api.getLiveStream()

                withContext(Dispatchers.Main) {

                    bridge.onReceiveStreamImage(bmp)

                }

            } catch (e: Exception) {

                withContext(Dispatchers.Main) {

                    bridge.onFail()
                }

                break

            }

            delay(1000L)
        }
    }

    fun awaitConnection(onStartConnection: () -> Unit) {

        scope.cancel()

        scope = CoroutineScope(Dispatchers.IO)

        scope.launch {

            withContext(Dispatchers.IO) {

                while (true) {

                    val response = getDeviceInformation()

                    if (response != null) {

                        withContext(Dispatchers.Main) {

                            onStartConnection()

                        }

                        break
                    }

                    delay(1000L)
                }
            }
        }
    }

    fun postCameraShutter(bridge: ControllerBridge) {

        scope.launch {

            withContext(Dispatchers.IO) {

                val postShutter = postShutterButton()

                if (postShutter != null) {

                    val bmp = getLatestImageFromCanon()

                    if (bmp != null) {

                        bridge.saveBitmap(bmp)
                    }
                }
            }
        }
    }

    private suspend fun postShutterButton() = try {

        api.postShutterButton(
            ShutterAction(
                af = true
            )
        )

    } catch (e: ConnectException) {

        null

    }

    private suspend fun getDeviceInformation() = try {

        api.getDeviceInformation()

    } catch (e: ConnectException) {

        null

    }

    private suspend fun postMovieModeOff() =  try {

        api.postMovieMode(MovieMode("off"))

    } catch (e: Exception) {

        null

    }

    private suspend fun postLiveViewSettings() = try {

        api.postLiveViewSettings(
            LiveViewSettings(
                "medium",
                "on"
            )
        )

    } catch (e: Exception) {

        null

    }

    private suspend fun getLatestImageFromCanon(): Bitmap? {

        val storage = getCurrentStorage()
        val dir = getCurrentDir()

        if (storage != null && dir != null) {

            val contents = getContents(storage.name, dir.name)

            val imagePath = contents?.path?.maxBy {

                try {

                    //parse out the index in filenames: IMG_108.JPG -> 108
                    val id = it.split("_")[1].split(".")[0].toInt()

                    Log.d(TAG, id.toString())

                    id

                } catch (e: Exception) {

                    Int.MIN_VALUE

                }
            }

            val name = imagePath?.split("/")?.last()

            if (name != null) {

                return getImage(storage.name, dir.name, name)

            }
        }

        return null
    }

    private suspend fun getContents(drive: String, dir: String) = try {

        api.getContents("ver120", drive, dir)

    } catch (e: Exception) {

        getContentsV110(drive, dir)
    }

    private suspend fun getContentsV110(drive: String, dir: String) = try {

        api.getContents("ver110", drive, dir)

    } catch (e: Exception) {

        getContentsV100(drive, dir)
    }

    private suspend fun getContentsV100(drive: String, dir: String) = try {

        api.getContents("ver100", drive, dir)

    } catch (e: Exception) {

        null

    }

    private suspend fun getImage(drive: String, dir: String, name: String? = null) = try {

        api.getContents("ver120", drive, dir, name)

    } catch (e: Exception) {

        getImageV110(drive, dir)
    }

    private suspend fun getImageV110(drive: String, dir: String, name: String? = null) = try {

        api.getContents("ver110", drive, dir, name)

    } catch (e: Exception) {

        getImageV100(drive, dir)
    }

    private suspend fun getImageV100(drive: String, dir: String, name: String? = null) = try {

        api.getContents("ver100", drive, dir, name)

    } catch (e: Exception) {

        null

    }

    private suspend fun getCurrentStorage() = try {
        api.getCurrentStorage()
    } catch (e: Exception) {
        null
    }

    private suspend fun getCurrentDir() = try {
        api.getCurrentDirectory()
    } catch (e: Exception) {
        null
    }
}