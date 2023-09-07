package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.widget.AppCompatImageButton
import androidx.core.graphics.scale
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.ImageTraitAdapter
import com.fieldbook.tracker.database.dao.ObservationDao
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.DocumentTreeUtil
import com.fieldbook.tracker.utilities.GoProWrapper
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.trackselection.TrackSelector
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DefaultDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.phenoapps.androidlibrary.Utils
import org.phenoapps.fragments.gopro.GoProGatt
import org.phenoapps.fragments.gopro.GoProGattInterface
import org.phenoapps.fragments.gopro.GoProHelper
import org.phenoapps.interfaces.gatt.GattCallbackInterface
import org.phenoapps.interfaces.security.SecureBluetooth

//TODO close connenction on collect activity finsishes onDestroy
//TODO leave stream at top of layout -> capture overlayed on the preview bottom or corner -> gallery below the preview -> connect button at bottom -> hide everything if not connected other than gallery   (DONE)
//todo gopro different versions
////todo aim for hero 11 -> if not 11 then say a message with a link to an email
//TODO improve time between taking a picture and it showing in match list items with timestamp when doing media query
//TODO : add animated progress bar to dummy image after it is shuttered'                                                                                                                                    (DONE)
//TODO: shutter on the gopro detected in FB
//TODO test usb camera trait with UVC.
//TODO: reopening preview after going back to config

class GoProTraitLayout :
    BaseTraitLayout,
    ImageTraitAdapter.ImageItemHandler,
    GattCallbackInterface,
    GoProGattInterface,
    GoProGatt.GoProGattController,
    GoProHelper.OnGoProStreamReady{

    //go pro specific collector interface
    interface GoProCollector {
        fun wrapper(): GoProWrapper
        fun advisor(): SecureBluetooth
    }

    companion object {
        const val TAG = "GoProTrait"
        const val type = "gopro"
        private const val CAMERA_DELAY_MS = 10000L
    }

    private var activity: Activity? = null

    //ui components
    private lateinit var playerView: StyledPlayerView
    private lateinit var imageRecyclerView: RecyclerView

    //buttons
    private lateinit var connectButton: AppCompatImageButton
    private lateinit var shutterButton: AppCompatImageButton

    //exoplayer instance
    private var player: ExoPlayer? = null
    private var streamStarted = false

    //collect activity controller
    private lateinit var collector: GoProCollector

    private val scope by lazy { CoroutineScope(Dispatchers.IO) }

    private val wrapper by lazy { collector.wrapper() }

    private val helper by lazy { wrapper.helper }

    private val gatt by lazy { wrapper.gatt }

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
                    helper?.requestStream()
                }

                Player.STATE_READY -> {
                    Log.d(TAG, "Player Ready")
                    initializeCameraShutterButton()
                    initializeDisconnectButton()
                    playerView.visibility = View.VISIBLE
                    shutterButton.visibility = View.VISIBLE
                }
            }
        }
    }

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun layoutId(): Int {
        return R.layout.trait_go_pro
    }

    override fun setNaTraitsText() {}
    override fun type(): String {
        return type
    }

    private fun initWork(act: Activity) {

        collector = (act as GoProCollector)

        playerView = act.findViewById(R.id.go_pro_pv)
        connectButton = act.findViewById(R.id.go_pro_connect_btn)
        shutterButton = act.findViewById(R.id.go_pro_capture_btn)
        imageRecyclerView = act.findViewById(R.id.go_pro_rv)

        activity = act

        //set control buttons gone
        shutterButton.visibility = View.GONE

        //set player gone
        playerView.visibility = View.GONE

        //set images gone
        //imageRecyclerView.visibility = View.GONE

        connectButton.visibility = View.VISIBLE

        initializeConnectButton()
        loadAdapterItems()

        detectActiveConnection()

    }

    override fun init(act: Activity) {

        //slight delay to make navigation a bit faster
        Handler(Looper.getMainLooper()).postDelayed({

            initWork(act)

        }, 500)
    }

    private fun detectActiveConnection() {

        createPlayer()

    }

    private fun initializeConnectButton() {

        //reset ui component states
        player?.stop()
        player?.release()
        player?.clearMediaItems()
        player?.clearVideoSurface()
        player = null
        playerView.player = null

        //buttons other than connect are gone until feature is available
        shutterButton.visibility = View.GONE

        //reset global flags
        this.streamStarted = false

        //start connection flow when button is pressed
        connectButton.setOnClickListener {

            connect()
        }
    }

    private fun initializeDisconnectButton() {

        connectButton.setOnClickListener {

            clearResources()

            initializeConnectButton()
        }
    }

    private fun awaitNetworkConnectionDialog() {

        if (activity?.window?.isActive == true) {

            var skipBssid = false
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                skipBssid = true
            }

            if (skipBssid || gatt.bssid != null) {

                enableAp()

                gatt.ssid?.let { s ->

                    gatt.password?.let { p ->

                        val dialog = androidx.appcompat.app.AlertDialog.Builder(
                            context,
                            R.style.AlertDialogStyle
                        )
                            .setTitle(context.getString(R.string.trait_go_pro_await_ap_title))
                            .setMessage(context.getString(R.string.trait_go_pro_await_ap_message, s, p))
                            .setCancelable(true)
                            .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                                dialog.dismiss()
                            }
                            .setNeutralButton(context.getString(R.string.trait_go_pro_await_ap_neutral)) { _, _ ->
                                helper?.openWifiSettings()
                            }
                            .setOnDismissListener {
                                Log.d(TAG, "$s connection attempting...")
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

                        dialog.show()

                        helper?.connectToGoProWifi(dialog, s, p, gatt.bssid) {

                            activity?.runOnUiThread {

                                helper?.requestStream()

                            }
                        }
                    }
                }
            }
        }
    }

    private fun onShutter() {

        shutterButton.isEnabled = false

        val plot = currentRange.plot_id
        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
        val traitName = currentTrait.trait
        val traitType = type
        val time = Utils.getDateTime()
        val name = "${traitName}_${plot}_$time.png"

        //load data here
        val data = hashMapOf(
            "studyId" to studyId,
            "plot" to plot,
            "traitName" to traitName,
            "traitType" to traitType,
            "name" to name
        )

        saveDummyObservation(data)

        Handler(Looper.getMainLooper()).postDelayed({

            shutterButton.isEnabled = true

            helper?.queryMedia(data)

        }, CAMERA_DELAY_MS)
    }

    private fun initializeCameraShutterButton() {

        shutterButton.visibility = View.VISIBLE

        shutterButton.setOnClickListener {

            shutterOn()

            onShutter()
        }
    }

    override fun onExit() {
        super.onExit()
        player?.stop()
        player?.release()
        player = null
    }

    private fun createPlayer() {

        context?.let { ctx ->

            val ffmpegOutputUri = "udp://0.0.0.0:8555"

            //Max. Buffer: The maximum duration, in milliseconds, of the media the player is attempting to buffer. Once the buffer reaches Max Buffer, it will stop filling it up.
            //min Buffer: The minimum length of media that the player will ensure is buffered at all times, in milliseconds.
            //Playback Buffer: The default amount of time, in milliseconds, of media that needs to be buffered in order for playback to start or resume after a user action such as a seek.
            //Buffer for playback after rebuffer: The duration of the media that needs to be buffered in order for playback to continue after a rebuffer, in milliseconds.

            val loadControl: DefaultLoadControl = DefaultLoadControl.Builder()
                .setPrioritizeTimeOverSizeThresholds(true)
                .setBufferDurationsMs(500, 1000, 500, 500)
                .build()

            val trackSelector: TrackSelector = DefaultTrackSelector(ctx)
            val mediaSource: MediaSource =
                ProgressiveMediaSource.Factory(DefaultDataSource.Factory(ctx)).createMediaSource(
                    MediaItem.fromUri(
                        Uri.parse(ffmpegOutputUri)
                    )
                )

            player?.stop()
            player?.release()
            player = null

            player = ExoPlayer.Builder(ctx)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .build()

            playerView.player = player.also {
                it?.addListener(playerListener)
                it?.setMediaSource(mediaSource)
                it?.playWhenReady = true
                it?.prepare()
            }

            playerView.requestFocus()

            Log.i(TAG, "Player created")
        }
    }

    private var credentialsDialog: androidx.appcompat.app.AlertDialog? = null

    private fun awaitCredentialsDialog() {

        if (activity?.window?.isActive == true) {

            if (credentialsDialog?.isShowing == true) credentialsDialog?.dismiss()

            credentialsDialog = androidx.appcompat.app.AlertDialog.Builder(
                context,
                R.style.AlertDialogStyle
            )
                .setTitle(context.getString(R.string.trait_go_pro_await_ble_title))
                .setMessage(context.getString(R.string.trait_go_pro_await_ble_message))
                .setCancelable(true)
                .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                    dialog.dismiss()
                }
                .create()

            credentialsDialog?.setView(ProgressBar(context).also {
                it.isIndeterminate = true
                it.layoutParams = LayoutParams(
                    LayoutParams.WRAP_CONTENT,
                    LayoutParams.WRAP_CONTENT
                )
                it.layout(16, 16, 16, 16)
            })

            credentialsDialog?.show()
        }
    }

    private fun connect() {

        //ensure bluetooth is enabled
        collector.advisor().withNearby { adapter ->

            if (helper?.isBluetoothEnabled(adapter) != true) {

                //if not enabled, start intent for settings
                context?.startActivity(
                    Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                )

            } else {

                helper?.registerReceivers()

                activity?.let { act ->

                    //start discovering and find a go pro device
                    adapter.startDiscovery()

                    fun onSelected(device: BluetoothDevice?) {

                        if (device != null) {

                            helper?.connectToGoPro(
                                device,
                                wrapper.gatt.callback
                            )

                            adapter.cancelDiscovery()

                            //show progress bar dialog until credentials are established
                            awaitCredentialsDialog()
                        }
                    }

                    val dialog = androidx.appcompat.app.AlertDialog.Builder(
                        context,
                        R.style.AlertDialogStyle
                    )
                        .setTitle(context.getString(R.string.trait_go_pro_await_device_title))
                        .setCancelable(true)
                        .setSingleChoiceItems(
                            helper?.goProDevices?.map { it.name }?.distinct()?.toTypedArray() ?: arrayOf(),
                            -1
                        ) { dialog, which ->
                            onSelected(helper?.goProDevices?.get(which))
                            dialog.dismiss()
                        }
                        .setNegativeButton(android.R.string.cancel) { dialog, _ ->
                            onSelected(null)
                            dialog.dismiss()
                        }
                        .setOnDismissListener {
                            helper?.bluetoothSearchJob?.cancel()
                        }
                        .create()

                    dialog.show()

                    helper?.searchForBluetoothGoPro(act, dialog)
                }
            }
        }
    }

    private fun scrollToLast() {

        try {

            imageRecyclerView.postDelayed({

                val pos = imageRecyclerView.adapter?.itemCount ?: 1

                imageRecyclerView.scrollToPosition(pos - 1)

            }, 500L)

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }

    override fun loadLayout() {

        //slight delay to make navigation a bit faster
        Handler(Looper.getMainLooper()).postDelayed({

            loadAdapterItems()

        }, 500)

        super.loadLayout()
    }

    override fun deleteTraitListener() {

        if (!isLocked) {

            (imageRecyclerView.layoutManager as? LinearLayoutManager)
                ?.findFirstCompletelyVisibleItemPosition()?.let { index ->

                    if (index > -1) {

                        (imageRecyclerView.adapter as? ImageTraitAdapter)
                            ?.currentList?.get(index)?.let { model ->

                                showDeleteImageDialog(model)

                            }
                    }
                }
        }
    }

    private fun showDeleteImageDialog(model: ImageTraitAdapter.Model) {

        if (!isLocked) {

            context.contentResolver.openInputStream(Uri.parse(model.uri)).use { input ->

                val imageView = ImageView(context)

                val bmp = BitmapFactory.decodeStream(input)

                val scaled = bmp.scale(512, 512, true)

                imageView.setImageBitmap(scaled)

                AlertDialog.Builder(context)
                    .setTitle(R.string.trait_go_pro_camera_delete_photo_title)
                    .setOnCancelListener { dialog -> dialog.dismiss() }
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->

                        dialog.dismiss()

                        deleteItem(model)

                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                    .setView(imageView)
                    .show()
            }
        }
    }

    private fun saveDummyObservation(data: Map<String, String>) {

        scope.launch {

            withContext(Dispatchers.IO) {

                //get current trait's trait name, use it as a plot_media directory

                val plot = data["plot"]
                val studyId = data["studyId"]
                val name = data["name"]

                database.insertObservation(
                    plot,
                    data["traitName"],
                    data["traitType"],
                    name,
                    prefs.getString(GeneralKeys.FIRST_NAME, "") + " "
                            + prefs.getString(GeneralKeys.LAST_NAME, ""),
                    (activity as? CollectActivity)?.locationByPreferences,
                    "",
                    studyId,
                    null,
                    null,
                    null
                )

                activity?.runOnUiThread {

                    loadAdapterItems()

                    scrollToLast()
                }
            }
        }
    }

    private fun saveBitmapToStorage(bmp: Bitmap, data: Map<String, String>) {

        scope.launch {

            withContext(Dispatchers.IO) {

                //get current trait's trait name, use it as a plot_media directory
                currentTrait.trait?.let { traitName ->

                    DocumentTreeUtil.getFieldMediaDirectory(context, traitName)
                        ?.let { usbPhotosDir ->

                            val plot = data["plot"]

                            val studyId = data["studyId"]

                            val name = data["name"] ?: String()

                            usbPhotosDir.createFile("*/*", name)?.let { file ->

                                context.contentResolver.openOutputStream(file.uri)?.let { output ->

                                    bmp.compress(Bitmap.CompressFormat.PNG, 100, output)

                                    database.deleteTraitByValue(studyId, plot, traitName, name)

                                    database.insertObservation(
                                        plot,
                                        data["traitName"],
                                        data["traitType"],
                                        file.uri.toString(),
                                        prefs.getString(GeneralKeys.FIRST_NAME, "") + " "
                                                + prefs.getString(GeneralKeys.LAST_NAME, ""),
                                        (activity as? CollectActivity)?.locationByPreferences,
                                        "",
                                        studyId,
                                        null,
                                        null,
                                        null
                                    )

                                    activity?.runOnUiThread {

                                        loadAdapterItems()

                                        scrollToLast()
                                    }
                                }
                            }
                        }
                }
            }
        }
    }

    private fun loadAdapterItems() {

        Log.d(TAG, "loadAdapterItems")

        val studyId = (context as CollectActivity).studyId

        imageRecyclerView.adapter = ImageTraitAdapter(context, this, hasProgressBar = true)

        currentTrait.trait?.let { traitName ->

            try {

                val plot = currentRange.plot_id
                val toc = System.currentTimeMillis()
                val uris = database.getAllObservations(studyId, plot, traitName)
                val tic = System.currentTimeMillis()

                Log.d(TAG, "Photo trait query time ${uris.size} photos: ${(tic - toc) * 1e-3}")

                val models =
                    uris.mapIndexed { index, model -> ImageTraitAdapter.Model(model.value, index) }

                activity?.runOnUiThread {
                    if (models.isNotEmpty()) {
                        imageRecyclerView.visibility = View.VISIBLE
                        (imageRecyclerView.adapter as ImageTraitAdapter).submitList(models)
                        imageRecyclerView.adapter?.notifyItemRangeChanged(0, models.size)
                    } else imageRecyclerView.visibility = View.GONE
                }

            } catch (e: Exception) {

                e.printStackTrace()

            }
        }
    }

    private fun deleteItem(model: ImageTraitAdapter.Model) {

        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()

        //get current trait's trait name, use it as a plot_media directory
        currentTrait?.trait?.let { traitName ->

            DocumentTreeUtil.getFieldMediaDirectory(context, traitName)?.let { fieldDir ->

                val plot = currentRange.plot_id

                DocumentTreeUtil.getPlotMedia(fieldDir, plot, ".png").let { highResImages ->

                    highResImages.firstOrNull {
                        it.name == (DocumentFile.fromSingleUri(
                            context,
                            Uri.parse(model.uri)
                        )?.name ?: String())
                    }?.let { image ->

                        try {

                            image.delete()
                            DocumentFile.fromSingleUri(context, Uri.parse(model.uri))?.delete()

                            ObservationDao.deleteTraitByValue(
                                studyId,
                                plot,
                                traitName,
                                image.uri.toString()
                            )

                            loadAdapterItems()

                        } catch (e: Exception) {

                            Log.e(TAG, "Failed to delete images.", e)

                        }
                    }
                }
            }
        }
    }

    override fun refreshLock() {
        super.refreshLock()
        (context as CollectActivity).traitLockData()
    }

    override fun onApRequested() {}

    override fun onBoardType(boardType: String) {}

    override fun onBssid(wifiBSSID: String) {}

    /**
     * Collect activity callback region
     */
    override fun onCredentialsAcquired() {

        try {

            Log.d(TAG, "onCredentialsAcquired")

            credentialsDialog?.dismiss()

            awaitNetworkConnectionDialog()

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }

    override fun onFirmware(firmware: String) {}

    override fun onModelId(modelID: Int) {}

    override fun onModelName(modelName: String) {
        if ("HERO11 Black" !in modelName) {
            activity?.runOnUiThread {
                Toast.makeText(context,
                    activity?.getString(R.string.go_pro_layout_black_11_not_detected),
                    Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onSerialNumber(serialNumber: String) {}

    override fun onSsid(wifiSSID: String) {}

    override fun onStreamReady() {

        try {

            Log.d(TAG, "onStreamReady")

            createPlayer()

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }

    override fun onImageRequestReady(bitmap: Bitmap, data: Map<String, String>) {

        try {

            saveBitmapToStorage(bitmap, data)

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }

    override fun onItemClicked(model: ImageTraitAdapter.Model) {

        try {

            if (!isLocked) {

                //get current trait's trait name, use it as a plot_media directory
                currentTrait?.trait?.let { traitName ->

                    DocumentTreeUtil.getFieldMediaDirectory(context, traitName)?.let { fieldDir ->

                        val plot = currentRange.plot_id

                        DocumentTreeUtil.getPlotMedia(fieldDir, plot, ".png").let { highResImages ->

                            highResImages.firstOrNull {
                                it.name == (DocumentFile.fromSingleUri(
                                    context,
                                    Uri.parse(model.uri)
                                )?.name ?: String())
                            }?.let { image ->

                                activity?.startActivity(Intent(Intent.ACTION_VIEW, image.uri).also {
                                    it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                })
                            }
                        }
                    }
                }
            }

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }

    override fun onItemDeleted(model: ImageTraitAdapter.Model) {

        try {

            showDeleteImageDialog(model)

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }

    private fun stopAp() {

        helper?.stopStream()

        playerView.player?.stop()
        playerView.player?.release()

        disableAp()
    }

    private fun clearResources() {

        stopAp()

        activity?.runOnUiThread {
            if (player != null) {
                player!!.stop()
                player!!.release()
            }
        }

        gatt.clear()
    }


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
}