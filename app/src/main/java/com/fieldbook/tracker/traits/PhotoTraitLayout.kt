package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.util.AttributeSet
import android.util.Size
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.PreviewView
import androidx.core.widget.NestedScrollView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CameraActivity
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.ImageAdapter
import com.fieldbook.tracker.database.internalTimeFormatter
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.provider.GenericFileProvider
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.utilities.camera.CameraCapabilities
import com.fieldbook.tracker.utilities.camera.CameraControlSettings
import com.fieldbook.tracker.utilities.camera.DualCaptureCallback
import com.fieldbook.tracker.views.CameraTraitSettingsView
import org.threeten.bp.OffsetDateTime
import java.io.File
import java.util.concurrent.ExecutorService

open class PhotoTraitLayout : CameraTrait {

    companion object {
        const val TAG = "PhotoTrait"
        const val type = "photo"
        const val PICTURE_REQUEST_CODE = 252
    }

    enum class Mode {
        PREVIEW,
        NO_PREVIEW
    }

    private var supportedResolutions: List<Size> = listOf()

    private var cameraCapabilities: CameraCapabilities = CameraCapabilities.NONE

    protected var boundCamera: Camera? = null

    protected var previewHolder: ImageAdapter.PreviewViewHolder? = null
    private var isCameraActive = false

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun type() = type

    /**
     * Whether this trait may capture RAW (DNG) files alongside JPEGs.
     */
    protected open fun supportsRawCapture() = true

    /**
     * The advanced camera controls requested by the current trait.
     */
    protected open fun controlSettings() = CameraControlSettings.from(currentTrait, supportsRawCapture())

    override fun init(act: Activity) {
        super.init(act)
        isCameraActive = false
    }

    private fun displayPreviewMode(mode: Mode) {

        controller.getCameraXFacade().await(context) {
            if (mode == Mode.NO_PREVIEW) {
                bindNoPreviewLifecycle()
            } else bindPreviewLifecycle()
        }
    }

    private fun setupCaptureButton(takePictureCallback: () -> Unit) {

        shutterButton?.setOnClickListener {

            if (!isLocked) {

                if (checkPictureLimit()) {

                    controller.getSoundHelper().playShutter()

                    takePictureCallback()

                } else {

                    Utils.makeToast(
                        context,
                        context.getString(R.string.traits_create_photo_maximum)
                    )
                }
            }
        }
    }

    private fun setup() {

        //we are limited to having everything extend linear layout, there are some cases
        //where the background thread has not loaded all the views and may be null,
        //so reload until we have the preview model
        awaitPreviewHolder {

            scrollToLast()

        }

        shutterButton?.visibility = VISIBLE
        settingsButton?.visibility = VISIBLE
        settingsButton?.isEnabled = true
        settingsButton?.setOnClickListener {
            showSettings()
        }

        connectBtn?.visibility = GONE

        setupCaptureButton {

            capture()

        }

        if (!isCameraActive) {
            isCameraActive = true
            onSettingsChanged()
        }
    }

    @OptIn(ExperimentalCamera2Interop::class)
    private fun bindCameraForInformation() {

        controller.getCameraXFacade().bindIdentity({ camera, sizes ->

            supportedResolutions = sizes

            cameraCapabilities = CameraCapabilities.from(camera.cameraInfo)

            settingsButton?.isEnabled = true

        }) {
            (context as CollectActivity).finishActivity(Activity.RESULT_CANCELED)
        }
    }

    private fun bindNoPreviewLifecycle() {

        previewHolder?.previewView?.visibility = GONE
        previewHolder?.embiggenButton?.visibility = GONE

        try {

            val resolution = getSupportedResolutionByPreferences()

            controller.getCameraXFacade().bindFrontCapture(resolution, controlSettings()) { camera, executor, capture ->

                boundCamera = camera

                notifyIfRawUnavailable()

                setupCaptureUi(executor, capture)
            }

        } catch (e: IllegalArgumentException) {

            e.printStackTrace()

        }
    }

    private fun setupCaptureUi(executorService: ExecutorService?, capture: ImageCapture) {

        setupCaptureButton {

            val file = File(context.cacheDir, TEMPORARY_IMAGE_NAME)

            val outputFileOptions = ImageCapture.OutputFileOptions.Builder(file).build()

            executorService?.let {

                if (controller.getCameraXFacade().isRawCaptureActive) {

                    val rawFile = File(context.cacheDir, TEMPORARY_RAW_NAME).apply { delete() }

                    val rawOutputFileOptions = ImageCapture.OutputFileOptions.Builder(rawFile).build()

                    capture.takePicture(rawOutputFileOptions, outputFileOptions, executorService,
                        DualCaptureCallback { makeImage(currentTrait) })

                } else {

                    capture.takePicture(outputFileOptions, executorService,
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onError(error: ImageCaptureException) {}
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                makeImage(currentTrait)
                            }
                        })
                }
            }
        }
    }

    private fun notifyIfRawUnavailable() {

        if (controller.getCameraXFacade().rawCaptureFellBack) {

            Utils.makeToast(context, context.getString(R.string.camera_raw_unavailable))
        }
    }

    /**
     * Long-pressing the preview re-meters exposure and white balance, then locks them again.
     */
    protected fun setupRemeterGesture(previewView: PreviewView?) {

        if (!controlSettings().needsLock) {
            previewView?.setOnLongClickListener(null)
            return
        }

        previewView?.setOnLongClickListener {

            boundCamera?.let { camera ->

                controller.getCameraXFacade().remeterAndLock(camera, null, controlSettings()) {

                    Utils.makeToast(context, context.getString(R.string.camera_exposure_relocked))
                }
            }

            true
        }
    }

    /**
     * @param orientation this is orientation from Surface class, not to be confused with Exif Orientation
     */
    private fun bindPreviewLifecycle() {

        previewHolder?.previewView?.visibility = VISIBLE

        previewHolder?.previewView?.implementationMode = PreviewView.ImplementationMode.COMPATIBLE

        previewHolder?.embiggenButton?.visibility = VISIBLE

        previewHolder?.embiggenButton?.setOnClickListener {

            launchCameraX()

        }

        try {

            val resolution = getSupportedResolutionByPreferences()

            // use an overridable binding so VideoTraitLayout can request video-capable preview
            bindPreviewLifecycleForMode(resolution)

        } catch (e: IllegalArgumentException) {

            e.printStackTrace()
        }
    }

    // Overridable hook: bind preview lifecycle for a given camera mode. Default uses image capture preview.
    protected open fun bindPreviewLifecycleForMode(resolution: Size?) {
        controller.getCameraXFacade().bindPreview(
            previewHolder?.previewView,
            resolution,
            currentTrait.id,
            null,
            showCropRegion = true,
            controls = controlSettings()
        ) { camera, executor, capture ->

            boundCamera = camera

            notifyIfRawUnavailable()

            setupRemeterGesture(previewHolder?.previewView)

            setupCaptureUi( executor, capture)
        }
    }

    private fun getSupportedResolutionByPreferences(): Size? {

        val supportedResolutionPreferredIndex = prefs.getInt(
            GeneralKeys.CAMERA_RESOLUTION,
            0
        )

        var resolution: Size? = null

        if (supportedResolutions.isNotEmpty() && supportedResolutionPreferredIndex < supportedResolutions.size) {

            resolution = supportedResolutions[supportedResolutionPreferredIndex]

        }

        return resolution
    }

    override fun loadLayout() {
        super.loadLayout()
        if (!isCameraActive) {
            setup()
        }
    }

    override fun showSettings() {

        val settingsView = CameraTraitSettingsView(
            context,
            supportedResolutions,
            currentTrait,
            cameraCapabilities,
            supportsRawCapture()
        )
        val scrollView = NestedScrollView(context).apply { addView(settingsView) }
        AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.trait_system_photo_settings_title)
            .setPositiveButton(R.string.dialog_ok) { dialog, _ ->
                settingsView.commitChanges()
                onSettingsChanged()
                dialog.dismiss()
            }
            .setView(scrollView)
            .show()
    }

    override fun onSettingsChanged() {

        val systemEnabled = preferences.getInt(GeneralKeys.CAMERA_SYSTEM, R.id.view_trait_photo_settings_camera_custom_rb)

        if (systemEnabled == R.id.view_trait_photo_settings_camera_system_rb) {

            setupSystemCameraMode()

        } else {

            controller.getCameraXFacade().unbind()

            previewHolder = null

            loadAdapterItems()

            awaitPreviewHolder {

                setupCameraXMode()

            }
        }
    }

    protected open fun awaitPreviewHolder(callback: () -> Unit) {

        previewHolder = getPreviewViewHolder()

        if (previewHolder == null && isPreviewable()) {

            Handler(Looper.getMainLooper()).postDelayed({
                awaitPreviewHolder(callback)
            }, 500)

        } else {

            callback.invoke()
        }
    }

    private fun setupSystemCameraMode() {

        previewHolder?.previewView?.visibility = GONE

        previewHolder?.embiggenButton?.visibility = GONE

        controller.getCameraXFacade().unbind()

        setupCaptureButton {

            capture()
        }

        loadAdapterItems()

    }

    private fun setupCameraXMode() {

        displayPreviewMode(
            if (prefs.getBoolean(
                    GeneralKeys.CAMERA_SYSTEM_PREVIEW,
                    true
                )
            ) Mode.PREVIEW else Mode.NO_PREVIEW
        )

        bindCameraForInformation()
    }

    open fun makeImage(currentTrait: TraitObject): Boolean {

        val file = File(context.cacheDir, TEMPORARY_IMAGE_NAME)

        if (!file.exists() || file.length() == 0L) {
            Log.e(TAG, "makeImage: temp file is missing or empty — camera may not have written to the output URI")
            return false
        }

        // claim the DNG synchronously so a quick next shot can't overwrite it
        val rawFile = File(context.cacheDir, TEMPORARY_RAW_NAME)
        val claimedRaw = if (controlSettings().saveRaw && rawFile.exists() && rawFile.length() > 0L) {
            File(context.cacheDir, "raw_${System.nanoTime()}.dng").takeIf { rawFile.renameTo(it) }
        } else {
            rawFile.delete()
            null
        }

        val uri = file.inputStream().use { stream ->

            val data = stream.readBytes()

            saveJpegToStorage(
                data,
                currentRange,
                currentTrait,
                FileUtil.sanitizeFileName(OffsetDateTime.now().format(internalTimeFormatter)),
                SaveState.SINGLE_SHOT
            )
        }

        claimedRaw?.let { saveCompanionRaw(uri, currentTrait, it) }

        return true
    }

    /**
     * When button is pressed, create a cached image and switch to the camera intent.
     * CollectActivity will receive REQUEST_IMAGE_CAPTURE and call this layout's makeImage() method.
     */
    protected open fun capture() {

        val file = File(context.cacheDir, TEMPORARY_IMAGE_NAME)

        file.delete()
        file.createNewFile()

        // the system camera never writes a DNG, so make sure a stale one isn't picked up
        File(context.cacheDir, TEMPORARY_RAW_NAME).delete()

        val uri =
            GenericFileProvider.getUriForFile(context, GenericFileProvider.AUTHORITY, file)

        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)

        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(context.packageManager) != null) {
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            // Required on Android 7.0+ so the camera app can write to the FileProvider URI.
            // Without this the camera silently fails to save and returns RESULT_OK with an empty file.
            takePictureIntent.clipData = ClipData.newRawUri("", uri)
            takePictureIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
            (context as Activity).startActivityForResult(
                takePictureIntent,
                PICTURE_REQUEST_CODE
            )
        } else {
            launchCameraX()
        }
    }

    protected open fun launchCameraX(mode: String = CameraActivity.MODE_PHOTO) {
        controller.getCameraXFacade().unbind()
        File(context.cacheDir, TEMPORARY_RAW_NAME).delete()
        isCameraActive = false
        val intent = Intent(context, CameraActivity::class.java)
        //set current trait id to set crop region
        intent.putExtra(CameraActivity.EXTRA_TRAIT_ID, currentTrait.id.toInt())
        intent.putExtra(CameraActivity.EXTRA_STUDY_ID, (activity as CollectActivity).studyId)
        intent.putExtra(CameraActivity.EXTRA_OBS_UNIT, currentRange.uniqueId)
        if (mode.isNotEmpty()) intent.putExtra(CameraActivity.EXTRA_MODE, mode)
        // mark that CameraActivity was launched from PhotoTrait so it can behave accordingly
        intent.putExtra(CameraActivity.EXTRA_LAUNCHED_FOR_PHOTO_TRAIT, true)
        activity?.startActivityForResult(intent, PICTURE_REQUEST_CODE)
    }

    override fun onRefresh() {
        super.onRefresh()
        if (!isCameraActive) {
            setup()
        }
    }

    override fun refreshLock() {
        super.refreshLock()
        (context as CollectActivity).traitLockData()
        try {
            onRefresh()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }
}
