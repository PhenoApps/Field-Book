package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.receivers.UsbAttachReceiver
import com.fieldbook.tracker.receivers.UsbDetachReceiver
import com.fieldbook.tracker.utilities.DocumentTreeUtil
import com.fieldbook.tracker.utilities.ExifUtil
import com.fieldbook.tracker.utilities.FileUtil
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.serenegiant.SimpleUVCCameraTextureView
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.phenoapps.adapters.ImageAdapter
import org.phenoapps.androidlibrary.Utils
import org.phenoapps.interfaces.usb.camera.CameraSurfaceListener
import org.phenoapps.interfaces.usb.camera.UsbCameraInterface
import org.phenoapps.receivers.UsbPermissionReceiver
import org.phenoapps.usb.camera.UsbCameraHelper
import java.io.FileNotFoundException

class UsbCameraTraitLayout : BaseTraitLayout, ImageAdapter.ImageItemHandler {

    companion object {
        const val TAG = "UsbTrait"
        const val type = "usb camera"
        private const val CAMERA_DELAY_MS = 2000L
    }

    private var activity: Activity? = null
    private var mUsbPermissionReceiver: UsbPermissionReceiver? = null
    private var mUsbDetachReceiver: UsbDetachReceiver? = null
    private var mUsbAttachReceiver: UsbAttachReceiver? = null
    private var mUsbCameraHelper: UsbCameraHelper? = null
    private var textureView: SimpleUVCCameraTextureView? = null
    private var connectBtn: FloatingActionButton? = null
    private var captureBtn: FloatingActionButton? = null
    private var recyclerView: RecyclerView? = null
    private var previewGroup: Group? = null
    private var constraintLayout: ConstraintLayout? = null

    //zoom buttons
    private var zoomInButton: FloatingActionButton? = null
    private var zoomOutButton: FloatingActionButton? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun layoutId(): Int {
        return R.layout.trait_usb_camera
    }

    override fun setNaTraitsText() {}
    override fun type(): String {
        return type
    }

    override fun init(act: Activity) {

        constraintLayout = act.findViewById(R.id.usb_camera_fragment_cv)
        textureView = act.findViewById(R.id.usb_camera_fragment_tv)
        connectBtn = act.findViewById(R.id.usb_camera_fragment_connect_btn)
        captureBtn = act.findViewById(R.id.usb_camera_fragment_capture_btn)
        recyclerView = act.findViewById(R.id.usb_camera_fragment_rv)
        previewGroup = act.findViewById(R.id.usb_camera_fragment_preview_group)
        zoomInButton = act.findViewById(R.id.usb_camera_fragment_plus_btn)
        zoomOutButton = act.findViewById(R.id.usb_camera_fragment_minus_btn)

        activity = act

        mUsbCameraHelper = (activity as? UsbCameraInterface)?.getCameraHelper()

        recyclerView?.adapter = ImageAdapter(this)

        connectBtn?.setOnClickListener {

            context?.let { ctx ->

                val manager = ctx.getSystemService(Context.USB_SERVICE) as UsbManager

                Log.d(TAG, "manager: $manager")

                val permissionIntent = PendingIntent.getBroadcast(
                    ctx,
                    0,
                    Intent(UsbPermissionReceiver.ACTION_USB_PERMISSION),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                )

                val devices = manager.deviceList.map { it.value }
                Log.d(TAG, "devices: $devices ${devices.size}")

                devices.forEach {
                    Log.d(TAG, "${it.vendorId} ${it.productId}")
                    Log.d(TAG, it.deviceName)
                    manager.requestPermission(it, permissionIntent)
                }
            }
        }

        textureView?.setOnClickListener {

            mUsbCameraHelper?.setFocus()

        }

        zoomOutButton?.setOnClickListener {

            try {

                val current = mUsbCameraHelper?.getZoom() ?: 1

                if (current < Int.MAX_VALUE) {

                    mUsbCameraHelper?.setZoom(current)

                }

            } catch (e: Exception) {

                e.printStackTrace()

                Log.d(TAG, "Something went wrong with zooming USB Camera.")

            }
        }

        zoomInButton?.setOnClickListener {

            try {

                val current = mUsbCameraHelper?.getZoom() ?: 1

                if (current > 1) {

                    mUsbCameraHelper?.setZoom(current - 1)

                }

            } catch (e: Exception) {

                e.printStackTrace()

                Log.d(TAG, "Something went wrong with zooming USB Camera.")

            }
        }

        registerReconnectListener()

        connectBtn?.requestFocus()
    }

    private fun registerReconnectListener() {

        try {

            context?.unregisterReceiver(mUsbPermissionReceiver)

        } catch (ignore: Exception) {}

        mUsbPermissionReceiver = UsbPermissionReceiver {

            Log.d(TAG, "Permission result $it")

            previewGroup?.visibility = View.VISIBLE

            try {

                context.unregisterReceiver(mUsbPermissionReceiver)

            } catch (ignore: Exception) {
            } //might not be registered if already paired

            setup()
        }

        val filter = IntentFilter(UsbPermissionReceiver.ACTION_USB_PERMISSION)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(mUsbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(mUsbPermissionReceiver, filter)
        }
    }

    private fun registerDetachListener() {

        try {

            context?.unregisterReceiver(mUsbDetachReceiver)

        } catch (ignore: Exception) {}

        try {

            val detachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)

            mUsbDetachReceiver = UsbDetachReceiver {

                (context as CollectActivity).usbCameraConnected = false

                Log.d(TAG, "Detaching")

                activity?.runOnUiThread {

                    previewGroup?.visibility = View.GONE

                    registerAttachListener()
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(
                    mUsbDetachReceiver,
                    detachFilter,
                    Context.RECEIVER_NOT_EXPORTED
                )
            } else {
                context.registerReceiver(mUsbDetachReceiver, detachFilter)
            }

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }

    private fun registerAttachListener() {

        try {

            context?.unregisterReceiver(mUsbAttachReceiver)

        } catch (ignore: Exception) {}

        mUsbAttachReceiver = UsbAttachReceiver {

            Log.d(TAG, "Usb attach")

            previewGroup?.visibility = VISIBLE

            try {

                context.unregisterReceiver(mUsbAttachReceiver)

            } catch (ignore: Exception) {
            } //might not be registered if already paired
        }

        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(mUsbAttachReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            context.registerReceiver(mUsbAttachReceiver, filter)
        }
    }

    private fun setup() {

        Log.d(TAG, "Setup")

        connectBtn?.visibility = View.GONE

        previewGroup?.visibility = View.VISIBLE

        textureView?.surfaceTextureListener = object : CameraSurfaceListener {

            override fun onSurfaceTextureAvailable(
                surface: SurfaceTexture,
                width: Int,
                height: Int
            ) {

                Log.d(TAG, "Surface available..")

                initPreview()

                (context as CollectActivity).usbCameraConnected = true

            }

            override fun onSurfaceTextureUpdated(surfaceTexture: SurfaceTexture) {}
        }

        if (textureView?.isAvailable == true) {

            Log.d(TAG, "Surface available already.")

            initPreview()

        }
    }

    private fun initPreview() {

        Log.d(TAG, "Init preview")

        registerDetachListener()

        textureView?.let { tv ->

            mUsbCameraHelper?.let { helper ->

                previewGroup?.visibility = View.VISIBLE

                Log.d(TAG, "Helper init")

                helper.init(tv) { ratio ->

                    activity?.runOnUiThread {

                        textureView?.setAspectRatio(ratio)

                        captureBtn?.invalidate()

                    }
                }

                captureBtn?.setOnClickListener {

                    if (!isLocked) {

                        captureBtn?.isEnabled = false

                        runBlocking {

                            Log.d(TAG, "Capture click.")

                            saveBitmapToStorage()

                            delay(CAMERA_DELAY_MS)

                            scrollToLast()

                        }
                    }
                }
            }
        }
    }

    private fun scrollToLast() {

        try {

            recyclerView?.postDelayed({

                val pos = recyclerView?.adapter?.itemCount ?: 1

                recyclerView?.scrollToPosition(pos - 1)

                captureBtn?.isEnabled = true

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

        if ((context as CollectActivity).usbCameraConnected) {

            try {

                setup()

            } catch (e: Exception) {

                e.printStackTrace()

            }
        }

        super.loadLayout()
    }

    override fun deleteTraitListener() {

        if (!isLocked) {

            (recyclerView?.layoutManager as? LinearLayoutManager)?.findFirstCompletelyVisibleItemPosition()?.let { index ->

                if (index > -1) {

                    (recyclerView?.adapter as? ImageAdapter)?.currentList?.get(index)?.let { model ->

                        showDeleteImageDialog(model)

                    }
                }
            }
        }
    }

    private fun showDeleteImageDialog(model: ImageAdapter.Model) {

        if (!isLocked) {
            context.contentResolver.openInputStream(Uri.parse(model.uri)).use { input ->

                val imageView = ImageView(context)
                imageView.setImageBitmap(BitmapFactory.decodeStream(input))

                AlertDialog.Builder(context, R.style.AppAlertDialog)
                    .setTitle(R.string.trait_usb_camera_delete_photo)
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

    private fun saveBitmapToStorage() {

        //get current trait's trait name, use it as a plot_media directory
        currentTrait.name?.let { traitName ->

            val sanitizedTraitName = FileUtil.sanitizeFileName(traitName)

            val traitDbId = currentTrait.id

            //get the bitmap from the texture view, only use it if its not null
            textureView?.bitmap?.let { bmp ->

                DocumentTreeUtil.getFieldMediaDirectory(context, sanitizedTraitName)
                    ?.let { usbPhotosDir ->

                        val plot = collectActivity.observationUnit
                        val studyId = collectActivity.studyId
                        val time = Utils.getDateTime()
                        val name = "${sanitizedTraitName}_${plot}_$time.png"

                    usbPhotosDir.createFile("*/*", name)?.let { file ->

                        context.contentResolver.openOutputStream(file.uri)?.let { output ->

                            //if sdk > 24, can write exif information to the image
                            //goal is to encode observation variable model into the user comments
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

                                ExifUtil.saveVariableUnitModelToExif(
                                    context,
                                    (controller.getContext() as CollectActivity).person,
                                    time,
                                    database.getStudyById(studyId),
                                    database.getObservationUnitById(currentRange.plot_id),
                                    database.getObservationVariableById(currentTrait.id),
                                    file.uri
                                )
                            }

                            bmp.compress(Bitmap.CompressFormat.PNG, 100, output)

                            database.insertObservation(
                                plot, traitDbId, type, file.uri.toString(),
                                (activity as? CollectActivity)?.person,
                                (activity as? CollectActivity)?.locationByPreferences, "", studyId,
                                null,
                                null,
                                null
                            )

                            //if sdk > 24, can write exif information to the image
                            //goal is to encode observation variable model into the user comments
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

                                ExifUtil.saveJsonToExif(
                                    context,
                                    currentTrait,
                                    file.uri
                                )

                            }
                        }
                    }

                    loadAdapterItems()
                }
            }
        }
    }

    private fun loadAdapterItems() {

        val thumbnailModels = getImageObservations().mapNotNull {

            var model: ImageAdapter.Model? = null

            try {

                DocumentsContract.getDocumentThumbnail(context.contentResolver,
                    Uri.parse(it.value), Point(256, 256), null)?.let { bmp ->

                    model = ImageAdapter.Model(it.value, bmp)

                }

            } catch (f: FileNotFoundException) {

                f.printStackTrace()

                model = null
            }

            model
        }

        (recyclerView?.adapter as? ImageAdapter)?.submitList(thumbnailModels)
    }

    private fun getImageObservations(): Array<ObservationModel> {

        val traitDbId = collectActivity.traitDbId.toInt()
        val plot = collectActivity.observationUnit
        val studyId = collectActivity.studyId

        return database.getAllObservations(studyId).filter {
            it.observation_variable_db_id == traitDbId && it.observation_unit_id == plot
        }.toTypedArray()
    }

    private fun deleteItem(model: ImageAdapter.Model) {

        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()

        //get current trait's trait name, use it as a plot_media directory
        currentTrait?.name?.let { traitName ->

            val plot = currentRange.plot_id

            val traitDbId = currentTrait.id

            getImageObservations().firstOrNull { it.value == model.uri }?.let { observation ->

                try {

                    DocumentFile.fromSingleUri(context, Uri.parse(observation.value))
                        ?.let { image ->

                            val result = image.delete()

                            if (result) {

                                database.deleteTraitByValue(
                                    studyId,
                                    plot,
                                    traitDbId,
                                    image.uri.toString()
                                )

                                loadAdapterItems()

                            } else {

                                collectActivity.runOnUiThread {

                                    Toast.makeText(context, R.string.photo_failed_to_delete, Toast.LENGTH_SHORT).show()

                                }
                            }
                        }

                } catch (e: Exception) {

                    Log.e(TAG, "Failed to delete images.", e)

                }
            }
        }
    }

    override fun onItemClicked(model: ImageAdapter.Model) {

        if (!isLocked) {

            getImageObservations().firstOrNull { it.value == model.uri }?.let { observation ->

                DocumentFile.fromSingleUri(context, Uri.parse(observation.value))?.let { image ->

                    activity?.startActivity(Intent(Intent.ACTION_VIEW, image.uri).also {
                        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                }
            }
        }
    }

    override fun onItemDeleted(model: ImageAdapter.Model) {

        showDeleteImageDialog(model)
    }

    override fun refreshLock() {
        super.refreshLock()
        (context as CollectActivity).traitLockData()
    }
}