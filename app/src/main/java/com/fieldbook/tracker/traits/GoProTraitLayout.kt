package com.fieldbook.tracker.traits

import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.view.WindowManager
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.documentfile.provider.DocumentFile
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
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

        //used until the stream reports its real size, GoPro previews are 16:9
        private const val DEFAULT_PREVIEW_ASPECT_RATIO = 16f / 9f

        //matches the margins on the thumbnail strip and shutter row in trait_camera.xml
        private const val CONTROL_MARGIN_DP = 8
    }

    private var dialogWaitForStream: AlertDialog? = null

    /**
     * True while this trait is on screen. Guards posted fits and late capture callbacks, so work
     * landing after [onExit] cannot change another camera trait's preview or shutter.
     */
    private var watchingPreview = false

    /** Whether the recording ui (stop button, navigation lock, screen kept on) is applied. */
    private var recordingUi = false

    private val previewFitListener =
        View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
            if (right - left != oldRight - oldLeft || bottom - top != oldBottom - oldTop) {
                schedulePreviewFit()
            }
        }

    private val videoSizeListener = object : Player.Listener {
        override fun onVideoSizeChanged(videoSize: VideoSize) {
            schedulePreviewFit()
        }
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

    private val api get() = controller.getGoProApi()

    override fun loadLayout() {
        super.loadLayout()

        //keep the api pointed at the entry the user is currently on so a photo taken with the
        //camera's own shutter button is filed against the right plot
        api.currentEntry = getImageRequestData()

        watchPreviewBounds()

        when (api.state()) {

            GoProApi.ConnectionState.DISCONNECTED,
            GoProApi.ConnectionState.ERROR -> setupDisconnected()

            GoProApi.ConnectionState.STREAMING,
            GoProApi.ConnectionState.CAPTURING,
            GoProApi.ConnectionState.RECORDING -> {
                bindExistingSession()
                //cancels a park still waiting on a capture if the user came straight back
                api.resume(currentTrait.captureVideo)
            }

            GoProApi.ConnectionState.PARKED -> {
                showConnectingUi()
                api.resume(currentTrait.captureVideo)
            }

            else -> showConnectingUi()
        }
    }

    /**
     * Parks the session when the user leaves the trait, so coming back skips the bluetooth
     * handshake and the wifi connect dialog. Only job handles are cancelled here: the layout
     * instance is created once per activity and reused, so cancelling the shared coroutine scopes
     * would permanently disable the trait.
     */
    override fun onExit() {
        super.onExit()

        dismissWaitDialog()

        //parking stops a running recording and saves its name, so release the navigation lock
        //and screen now rather than waiting on the camera
        setRecordingUi(false)

        unwatchPreviewBounds()

        styledPlayerView?.player?.removeListener(videoSizeListener)
        styledPlayerView?.player = null

        shutterButton?.setOnClickListener(null)

        api.park()
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

    /**
     * Refits the preview whenever the space around it changes: the thumbnail strip grows with the
     * first photo, and the trait container shrinks when the input view above it does.
     */
    private fun watchPreviewBounds() {

        watchingPreview = true

        listOfNotNull(recyclerView, findScrollContainer()).forEach {
            it.removeOnLayoutChangeListener(previewFitListener)
            it.addOnLayoutChangeListener(previewFitListener)
        }
    }

    private fun unwatchPreviewBounds() {

        watchingPreview = false

        listOfNotNull(recyclerView, findScrollContainer()).forEach {
            it.removeOnLayoutChangeListener(previewFitListener)
        }
    }

    //posted because a resize requested from inside a layout pass is deferred or dropped
    private fun schedulePreviewFit() {
        previewCardView?.post { fitPreview() }
    }

    /**
     * Sizes the preview to the stream's aspect ratio, as wide as the trait allows but no taller than
     * the visible space between the thumbnail strip and the shutter row. Filling the width alone
     * pushed the shutter below the fold whenever the trait area was short or wide.
     */
    private fun fitPreview() {

        if (!watchingPreview) return

        val card = previewCardView ?: return
        val container = card.parent as? View ?: return
        val scroll = findScrollContainer() ?: return

        val viewport = scroll.height - scroll.paddingTop - scroll.paddingBottom

        if (container.width <= 0 || viewport <= 0) return

        val margin = (CONTROL_MARGIN_DP * resources.displayMetrics.density).toInt()

        val above = recyclerView?.let { offsetWithin(it, scroll) + it.height + margin }
            ?: offsetWithin(container, scroll)

        val below = resources.getDimensionPixelSize(R.dimen.fb_trait_fab_size) + 2 * margin

        val maxWidth = container.width
        val maxHeight = viewport - above - below

        val aspect = previewAspectRatio()

        var width = maxWidth
        var height = (width / aspect).toInt()

        if (height > maxHeight) {
            //on a very short screen keep a usable preview and let the container scroll instead
            height = maxOf(maxHeight, resources.getDimensionPixelSize(R.dimen.camera_preview_landscape_height))
            width = minOf(maxWidth, (height * aspect).toInt())
        }

        val topAnchor = recyclerView?.id ?: ConstraintLayout.LayoutParams.PARENT_ID

        val current = card.layoutParams as? ConstraintLayout.LayoutParams

        if (current != null && current.width == width && current.height == height &&
            current.topToBottom == topAnchor &&
            current.startToStart == ConstraintLayout.LayoutParams.PARENT_ID
        ) return

        card.layoutParams = ConstraintLayout.LayoutParams(width, height).also {
            it.startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            it.endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            it.topToBottom = topAnchor
        }
    }

    private fun previewAspectRatio(): Float {

        val size = styledPlayerView?.player?.videoSize

        return if (size != null && size.width > 0 && size.height > 0) {
            size.width * size.pixelWidthHeightRatio / size.height
        } else DEFAULT_PREVIEW_ASPECT_RATIO
    }

    private fun findScrollContainer(): ScrollView? {

        var parent = previewCardView?.parent

        while (parent != null) {
            if (parent is ScrollView) return parent
            parent = parent.parent
        }

        return null
    }

    private fun offsetWithin(view: View, ancestor: View): Int {

        var offset = 0
        var current: View? = view

        while (current != null && current !== ancestor) {
            offset += current.top
            current = current.parent as? View
        }

        return offset
    }

    private fun setupDisconnected() {

        schedulePreviewFit()

        dismissWaitDialog()

        connectBtn?.visibility = View.VISIBLE
        connectProgress?.visibility = View.GONE
        shutterButton?.visibility = View.GONE
        settingsButton?.visibility = View.GONE
        styledPlayerView?.visibility = View.GONE
        previewCardView?.visibility = View.GONE
        imageView?.visibility = View.INVISIBLE
        imageView?.setImageDrawable(null)

        connectBtn?.setOnClickListener { connect() }
    }

    private fun showConnectingUi() {

        schedulePreviewFit()

        connectBtn?.visibility = View.GONE
        connectProgress?.visibility = View.VISIBLE
        shutterButton?.visibility = View.GONE
        settingsButton?.visibility = View.GONE
        styledPlayerView?.visibility = View.GONE
        previewCardView?.visibility = View.GONE
    }

    /**
     * Re-attaches the view to a session that is already running, without restarting the player.
     */
    private fun bindExistingSession() {

        schedulePreviewFit()

        dismissWaitDialog()

        connectBtn?.visibility = View.GONE
        connectProgress?.visibility = View.GONE
        shutterButton?.visibility = View.VISIBLE
        settingsButton?.visibility = View.VISIBLE

        applyPreviewVisibility()

        updateShutterIcon()

        bindShutterAndSettings()
    }

    /**
     * Applies or releases everything a running recording needs from the screen. Plot navigation is
     * blocked so the recording stays filed against the plot it started on, and the screen is kept
     * on because the activity stopping ends the session, and the recording with it.
     */
    private fun setRecordingUi(recording: Boolean) {

        if (recording == recordingUi) return

        //a start still in flight when the user left lands here during teardown, which then stops
        //it, so it must not lock navigation on whatever trait is now showing
        if (recording && !watchingPreview) return

        recordingUi = recording

        controller.getRangeBox().toggleNavigation(!recording)

        activity?.window?.let { window ->
            if (recording) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }

        if (recording) shutterButton?.isEnabled = true

        updateShutterIcon()
    }

    private fun updateShutterIcon() {

        //capture callbacks can land after onExit, when the button belongs to another trait
        if (!watchingPreview) return

        val stop = currentTrait.captureVideo && recordingUi

        shutterButton?.setImageResource(
            when {
                !currentTrait.captureVideo -> R.drawable.camera_24px
                stop -> R.drawable.ic_media_stop
                else -> R.drawable.ic_media_record
            }
        )

        //the stop square is white and disappears against the light button, as in the video trait
        if (stop) shutterButton?.setColorFilter(Color.BLACK)
        else shutterButton?.clearColorFilter()
    }

    /**
     * Hides the preview without touching the stream: the session only counts as live once a frame
     * has arrived, and captures restart the stream regardless, so stopping it here would break both.
     */
    private fun applyPreviewVisibility() {

        val show = prefs.getBoolean(GeneralKeys.GOPRO_CAMERA_PREVIEW, true)

        //a transcoded preview is drawn frame by frame into the image view instead of the player
        val transcoded = api.isPreviewTranscoded

        previewCardView?.visibility = if (show) View.VISIBLE else View.GONE
        styledPlayerView?.visibility = if (show && !transcoded) View.VISIBLE else View.GONE
        imageView?.visibility = if (show && transcoded) View.VISIBLE else View.INVISIBLE
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

            when {

                !currentTrait.captureVideo -> {
                    controller.getSoundHelper().playShutter()
                    api.capture(getImageRequestData(), currentTrait.saveImage)
                }

                api.state() == GoProApi.ConnectionState.RECORDING -> api.stopRecording()

                else -> api.startRecording(getImageRequestData())
            }
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
                    api.onConnect(devices[selected], this, currentTrait.captureVideo)
                }
            }
            .create()
            .show()
    }

    override fun showSettings() {

        val settingsView = GoProCameraSettingsView(context, currentTrait)

        val dialog = AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.go_pro_trait_settings_title)
            .setView(settingsView)
            .setPositiveButton(android.R.string.ok) { dialog, _ ->
                settingsView.commitChanges()
                dialog.dismiss()
                //before the first frame the connecting ui owns the preview, it applies this once live
                if (api.state() == GoProApi.ConnectionState.STREAMING ||
                    api.state() == GoProApi.ConnectionState.CAPTURING ||
                    api.state() == GoProApi.ConnectionState.RECORDING
                ) applyPreviewVisibility()
            }
            .setNeutralButton(R.string.disconnect) { dialog, _ ->
                settingsView.commitChanges()
                dialog.dismiss()
                showConnectingUi()
                api.teardownAsync()
            }
            .create()

        dialog.show()

        //teardown cancels an in flight capture, which would silently drop the photo
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.isEnabled =
            api.state() != GoProApi.ConnectionState.CAPTURING
    }

    /**
     * GoProApi.Callbacks region. These arrive on background threads.
     */
    override fun onConnectionStateChanged(state: GoProApi.ConnectionState, messageRes: Int?) {

        ui.launch {

            messageRes?.let {
                Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            }

            //every other state, including a failure, ends the recording ui
            setRecordingUi(state == GoProApi.ConnectionState.RECORDING)

            when (state) {

                GoProApi.ConnectionState.DISCONNECTED,
                GoProApi.ConnectionState.ERROR -> setupDisconnected()

                GoProApi.ConnectionState.CONNECTING_BLE,
                GoProApi.ConnectionState.AWAITING_AP,
                GoProApi.ConnectionState.CONNECTING_WIFI,
                GoProApi.ConnectionState.CONNECTED -> {
                    showConnectingUi()
                    showStage(state)
                }

                //teardown takes a few seconds, keep the controls hidden until it completes
                GoProApi.ConnectionState.DISCONNECTING -> showConnectingUi()

                GoProApi.ConnectionState.STREAMING,
                GoProApi.ConnectionState.CAPTURING,
                GoProApi.ConnectionState.RECORDING,
                GoProApi.ConnectionState.PARKED -> Unit
            }
        }
    }

    /**
     * Names the stage on the progress dialog. A generic spinner gives the user nothing to act on
     * when a connection stalls, whereas knowing whether it stopped at bluetooth, at the access
     * point or at the preview points straight at the cause.
     */
    private fun showStage(state: GoProApi.ConnectionState) {

        val messageRes = when (state) {
            GoProApi.ConnectionState.CONNECTING_BLE -> R.string.gopro_stage_bluetooth
            GoProApi.ConnectionState.AWAITING_AP -> R.string.gopro_stage_enabling_ap
            GoProApi.ConnectionState.CONNECTING_WIFI -> R.string.gopro_stage_joining_wifi
            GoProApi.ConnectionState.CONNECTED -> R.string.gopro_stage_starting_preview
            else -> return
        }

        try {
            dialogWaitForStream?.setMessage(context.getString(messageRes))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to update stream dialog message", e)
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

            //once transcoding, the player is never prepared again for this session
            if (api.isPreviewTranscoded) return@launch

            val player = api.createPlayer()

            styledPlayerView?.player = player
            styledPlayerView?.requestFocus()

            //the same player is handed back after every capture, so never stack the listener
            player.removeListener(videoSizeListener)
            player.addListener(videoSizeListener)
        }
    }

    override fun onPreviewFrame(frame: Bitmap) {
        ui.launch {
            imageView?.setImageBitmap(frame)
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
            updateShutterIcon()
        }
    }

    override fun onCaptureFailed(messageRes: Int) {
        ui.launch {
            shutterButton?.isEnabled = true
            updateShutterIcon()
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

        val file: DocumentFile =
            createStreamedFile(data.range, data.trait, data.time) ?: return null

        return object : GoProApi.ImageSink {

            override fun openStream(): OutputStream? =
                this@GoProTraitLayout.openStreamedFile(file)

            override fun commit(bytesWritten: Long) {
                this@GoProTraitLayout.commitStreamedFile(file, data.range, data.trait, data.time)
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
        saveFileNameObservation(data.range, data.trait, model.observationName)
    }

    override fun onBusyStateChanged(isBusy: Int, isEncoding: Int) {
        Log.d(TAG, "Busy state changed: busy state: $isBusy, encoding state: $isEncoding")
    }
}
