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

@AndroidEntryPoint
class CanonTrait :
    CameraTrait,
    PtpSessionCallback,
    WifiHelper.WifiRequester {

    private val uiScope = CoroutineScope(Dispatchers.Main)

    companion object {
        const val TAG = "Canon"
        const val type = "canon"
        const val CAMERA_SHUTTER_DELAY_MS = 2000L
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

            captureBtn?.visibility = View.INVISIBLE

            imageView?.visibility = View.INVISIBLE

            (imageView?.layoutParams as ConstraintLayout.LayoutParams)
                .width = ConstraintLayout.LayoutParams.MATCH_PARENT

            connectBtn?.visibility = View.VISIBLE

            if (controller.getCanonApi().isConnected) {

                startCanonSession()

            } else controller.getWifiHelper().startWifiSearch(ssid, this)

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

    private fun startCanonSession() {

        val scope = CoroutineScope(Dispatchers.IO)

        scope.launch(Dispatchers.IO) {

            try {

                controller.getCanonApi().initiateSession(this@CanonTrait)

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

            captureBtn?.setOnClickListener {

                captureBtn?.isEnabled = false

                Handler(Looper.getMainLooper()).postDelayed({

                    captureBtn?.isEnabled = true

                }, CAMERA_SHUTTER_DELAY_MS)

                controller.getCanonApi().startSingleShotCapture(currentRange)

            }
        }
    }

    override fun onSessionStop() {

        uiScope.launch {

            imageView?.visibility = View.INVISIBLE

            captureBtn?.visibility = View.INVISIBLE

            connectBtn?.visibility = View.VISIBLE

            captureBtn?.setOnClickListener(null)
        }
    }

    override fun onPreview(bmp: Bitmap) {

        uiScope.launch(Dispatchers.Main) {

            captureBtn?.visibility = View.VISIBLE

            imageView?.setImageBitmap(bmp)

        }
    }

    override fun onBitmapCaptured(bmp: Bitmap, obsUnit: RangeObject) {

        uiScope.launch(Dispatchers.Main) {

            saveBitmapToStorage(type(), bmp, obsUnit)

        }
    }

//    override fun getSsidName() =
//        preferences.getString(GeneralKeys.CANON_SSID_NAME, "Canon") ?: "Canon"

    override fun onNetworkBound() {

        startCanonSession()

    }
}

