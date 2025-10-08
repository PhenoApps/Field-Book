package com.fieldbook.tracker.traits

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.constraintlayout.widget.ConstraintLayout
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.internalTimeFormatter
import com.fieldbook.tracker.devices.camera.GoProApi
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.views.CanonCameraTraitSettingsView
import com.fieldbook.tracker.views.GoProCameraSettingsView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.threeten.bp.OffsetDateTime

@AndroidEntryPoint
class GoProTraitLayout :
    CameraTrait,
    GoProApi.Callbacks {

    companion object {
        const val TAG = "GoProTrait"
        const val type = "gopro"
        const val GO_PRO_9_QUERY_DELAY = 5000L
    }

    private var dialogWaitForStream: AlertDialog? = null

    private var cameraBusy: Boolean = false

    private var currentPlotId: String? = null

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

    private fun setup() {

        setupWaitForStreamDialog()

        previewCardView?.visibility = View.VISIBLE
        styledPlayerView?.visibility = View.VISIBLE
        imageView?.visibility = View.INVISIBLE

        previewCardView?.layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.topToBottom = recyclerView?.id ?: 0
        }

        val started = controller.getGoProApi().isStreamStarted()
        Log.d(TAG, "Connected: $started")

        if (started) {

            createPlayer()

        } else {

            initializeConnectButton()

        }
    }

    private fun setupWaitForStreamDialog() {

        dialogWaitForStream = AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.dialog_go_pro_wait_stream_title)
            .setMessage(R.string.dialog_go_pro_wait_stream_message)
            .setPositiveButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .create()

        dialogWaitForStream?.setView(ProgressBar(context).also {
            it.isIndeterminate = true
            it.layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
            it.layout(16, 16, 16, 16)
        })
    }

    override fun loadLayout() {
        super.loadLayout()
        setup()
        currentPlotId = currentRange.uniqueId
    }

    private fun initializeConnectButton() {

        connectBtn?.visibility = View.VISIBLE
        shutterButton?.visibility = View.GONE
        styledPlayerView?.visibility = View.GONE
        previewCardView?.visibility = View.GONE

        connectBtn?.setOnClickListener {
            connect()
        }
    }

    private fun getImageRequestData(): GoProApi.ImageRequestData {

        //val plot = currentRange.plot_id
        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
        //val traitName = currentTrait.name
        val timestamp = FileUtil.sanitizeFileName(OffsetDateTime.now().format(internalTimeFormatter))
        //val name = "${traitName}_${plot}_$timestamp.png"

        return GoProApi.ImageRequestData(
            studyId,
            currentRange,
            currentTrait,
            timestamp
        )
    }

    private fun initializeCameraShutterButton() {

        previewCardView?.visibility = View.VISIBLE
        connectBtn?.visibility = View.GONE
        styledPlayerView?.visibility = View.VISIBLE

        shutterButton?.setOnClickListener {

            shutterButton?.isEnabled = false

            controller.getSoundHelper().playShutter()

            controller.getGoProApi().range.add(getImageRequestData())

            controller.getGoProApi().shutterOn()

            Handler(Looper.getMainLooper()).postDelayed({
                controller.getGoProApi().queryMedia(requestAndSaveImage = currentTrait.saveImage)
                shutterButton?.isEnabled = true
            }, GO_PRO_9_QUERY_DELAY)

            shutterButton?.isEnabled = false
        }

        background.launch {

            val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
            var currentStudyId = studyId
            while (currentStudyId == studyId) {

                controller.getGoProApi().getBusyState()

                delay(2000)

                currentStudyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
            }
        }
    }

    override fun onImageRequestReady(
        bytes: ByteArray,
        data: GoProApi.ImageRequestData,
        model: GoProApi.GoProImage?
    ) {

        ui.launch {

            saveJpegToStorage(bytes, data.range, data.trait, data.time, SaveState.SINGLE_SHOT, goProImage = model)

            shutterButton?.isEnabled = true

        }
    }

    override fun onBusyStateChanged(isBusy: Int, isEncoding: Int) {

        Log.d(TAG, "Busy state changed: busy state: $isBusy, encoding state: $isEncoding")

        val old = cameraBusy

        cameraBusy = isBusy == 1 || isEncoding == 1

        if (cameraBusy) {

            //capturing photo

        } else {

            //waiting for capture
            //check if capture is done
            if (old) {
                Log.d(TAG, "Capture is done")
                //capture is done
                controller.getGoProApi().queryMedia(requestAndSaveImage = currentTrait.saveImage)
            }

            controller.getGoProApi().lastMoved = getImageRequestData()

        }
    }

    override fun onStreamRequested() {
        ui.launch {
            styledPlayerView?.player = controller.getGoProApi().createPlayer()
            styledPlayerView?.requestFocus()
        }
    }

    override fun onStreamReady() {
        dialogWaitForStream?.dismiss()
        initializeCameraShutterButton()
        shutterButton?.visibility = View.VISIBLE
        settingsButton?.visibility = View.VISIBLE

        settingsButton?.setOnClickListener {

            showSettings()
        }
    }

    private fun createPlayer() {

        styledPlayerView?.player = controller.getGoProApi().createPlayer()

        Log.i(TAG, "Player created")
    }

    private fun connect() {

        controller.advisor().withNearby { adapter: BluetoothAdapter ->

            if (!adapter.isEnabled) {

                //if not enabled, start intent for settings
                context?.startActivity(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                )

            } else {

                connectToBluetoothDevice(adapter)

            }
        }
    }

    private fun connectToBluetoothDevice(adapter: BluetoothAdapter) {

        val devices = adapter.bondedDevices.toTypedArray()
        val displayList = devices.map { it.name }.toTypedArray()
        var selected = 0
        val dialog = AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.trait_go_pro_await_device_title)
            .setCancelable(true)
            .setSingleChoiceItems(displayList, 0) { _, which ->
                selected = which
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(android.R.string.ok) { _, which ->
                Log.d(TAG, which.toString())
                controller.getGoProApi().onConnect(devices[selected], this)
            }.create()

        dialog.show()
    }

    override fun onConnected() {

        controller.getGoProApi().requestStream()

    }

    override fun onInitializeGatt() {

        ui.launch {
            dialogWaitForStream?.show()
        }
    }

    override fun showSettings() {
        val settingsView = GoProCameraSettingsView(context, currentTrait)

        AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.go_pro_trait_settings_title)
            .setView(settingsView)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                settingsView.commitChanges()
                dialog.dismiss()
            }
            .setView(settingsView)
            .create()
            .show()
    }
}