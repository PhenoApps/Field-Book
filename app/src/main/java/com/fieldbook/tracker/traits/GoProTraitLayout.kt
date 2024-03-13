package com.fieldbook.tracker.traits

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.media3.common.util.UnstableApi
import com.fieldbook.tracker.R
import com.fieldbook.tracker.devices.camera.GoProApi
import com.fieldbook.tracker.preferences.GeneralKeys
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.phenoapps.fragments.gopro.GoProFragment
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

//todo gopro different versions
////todo aim for hero 11 -> if not 11 then say a message with a link to an email
//TODO improve time between taking a picture and it showing in match list items with timestamp when doing media query
//TODO: shutter on the gopro detected in FB
//TODO test usb camera trait with UVC.

@UnstableApi @AndroidEntryPoint
class GoProTraitLayout :
    CameraTrait,
    GoProApi.Callbacks {

    companion object {
        const val TAG = "GoProTrait"
        const val type = "gopro"
        private const val CAMERA_DELAY_MS = 10000L
    }

    private var dialogWaitForStream: AlertDialog? = null

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

        styledPlayerView?.visibility = View.VISIBLE
        imageView?.visibility = View.INVISIBLE

        styledPlayerView?.layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_CONSTRAINT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            it.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            it.topToBottom = recyclerView?.id ?: 0
            it.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
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

        dialogWaitForStream = AlertDialog.Builder(context)
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
    }

    private fun initializeConnectButton() {

        connectBtn?.visibility = View.VISIBLE
        captureBtn?.visibility = View.GONE
        styledPlayerView?.visibility = View.GONE

        connectBtn?.setOnClickListener {
            connect()
        }
    }

    @UnstableApi private fun onShutter() {

        captureBtn?.isEnabled = false

        //val plot = currentRange.plot_id
        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
        //val traitName = currentTrait.name
        val timestamp = SimpleDateFormat("yyyy-MM-dd-hh-mm-ss", Locale.US)
            .format(Calendar.getInstance().time)
        //val name = "${traitName}_${plot}_$timestamp.png"

        Handler(Looper.getMainLooper()).postDelayed({

            controller.getGoProApi().queryMedia(
                GoProApi.ImageRequestData(
                    studyId,
                    currentRange,
                    currentTrait,
                    timestamp
                )
            )
        }, CAMERA_DELAY_MS)
    }

    private fun initializeCameraShutterButton() {

        connectBtn?.visibility = View.GONE
        styledPlayerView?.visibility = View.VISIBLE

        captureBtn?.visibility = View.VISIBLE

        (captureBtn?.layoutParams as ConstraintLayout.LayoutParams)
            .bottomToBottom = styledPlayerView?.id ?: 0

        captureBtn?.setOnClickListener {

            captureBtn?.isEnabled = false

            controller.getGoProApi().shutterOn()

            onShutter()
        }
    }

    override fun onImageRequestReady(bitmap: Bitmap, data: GoProApi.ImageRequestData) {

        ui.launch {

            saveBitmapToStorage(type(), bitmap, data.range)

            captureBtn?.isEnabled = true

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
        captureBtn?.visibility = View.VISIBLE
    }

    private fun createPlayer() {

        styledPlayerView?.player = controller.getGoProApi().createPlayer()

        Log.i(GoProFragment.TAG, "Player created")
    }

    private fun connect() {

        controller.advisor().withNearby { adapter ->

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
            .setPositiveButton(android.R.string.ok) { dialog, which ->
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
}