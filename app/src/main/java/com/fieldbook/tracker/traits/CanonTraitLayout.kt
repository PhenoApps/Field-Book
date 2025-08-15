package com.fieldbook.tracker.traits

import android.app.AlertDialog
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.View
import androidx.constraintlayout.widget.ConstraintLayout
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.internalTimeFormatter
import com.fieldbook.tracker.devices.ptpip.PtpSessionCallback
import com.fieldbook.tracker.objects.RangeObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.WifiHelper
import com.fieldbook.tracker.views.CanonCameraTraitSettingsView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.threeten.bp.OffsetDateTime

@AndroidEntryPoint
class CanonTraitLayout :
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

        scope.launch {

            try {

                controller.getCanonApi().initiateSession(this@CanonTraitLayout, rangeObject)

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
            previewCardView?.visibility = if (prefs.getBoolean(GeneralKeys.CANON_CAMERA_PREVIEW, true))
                View.VISIBLE else View.GONE
            previewCardView?.layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.topToBottom = recyclerView?.id ?: 0
            }

            shutterButton?.setOnClickListener {

                shutterButton?.isEnabled = false

                controller.getCanonApi().startSingleShotCapture(currentRange,
                    FileUtil.sanitizeFileName(OffsetDateTime.now().format(internalTimeFormatter))
                )

                controller.getSoundHelper().playShutter()

                controller.getRangeBox().toggleNavigation(false)

                Handler(Looper.getMainLooper()).postDelayed({

                    shutterButton?.isEnabled = true

                    controller.getRangeBox().toggleNavigation(true)

                }, CAMERA_SHUTTER_DELAY_MS)

            }

            settingsButton?.setOnClickListener {

                showSettings()
            }
        }
    }

    override fun onSettingsChanged() {
        previewCardView?.visibility = if (prefs.getBoolean(GeneralKeys.CANON_CAMERA_PREVIEW, true))
            View.VISIBLE else View.GONE
    }

    override fun showSettings() {

        val settingsView = CanonCameraTraitSettingsView(context)

        AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.canon_trait_settings_title)
            .setView(settingsView)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                settingsView.commitChanges()
                onSettingsChanged()
                dialog.dismiss()
            }
            .setView(settingsView)
            .create()
            .show()
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
            settingsButton?.visibility = View.VISIBLE

            imageView?.setImageBitmap(bmp)

        }
    }

    override fun onJpegCaptured(
        data: ByteArray,
        obsUnit: RangeObject,
        saveTime: String,
        saveState: SaveState,
        offset: Int?
    ) {

        uiScope.launch(Dispatchers.Main) {

            saveJpegToStorage(data, obsUnit, currentTrait, saveTime, saveState, offset)

        }
    }

//    override fun getSsidName() =
//        preferences.getString(GeneralKeys.CANON_SSID_NAME, "Canon") ?: "Canon"

    override fun onNetworkBound() {

        startCanonSession(currentRange)

    }
}

