package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.SurfaceTexture
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.Group
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.database.dao.ObservationDao
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.receivers.UsbAttachReceiver
import com.fieldbook.tracker.receivers.UsbDetachReceiver
import com.fieldbook.tracker.utilities.DocumentTreeUtil
import com.serenegiant.SimpleUVCCameraTextureView
import com.serenegiant.usb.UVCCamera
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.phenoapps.adapters.ImageAdapter
import org.phenoapps.androidlibrary.Utils
import org.phenoapps.interfaces.usb.camera.CameraSurfaceListener
import org.phenoapps.interfaces.usb.camera.UsbCameraInterface
import org.phenoapps.receivers.UsbPermissionReceiver
import org.phenoapps.usb.camera.UsbCameraHelper
import kotlin.math.abs

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
    private var connectBtn: ImageButton? = null
    private var captureBtn: ImageButton? = null
    private var recyclerView: RecyclerView? = null
    private var previewGroup: Group? = null
    private var constraintLayout: ConstraintLayout? = null
    //zoom buttons
    private var zoomInButton: ImageButton? = null
    private var zoomOutButton: ImageButton? = null

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

                val permissionIntent = PendingIntent.getBroadcast(
                    ctx,
                    0,
                    Intent(UsbPermissionReceiver.ACTION_USB_PERMISSION),
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
                )

                val devices = manager.deviceList.map { it.value }

                devices.forEach {
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

            } catch (ignore: Exception) {} //might not be registered if already paired

            setup()
        }

        val filter = IntentFilter(UsbPermissionReceiver.ACTION_USB_PERMISSION)

        context.registerReceiver(mUsbPermissionReceiver, filter)
    }

    private fun registerDetachListener() {

        try {

            context?.unregisterReceiver(mUsbDetachReceiver)

        } catch (ignore: Exception) {}

        try {

            val detachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)

            mUsbDetachReceiver = UsbDetachReceiver {

                Log.d(TAG, "Detaching")

                activity?.runOnUiThread {

                    previewGroup?.visibility = View.GONE

                    registerAttachListener()
                }
            }

            context.registerReceiver(mUsbDetachReceiver, detachFilter)

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

            } catch (ignore: Exception) {} //might not be registered if already paired
        }

        val filter = IntentFilter(UsbManager.ACTION_USB_DEVICE_ATTACHED)

        context.registerReceiver(mUsbAttachReceiver, filter)
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

                AlertDialog.Builder(context)
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
        currentTrait.trait?.let { traitName ->

            //get the bitmap from the texture view, only use it if its not null
            textureView?.bitmap?.let { bmp ->

                DocumentTreeUtil.getFieldMediaDirectory(context, traitName)?.let { usbPhotosDir ->

                    val plot = currentRange.plot_id

                    val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()

                    val time = Utils.getDateTime()

                    val name = "${traitName}_${plot}_$time.png"

                    usbPhotosDir.createFile("*/*", name)?.let { file ->

                        context.contentResolver.openOutputStream(file.uri)?.let { output ->

                            bmp.compress(Bitmap.CompressFormat.PNG, 100, output)

                            database.insertObservation(
                                plot, traitName, type, file.uri.toString(),
                                prefs.getString(GeneralKeys.FIRST_NAME, "") + " "
                                        + prefs.getString(GeneralKeys.LAST_NAME, ""),
                                (activity as? CollectActivity)?.locationByPreferences, "", studyId,
                                null,
                                null,
                                null
                            )

                            createThumbnail(traitName, name, bmp)
                        }
                    }
                }
            }
        }
    }

    private fun createThumbnail(traitName: String, name: String, bitmap: Bitmap) {

        DocumentTreeUtil.getThumbnailsDir(context, traitName)?.let { thumbnails ->

            var thumbnailWidth = resources.getInteger(R.integer.thumbnailWidth)
            var thumbnailHeight = resources.getInteger(R.integer.thumbnailHeight)
            var aspectRatio = mUsbCameraHelper?.aspectRatio ?: (UVCCamera.DEFAULT_PREVIEW_WIDTH / UVCCamera.DEFAULT_PREVIEW_HEIGHT).toDouble()

            val aspectKeys = resources.getStringArray(R.array.aspect_ratio_keys)
            val aspectValues = resources.getStringArray(R.array.aspect_ratio_values)
            aspectKeys.minByOrNull { abs(it.toDouble() - aspectRatio) }?.let { closest ->
                val index = aspectKeys.indexOf(closest)
                if (index < aspectValues.size) {
                    val (width, height) = aspectValues[index].split("x")
                    thumbnailWidth = width.toInt()
                    thumbnailHeight = height.toInt()
                    aspectRatio = thumbnailWidth / thumbnailHeight.toDouble()

                    Log.d(TAG, "Chosen thumbnail: $thumbnailWidth x $thumbnailHeight a.r: $aspectRatio")
                }
            }

            val thumbnailBitmap = Bitmap.createScaledBitmap(bitmap, thumbnailWidth, thumbnailHeight, true)

            thumbnails.createFile("image/png", name)?.let { thumbnail ->

                context?.contentResolver?.openOutputStream(thumbnail.uri)?.use { output ->

                    thumbnailBitmap.compress(Bitmap.CompressFormat.PNG, 100, output)

                    loadAdapterItems()
                }
            }
        }
    }

    private fun loadAdapterItems() {

        //get current trait's trait name, use it as a plot_media directory
        currentTrait?.trait?.let { traitName ->

            DocumentTreeUtil.getThumbnailsDir(context, traitName)?.let { thumbnailDir ->

                val plot = currentRange.plot_id

                val images = DocumentTreeUtil.getPlotMedia(thumbnailDir, plot, ".png")

                with (context.contentResolver) {

                    (recyclerView?.adapter as? ImageAdapter)?.submitList(images.map {
                        openInputStream(it.uri).use { input ->
                            ImageAdapter.Model(it.uri.toString(), BitmapFactory.decodeStream(input))
                        }
                    })
                }
            }
        }
    }

    private fun deleteItem(model: ImageAdapter.Model) {

        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()

        //get current trait's trait name, use it as a plot_media directory
        currentTrait?.trait?.let { traitName ->

            DocumentTreeUtil.getFieldMediaDirectory(context, traitName)?.let { fieldDir ->

                val plot = currentRange.plot_id

                DocumentTreeUtil.getPlotMedia(fieldDir, plot, ".png").let { highResImages ->

                    highResImages.firstOrNull { it.name == (DocumentFile.fromSingleUri(context, Uri.parse(model.uri))?.name ?: String()) }?.let { image ->

                        try {

                            image.delete()
                            DocumentFile.fromSingleUri(context, Uri.parse(model.uri))?.delete()

                            ObservationDao.deleteTraitByValue(studyId, plot, traitName, image.uri.toString())

                            loadAdapterItems()

                        } catch (e: Exception) {

                            Log.e(TAG, "Failed to delete images.", e)

                        }
                    }
                }
            }
        }
    }

    override fun onItemClicked(model: ImageAdapter.Model) {

        if (!isLocked) {

            //get current trait's trait name, use it as a plot_media directory
            currentTrait?.trait?.let { traitName ->

                DocumentTreeUtil.getFieldMediaDirectory(context, traitName)?.let { fieldDir ->

                    val plot = currentRange.plot_id

                    DocumentTreeUtil.getPlotMedia(fieldDir, plot, ".png").let { highResImages ->

                        highResImages.firstOrNull { it.name == (DocumentFile.fromSingleUri(context, Uri.parse(model.uri))?.name ?: String()) }?.let { image ->

                            activity?.startActivity(Intent(Intent.ACTION_VIEW, image.uri).also {
                                it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            })
                        }
                    }
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