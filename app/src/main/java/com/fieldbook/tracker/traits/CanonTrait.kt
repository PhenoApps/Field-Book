package com.fieldbook.tracker.traits

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.fieldbook.tracker.devices.ptpip.PtpSessionCallback
import com.fieldbook.tracker.objects.RangeObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.WifiHelper
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.phenoapps.androidlibrary.Utils

@AndroidEntryPoint
class CanonTrait :
    CameraTrait,
    PtpSessionCallback,
    WifiHelper.WifiRequester {

    private val uiScope = CoroutineScope(Dispatchers.Main)

    companion object {
        const val TAG = "Canon"
        const val type = "canon"
        const val CAMERA_SHUTTER_DELAY_MS = 3000L
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun type(): String {
        return type
    }

    override fun loadLayout() {
        super.loadLayout()
        setup()
    }

    private fun setup() {

        val ssid = preferences.getString(GeneralKeys.CANON_SSID_NAME, "Canon") ?: "Canon"

        activity?.runOnUiThread {

            shutterButton?.visibility = View.INVISIBLE

            imageView?.visibility = View.INVISIBLE
            previewCardView?.visibility = View.INVISIBLE

            connectBtn?.visibility = View.VISIBLE

            if (controller.getCanonApi().isConnected) {

                startCanonSession(currentRange)

            } //else controller.getWifiHelper().startWifiSearch(ssid, this)

            connectBtn?.setOnClickListener {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    collectActivity.advisor().withPermission(
                        arrayOf(
                            android.Manifest.permission.ACCESS_COARSE_LOCATION,
                            android.Manifest.permission.ACCESS_FINE_LOCATION,
                            android.Manifest.permission.NEARBY_WIFI_DEVICES,
                            android.Manifest.permission.BLUETOOTH_SCAN
                        )
                    ) {

                        controller.getWifiHelper().disconnect()

                        controller.getWifiHelper().startWifiSearch(ssid, this)

                    }
                }
            }
        }
    }

    private fun startCanonSession(rangeObject: RangeObject) {

        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch(Dispatchers.IO) {

            try {

                controller.getCanonApi().initiateSession(this@CanonTrait, rangeObject)

            } catch (e: Exception) {

                controller.getCanonApi().stopSession()

                e.printStackTrace()
            }
        }
    }

    override fun onSessionStart() {

        uiScope.launch {

            connectBtn?.visibility = View.INVISIBLE
            imageView?.visibility = View.VISIBLE
            previewCardView?.visibility = View.VISIBLE

            previewCardView?.layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
                it.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
                it.topToBottom = recyclerView?.id ?: 0
                it.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            }

            shutterButton?.layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.WRAP_CONTENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT)
                .apply {
                    startToStart = previewCardView?.id ?: 0
                    endToEnd = previewCardView?.id ?: 0
                    bottomToBottom = previewCardView?.id ?: 0
                }

            shutterButton?.setOnClickListener {

                shutterButton?.isEnabled = false

                controller.getCanonApi().startSingleShotCapture(currentRange, Utils.getDateTime())

                controller.getRangeBox().toggleNavigation(false)

                Handler(Looper.getMainLooper()).postDelayed({

                    shutterButton?.isEnabled = true

                    controller.getRangeBox().toggleNavigation(true)

                }, CAMERA_SHUTTER_DELAY_MS)

            }
        }
    }

    override fun onSessionStop() {

        uiScope.launch {

            imageView?.visibility = View.INVISIBLE
            previewCardView?.visibility = View.INVISIBLE

            shutterButton?.visibility = View.INVISIBLE

            connectBtn?.visibility = View.VISIBLE

            shutterButton?.setOnClickListener(null)
        }
    }

    override fun onPreview(bmp: Bitmap) {

        uiScope.launch(Dispatchers.Main) {

            shutterButton?.visibility = View.VISIBLE

            imageView?.setImageBitmap(bmp)

        }
    }

    override fun onJpegCaptured(
        data: ByteArray,
        obsUnit: RangeObject,
        saveTime: String,
        saveState: SaveState
    ) {

        uiScope.launch(Dispatchers.Main) {

            saveJpegToStorage(type(), data, obsUnit, saveTime, saveState)

        }
    }

//    override fun getSsidName() =
//        preferences.getString(GeneralKeys.CANON_SSID_NAME, "Canon") ?: "Canon"

    override fun onNetworkBound() {

        startCanonSession(currentRange)

    }
}

