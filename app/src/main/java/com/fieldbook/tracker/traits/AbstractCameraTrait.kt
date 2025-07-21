package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.util.AttributeSet
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.cardview.widget.CardView
import androidx.documentfile.provider.DocumentFile
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.ImageAdapter
import com.fieldbook.tracker.database.internalTimeFormatter
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.objects.RangeObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.provider.GenericFileProvider
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.utilities.BitmapLoader
import com.fieldbook.tracker.utilities.DocumentTreeUtil
import com.fieldbook.tracker.utilities.ExifUtil
import com.fieldbook.tracker.utilities.FileUtil
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import org.threeten.bp.OffsetDateTime
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import javax.inject.Inject

@AndroidEntryPoint
abstract class AbstractCameraTrait :
    BaseTraitLayout,
    ImageAdapter.ImageItemHandler {

    @Inject
    lateinit var preferences: SharedPreferences

    companion object {
        const val TAG = "Camera"
        const val TEMPORARY_IMAGE_NAME = "temp.jpg"
    }

    enum class SaveState {
        SINGLE_SHOT, NEW, SAVING, COMPLETE
    }

    class RightToLeftReversedLinearLayoutManager(context: Context) :
        LinearLayoutManager(context, HORIZONTAL, true) {
        override fun isLayoutRTL(): Boolean {
            return true // This will start the layout from the right
        }
    }

    protected var activity: Activity? = null
    protected var connectBtn: FloatingActionButton? = null
    protected var styledPlayerView: PlayerView? = null
    protected var recyclerView: RecyclerView? = null
    protected var settingsButton: ImageButton? = null
    protected var shutterButton: ImageButton? = null
    protected var imageView: ImageView? = null
    protected var previewCardView: CardView? = null

    private var loader = CoroutineScope(Dispatchers.IO)
    protected val background = CoroutineScope(Dispatchers.IO)
    protected val ui = CoroutineScope(Dispatchers.Main)

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun layoutId(): Int {
        return R.layout.trait_camera
    }

    fun setImageNa() {

        if (!isLocked) {

            val image = getSelectedImage()

            if (image == null) {

                insertNa()

            } else getImageObservations().firstOrNull { it.internal_id_observation == image.id }?.let { obs ->

                if (obs.value != "NA") {

                    showDeleteImageDialog(image, true)

                }
            }
        }

        loadAdapterItems()
    }

    override fun setNaTraitsText() {}

    override fun loadLayout() {

        //slight delay to make navigation a bit faster
        //Handler(Looper.getMainLooper()).postDelayed({

            loadAdapterItems()

       // }, 500)

        super.loadLayout()
    }

    override fun init(act: Activity) {

        connectBtn = act.findViewById(R.id.camera_fragment_connect_btn)
        imageView = act.findViewById(R.id.trait_camera_iv)
        styledPlayerView = act.findViewById(R.id.trait_camera_spv)
        recyclerView = act.findViewById(R.id.camera_fragment_rv)
        settingsButton = act.findViewById(R.id.camera_fragment_settings_btn)
        shutterButton = act.findViewById(R.id.camera_fragment_capture_btn)
        previewCardView = act.findViewById(R.id.trait_camera_cv)

        recyclerView?.adapter = ImageAdapter(context, this)

        recyclerView?.layoutManager = RightToLeftReversedLinearLayoutManager(context)

        activity = act
    }

    abstract fun showSettings()

    abstract fun onSettingsChanged()

    fun requestPicture() {

        if (!isLocked) {

            if (checkPictureLimit()) {

                shutterButton?.performClick()
            }
        }
    }

    protected fun isCropRequired() = (currentTrait?.cropImage ?: false)

    protected fun isCropExist() = (preferences.getString(GeneralKeys.getCropCoordinatesKey(currentTrait?.id?.toInt() ?: -1), "") ?: "").isNotEmpty()

    protected fun requestCropDefinition(traitId: String, imageUri: Uri) {

        (context as CollectActivity).showCropDialog(traitId, imageUri)

    }

    protected fun saveJpegToStorage(
        format: String,
        data: ByteArray,
        obsUnit: RangeObject,
        saveTime: String,
        saveState: SaveState,
        offset: Int? = null
    ) {

        saveToStorage(format, obsUnit, saveTime, saveState) { uri ->

            if (saveState != SaveState.SINGLE_SHOT) {

                context.externalCacheDir?.let { dir ->

                    RandomAccessFile(File(dir, TEMPORARY_IMAGE_NAME), "rw").use { raf ->

                        if (saveState != SaveState.COMPLETE) {

                            saveBufferedData(raf, data, offset)

                        } else {

                            saveTempFileToStorage(uri, raf)
                        }
                    }
                }

            } else {

                saveSingleShot(uri, data)

            }
        }
    }

    private fun saveTempFileToStorage(uri: Uri, raf: RandomAccessFile) {

        context.contentResolver.openOutputStream(uri, "wa")?.use { output ->
            try {
                val buffer = ByteArray(1024)
                var bytesRead = raf.read(buffer)
                while (bytesRead != -1) {
                    output.write(buffer, 0, bytesRead)
                    bytesRead = raf.read(buffer)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                Log.e(TAG, e.message ?: "Failed to save image.")
            }
        }
    }

    private fun saveBufferedData(raf: RandomAccessFile, data: ByteArray, offset: Int?) {

        raf.seek((offset ?: 0).toLong())

        raf.write(data)
    }

    private fun saveSingleShot(uri: Uri, data: ByteArray) {

        context.contentResolver.openOutputStream(uri, "wa")?.use { output ->

            output.write(data)

        }
    }

    protected fun saveBitmapToStorage(format: String, bmp: Bitmap, obsUnit: RangeObject) {

        saveToStorage(
            format, obsUnit, saveTime = FileUtil.sanitizeFileName(
                OffsetDateTime.now().format(
                    internalTimeFormatter
                )
            ), saveState = SaveState.SINGLE_SHOT
        ) { uri ->

            context.contentResolver.openOutputStream(uri)?.let { output ->

                bmp.compress(Bitmap.CompressFormat.JPEG, 80, output)

            }
        }
    }

    private fun writeExif(file: DocumentFile, studyId: String, timestamp: String) {

        //if sdk > 24, can write exif information to the image
        //goal is to encode observation variable model into the user comments
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {

            ExifUtil.saveVariableUnitModelToExif(
                context,
                (controller.getContext() as CollectActivity).person,
                timestamp,
                database.getStudyById(studyId),
                database.getObservationUnitById(currentRange.uniqueId),
                database.getObservationVariableById(currentTrait.id),
                file.uri,
                controller.getRotationRelativeToDevice()
            )
        }
    }

    private fun saveToStorage(
        format: String,
        obsUnit: RangeObject,
        saveTime: String,
        saveState: SaveState,
        saver: (Uri) -> Unit
    ) {

        val plot = obsUnit.uniqueId
        val studyId = collectActivity.studyId
        val person = (activity as? CollectActivity)?.person
        val location = (activity as? CollectActivity)?.locationByPreferences
        val rep = database.getNextRep(studyId, plot, currentTrait.id)

        background.launch {

            //get current trait's trait name, use it as a plot_media directory
            currentTrait.name.let { traitName ->

                val sanitizedTraitName = FileUtil.sanitizeFileName(traitName)

                val traitDbId = currentTrait.id

                //get the bitmap from the texture view, only use it if its not null

                val name = "${plot}_${sanitizedTraitName}_$saveTime.jpg"

                DocumentTreeUtil.getFieldMediaDirectory(context, sanitizedTraitName)?.let { dir ->

                    if (saveState == SaveState.NEW || saveState == SaveState.SINGLE_SHOT) {

                        dir.createFile("*/*", name)?.let { file ->

                            saver.invoke(file.uri)

                            database.insertObservation(
                                plot, traitDbId, file.uri.toString(),
                                person,
                                location, "", studyId,
                                null,
                                null,
                                rep
                            )

                            if (saveState == SaveState.SINGLE_SHOT) {

                                writeExif(file, studyId, saveTime)

                                notifyItemInserted(file.uri)
                            }
                        }

                        ui.launch {

                            (context as CollectActivity).refreshRepeatedValuesToolbarIndicator()

                        }

                    } else if (saveState in setOf(SaveState.SAVING, SaveState.COMPLETE)) {

                        dir.findFile(name)?.let { file ->

                            if (saveState == SaveState.COMPLETE) {

                                saver.invoke(file.uri)

                                writeExif(file, studyId, saveTime)

                                notifyItemInserted(file.uri)

                            } else {

                                saver.invoke(file.uri)

                            }
                        }
                    }
                }
            }
        }
    }

    private fun notifyItemInserted(uri: Uri) {

        ui.launch {

            if (isCropRequired()) {

                if (isCropExist()) {

                    //get bitmap from uri, create new bitmap from preference roi and update uri to database
                    val cropRect = preferences.getString(GeneralKeys.getCropCoordinatesKey(currentTrait.id.toInt()), "") ?: ""

                    withContext(Dispatchers.IO) {

                        //crop bmp
                        val croppedBmp = BitmapLoader.cropBitmap(context, uri, cropRect)

                        //save cropped bmp to uri
                        context.contentResolver.openOutputStream(uri)?.use { output ->
                            croppedBmp.compress(Bitmap.CompressFormat.JPEG, 80, output)
                        }

                        withContext(Dispatchers.Main) {
                            loadItems()
                        }
                    }

                } else {

                    requestCropDefinition(currentTrait.id, uri)
                }

            } else {

                loadItems()
            }
        }
    }

    private fun loadItems() {

        loadAdapterItems()

        // update trait status as observation was saved
        (context as CollectActivity).updateCurrentTraitStatus(true)
    }

    private fun getSelectedImage(): ImageAdapter.Model? {

        //check if its external or no preview
        val skipPreview = isPreviewable()

        val size = (recyclerView?.adapter as? ImageAdapter)?.currentList?.size ?: 0
        val lm = recyclerView?.layoutManager as? LinearLayoutManager

        val lastVisible = lm?.findLastVisibleItemPosition()
        val lastFullVisible = lm?.findLastCompletelyVisibleItemPosition()

        var position = if (lastFullVisible == -1) lastVisible else lastFullVisible

        //get previous position if the first visible is preview
        if (skipPreview) {
            position = if (position == size - 1) position - 1 else position
        }

        //ensure position is within array bounds
        if (position != null && position in 0 until size) {

            return (recyclerView?.adapter as? ImageAdapter)?.currentList?.get(position)

        }

        return null
    }

    override fun deleteTraitListener() {

        if (!isLocked) {

            getSelectedImage()?.let { model ->

                showDeleteImageDialog(model, false)

            }
        }
    }

    private fun showDeleteImageDialog(model: ImageAdapter.Model, setNa: Boolean) {

        if (!isLocked) {

            val imageView = ImageView(context)

            if (model.uri == "NA") {
                val data = context.resources.assets.open("na_placeholder.jpg").readBytes()
                BitmapFactory.decodeByteArray(data, 0, data.size).also { bmp ->
                    imageView.setImageBitmap(bmp)
                }
            } else {
                val preview = BitmapLoader.getPreview(context, model.uri, model.orientation)
                imageView.setImageBitmap(preview)
            }

            AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(R.string.delete_local_photo)
                .setOnCancelListener { dialog -> dialog.dismiss() }
                .setPositiveButton(android.R.string.ok) { dialog, _ ->

                    dialog.dismiss()

                    deleteItem(model, setNa)

                }
                .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                .setView(imageView)
                .show()
        }
    }

    fun scrollToLast() {

        try {

            recyclerView?.postDelayed({

                val pos = recyclerView?.adapter?.itemCount ?: 1

                recyclerView?.scrollToPosition(pos - 1)

            }, 500L)

        } catch (e: Exception) {

            e.printStackTrace()

        }
    }

    protected fun loadAdapterItems() {

        val studyId = collectActivity.studyId
        val plot = collectActivity.observationUnit
        val traitId = currentTrait.id
        val rep = collectActivity.rep

        val isExternalCamera = Formats.isExternalCameraTrait(currentTrait.format)

        loader.cancel()
        loader = CoroutineScope(Dispatchers.IO)

        loader.launch {

            val thumbnailModels = getImageObservations().map {

                //check if coroutine has canceled
                yield()

                val brapiSynced = database.isBrapiSynced(studyId, plot, traitId, rep)

                ImageAdapter.Model(
                    id = it.internal_id_observation,
                    type = ImageAdapter.Type.IMAGE,
                    orientation = if (isExternalCamera) Configuration.ORIENTATION_LANDSCAPE else Configuration.ORIENTATION_PORTRAIT,
                    uri = it.value,
                    brapiSynced = brapiSynced
                )

            }.toMutableList()

            //skip adding the inline-preview if the camera is external
            if (isPreviewable()) {

                //add preview image adapter item
                thumbnailModels.add(
                    ImageAdapter.Model(
                        id = -1,
                        type = ImageAdapter.Type.PREVIEW,
                    )
                )
            }

            ui.launch {

                (recyclerView?.adapter as? ImageAdapter)?.submitList(thumbnailModels)

                scrollToLast()
            }
        }
    }

    private fun getImageObservations(): Array<ObservationModel> {

        val traitDbId = collectActivity.traitDbId
        val plot = collectActivity.observationUnit
        val studyId = collectActivity.studyId

        return database.getAllObservations(studyId, plot, traitDbId)
    }

    protected fun isPreviewable(): Boolean {

        val isExternalCamera = Formats.isExternalCameraTrait(currentTrait.format)

        //skip adding the inline-preview if the camera is external
        if (!isExternalCamera) {

            //skip adding the preview if the camera is Android system
            if (preferences.getInt(
                    GeneralKeys.CAMERA_SYSTEM,
                    R.id.view_trait_photo_settings_camera_custom_rb
                ) != R.id.view_trait_photo_settings_camera_system_rb
            ) {

                //finally skip the preview if the user has disabled it
                if (preferences.getBoolean(GeneralKeys.CAMERA_SYSTEM_PREVIEW, true)) {

                    //add preview image adapter item
                    return true

                }
            }
        }

        return false
    }

    /**
     * Uses details parameter to check if the number of photos has been reached.
     */
    protected fun checkPictureLimit(): Boolean {

        try {

            val m: Int = try {

                currentTrait.details.toInt()

            } catch (n: Exception) {

                0
            }

            // Do not take photos if limit is reached
            return m == 0 || getImageObservations().size < m

        } catch (e: Exception) {

            e.printStackTrace()

            com.fieldbook.tracker.utilities.Utils.makeToast(context, context.getString(R.string.trait_error_hardware_missing))
        }

        return false
    }

    private fun insertNa() {

        database.insertObservation(
            currentRange.uniqueId,
            currentTrait.id,
            "NA",
            (activity as? CollectActivity)?.person,
            (activity as? CollectActivity)?.locationByPreferences,
            "",
            (activity as? CollectActivity)?.studyId,
            null,
            null,
            (activity as? CollectActivity)?.rep
        )
    }

    private fun updateObservationValueToNa(observation: ObservationModel) {

        database.updateObservation(observation.also {
            it.value = "NA"
        })
    }

    private fun deleteImage(image: DocumentFile) {

        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()

        val plot = currentRange.uniqueId

        val traitDbId = currentTrait.id

        val result = image.delete()

        if (result) {

            database.deleteTraitByValue(
                studyId,
                plot,
                traitDbId,
                image.uri.toString()
            )

        } else {

            collectActivity.runOnUiThread {

                Toast.makeText(
                    context,
                    R.string.photo_failed_to_delete,
                    Toast.LENGTH_SHORT
                ).show()

            }
        }
    }

    private fun deleteItem(model: ImageAdapter.Model, setNa: Boolean) {

        getImageObservations().firstOrNull { it.internal_id_observation == model.id }?.let { observation ->

            try {

                if (observation.value == "NA") {

                    database.deleteObservation(observation.internal_id_observation.toString())

                } else {

                    DocumentFile.fromSingleUri(context, Uri.parse(observation.value))
                        ?.let { image ->

                            if (model.brapiSynced == true || setNa) {

                                image.delete()

                                updateObservationValueToNa(observation)

                            } else deleteImage(image)

                        }
                }

                loadAdapterItems()

            } catch (e: Exception) {

                Log.e(TAG, "Failed to delete images.", e)

            }

            ui.launch {
                if (getImageObservations().isEmpty()){
                    (context as CollectActivity).updateCurrentTraitStatus(false)
                }
            }
            (context as CollectActivity).refreshRepeatedValuesToolbarIndicator()

        }
    }

    override fun onItemClicked(model: ImageAdapter.Model) {

        if (!isLocked) {

            getImageObservations().firstOrNull { it.value == model.uri }?.let { observation ->

                val uri = if (observation.value != "NA") {
                    DocumentFile.fromSingleUri(context, Uri.parse(observation.value))?.uri
                } else {

                    context?.cacheDir?.let { dir ->

                        val file = File(dir, "na_placeholder.jpg")

                        if (!file.exists()) {
                            val data = context.resources.assets.open("na_placeholder.jpg").readBytes()
                            BitmapFactory.decodeByteArray(data, 0, data.size).also { bmp ->
                                file.writeBytes(data)
                            }
                        }

                        GenericFileProvider.getUriForFile(context, GenericFileProvider.AUTHORITY, file)
                    }
                }

                uri?.let { imageUri ->

                    activity?.startActivity(Intent(Intent.ACTION_VIEW, imageUri).also {
                        it.setDataAndType(imageUri, "image/*")
                        it.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    })
                }
            }
        }
    }

    override fun onItemDeleted(model: ImageAdapter.Model) {

        showDeleteImageDialog(model, false)
    }

    override fun refreshLock() {
        super.refreshLock()
        (context as CollectActivity).traitLockData()
    }

    override fun refreshLayout(onNew: Boolean?) {
        super.refreshLayout(onNew)
        loadAdapterItems()
    }
}