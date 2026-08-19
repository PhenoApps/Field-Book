package com.fieldbook.tracker.traits

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.documentfile.provider.DocumentFile
import com.fieldbook.tracker.R
import com.fieldbook.tracker.database.internalTimeFormatter
import com.fieldbook.tracker.devices.camera.GoProApi
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.views.GoProCameraSettingsView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import org.threeten.bp.OffsetDateTime
import java.io.OutputStream

@AndroidEntryPoint
class GoProTraitLayout :
    CameraTrait,
    GoProApi.Callbacks {

    companion object {
        const val TAG = "GoProTrait"
        const val type = "gopro"
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

    private val api get() = controller.getGoProApi()

    override fun loadLayout() {
        super.loadLayout()

        //keep the api pointed at the entry the user is currently on so a photo taken with the
        //camera's own shutter button is filed against the right plot
        api.currentEntry = getImageRequestData()

        when (api.state()) {

            GoProApi.ConnectionState.DISCONNECTED,
            GoProApi.ConnectionState.ERROR -> setupDisconnected()

            GoProApi.ConnectionState.STREAMING,
            GoProApi.ConnectionState.CAPTURING -> bindExistingSession()

            else -> showConnectingUi()
        }
    }

    /**
     * Releases the session when the user leaves the trait. Only job handles are cancelled here:
     * the layout instance is created once per activity and reused, so cancelling the shared
     * coroutine scopes would permanently disable the trait.
     */
    override fun onExit() {
        super.onExit()

        dismissWaitDialog()

        styledPlayerView?.player = null

        shutterButton?.setOnClickListener(null)

        api.teardownAsync()
    }

    private fun setupWaitForStreamDialog(): AlertDialog {

        dialogWaitForStream?.let { return it }

        val dialog = AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.dialog_go_pro_wait_stream_title)
            .setMessage(R.string.dialog_go_pro_wait_stream_message)
            .setPositiveButton(android.R.string.cancel) { d, _ ->
                d.dismiss()
                api.teardownAsync()
            }
            .create()

        dialog.setView(ProgressBar(context).also {
            it.isIndeterminate = true
            it.layoutParams = LayoutParams(
                LayoutParams.WRAP_CONTENT,
                LayoutParams.WRAP_CONTENT
            )
            it.layout(16, 16, 16, 16)
        })

        dialogWaitForStream = dialog

        return dialog
    }

    private fun dismissWaitDialog() {
        try {
            dialogWaitForStream?.dismiss()
        } catch (e: Exception) {
            Log.w(TAG, "Unable to dismiss stream dialog", e)
        }
    }

    private fun preparePreviewLayout() {

        previewCardView?.layoutParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).also {
            it.topToBottom = recyclerView?.id ?: 0
        }
    }

    private fun setupDisconnected() {

        preparePreviewLayout()

        dismissWaitDialog()

        connectBtn?.visibility = View.VISIBLE
        connectProgress?.visibility = View.GONE
        shutterButton?.visibility = View.GONE
        settingsButton?.visibility = View.GONE
        styledPlayerView?.visibility = View.GONE
        previewCardView?.visibility = View.GONE
        imageView?.visibility = View.INVISIBLE

        connectBtn?.setOnClickListener { connect() }
    }

    private fun showConnectingUi() {

        preparePreviewLayout()

        connectBtn?.visibility = View.GONE
        connectProgress?.visibility = View.VISIBLE
        shutterButton?.visibility = View.GONE
        styledPlayerView?.visibility = View.GONE
        previewCardView?.visibility = View.GONE
    }

    /**
     * Re-attaches the view to a session that is already running, without restarting the player.
     */
    private fun bindExistingSession() {

        preparePreviewLayout()

        dismissWaitDialog()

        connectBtn?.visibility = View.GONE
        connectProgress?.visibility = View.GONE
        previewCardView?.visibility = View.VISIBLE
        styledPlayerView?.visibility = View.VISIBLE
        imageView?.visibility = View.INVISIBLE
        shutterButton?.visibility = View.VISIBLE
        settingsButton?.visibility = View.VISIBLE

        bindShutterAndSettings()
    }

    private fun getImageRequestData(): GoProApi.ImageRequestData {

        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
        val timestamp = FileUtil.sanitizeFileName(OffsetDateTime.now().format(internalTimeFormatter))

        return GoProApi.ImageRequestData(
            studyId,
            currentRange,
            currentTrait,
            timestamp
        )
    }

    private fun bindShutterAndSettings() {

        shutterButton?.setOnClickListener {

            shutterButton?.isEnabled = false

            controller.getSoundHelper().playShutter()

            api.capture(getImageRequestData(), currentTrait.saveImage)
        }

        settingsButton?.setOnClickListener { showSettings() }
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

        if (devices.isEmpty()) {
            Toast.makeText(context, R.string.trait_go_pro_no_paired_devices, Toast.LENGTH_LONG)
                .show()
            return
        }

        val displayList = devices.map { it.name }.toTypedArray()
        var selected = 0

        AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.trait_go_pro_await_device_title)
            .setCancelable(true)
            .setSingleChoiceItems(displayList, 0) { _, which ->
                selected = which
            }
            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                dialog.dismiss()
            }
            .setPositiveButton(android.R.string.ok) { _, _ ->
                if (selected < devices.size) {
                    api.onConnect(devices[selected], this)
                }
            }
            .create()
            .show()
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
            .create()
            .show()
    }

    /**
     * GoProApi.Callbacks region. These arrive on background threads.
     */
    override fun onConnectionStateChanged(state: GoProApi.ConnectionState, messageRes: Int?) {

        ui.launch {

            messageRes?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }

            when (state) {

                GoProApi.ConnectionState.DISCONNECTED,
                GoProApi.ConnectionState.ERROR -> setupDisconnected()

                GoProApi.ConnectionState.CONNECTING_BLE,
                GoProApi.ConnectionState.AWAITING_AP,
                GoProApi.ConnectionState.CONNECTING_WIFI,
                GoProApi.ConnectionState.CONNECTED -> showConnectingUi()

                GoProApi.ConnectionState.STREAMING,
                GoProApi.ConnectionState.CAPTURING,
                GoProApi.ConnectionState.DISCONNECTING -> Unit
            }
        }
    }

    override fun onInitializeGatt() {
        ui.launch {
            showConnectingUi()
            setupWaitForStreamDialog().show()
        }
    }

    override fun onConnected() {
        api.requestStartStream()
    }

    override fun onStreamRequested() {
        ui.launch {
            styledPlayerView?.player = api.createPlayer()
            styledPlayerView?.requestFocus()
        }
    }

    override fun onStreamReady() {

        api.startPolling()

        ui.launch {
            dismissWaitDialog()
            bindExistingSession()
        }
    }

    override fun onCaptureFinished() {
        ui.launch {
            shutterButton?.isEnabled = true
        }
    }

    override fun onCaptureFailed(messageRes: Int) {
        ui.launch {
            shutterButton?.isEnabled = true
            Toast.makeText(context, messageRes, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Hands the api a destination to stream the photo into. Called off the main thread.
     */
    override fun onImageSinkRequested(
        data: GoProApi.ImageRequestData,
        model: GoProApi.GoProImage
    ): GoProApi.ImageSink? {

        val file: DocumentFile = createStreamedFile(data.range, data.time) ?: return null

        return object : GoProApi.ImageSink {

            override fun openStream(): OutputStream? =
                this@GoProTraitLayout.openStreamedFile(file)

            override fun commit(bytesWritten: Long) {
                this@GoProTraitLayout.commitStreamedFile(file, data.range, data.time)
            }

            override fun discard() {
                this@GoProTraitLayout.discardStreamedFile(file)
            }
        }
    }

    override fun onImageSaved(data: GoProApi.ImageRequestData, model: GoProApi.GoProImage) {
        Log.d(TAG, "Saved ${model.fileName} for ${data.range.uniqueId}")
    }

    override fun onImageNameReady(
        data: GoProApi.ImageRequestData,
        model: GoProApi.GoProImage
    ) {
        saveFileNameObservation(data.range, data.trait, model.fileName)
    }

    override fun onBusyStateChanged(isBusy: Int, isEncoding: Int) {
        Log.d(TAG, "Busy state changed: busy state: $isBusy, encoding state: $isEncoding")
    }
}
