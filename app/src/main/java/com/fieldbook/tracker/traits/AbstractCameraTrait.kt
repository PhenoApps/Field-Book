package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.AttributeSet
import android.util.Log
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.ImageAdapter
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.objects.RangeObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.DocumentTreeUtil
import com.fieldbook.tracker.utilities.ExifUtil
import com.fieldbook.tracker.utilities.FileUtil
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import org.phenoapps.androidlibrary.Utils
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
    protected var recyclerView: RecyclerView? = null
    protected var settingsButton: ImageButton? = null
    protected var shutterButton: ImageButton? = null

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
        recyclerView = act.findViewById(R.id.camera_fragment_rv)
        settingsButton = act.findViewById(R.id.camera_fragment_settings_btn)
        shutterButton = act.findViewById(R.id.camera_fragment_capture_btn)

        recyclerView?.adapter = ImageAdapter(this, getThumbnailSize())

        recyclerView?.layoutManager = RightToLeftReversedLinearLayoutManager(context)

        activity = act
    }

    abstract fun showSettings()

    abstract fun onSettingsChanged()

    open fun getThumbnailSize(): Point {

        //get thumbnail size from camera dimension resources
        val thumbnailWidth = resources.getDimensionPixelSize(R.dimen.camera_preview_width)
        val thumbnailHeight = resources.getDimensionPixelSize(R.dimen.camera_preview_height)

        return Point(thumbnailWidth, thumbnailHeight)
    }

    protected fun saveJpegToStorage(
        format: String,
        data: ByteArray,
        obsUnit: RangeObject,
        saveTime: String,
        saveState: SaveState
    ) {

        saveToStorage(format, obsUnit, saveTime, saveState) { uri ->

            context.contentResolver.openOutputStream(uri, "wa")?.use { output ->

                output.write(data)

            }
        }
    }

    protected fun saveBitmapToStorage(format: String, bmp: Bitmap, obsUnit: RangeObject) {

        saveToStorage(format, obsUnit, saveTime = Utils.getDateTime(), saveState = SaveState.NEW) { uri ->

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
                database.getObservationUnitById(currentRange.plot_id),
                database.getObservationVariableById(currentTrait.id),
                file.uri
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

        val plot = obsUnit.plot_id
        val studyId = collectActivity.studyId
        val person = (activity as? CollectActivity)?.person
        val location = (activity as? CollectActivity)?.locationByPreferences

        background.launch {

            //get current trait's trait name, use it as a plot_media directory
            currentTrait.name?.let { traitName ->

                val sanitizedTraitName = FileUtil.sanitizeFileName(traitName)

                val traitDbId = currentTrait.id

                //get the bitmap from the texture view, only use it if its not null

                val name = "${sanitizedTraitName}_${plot}_$saveTime.jpg"

                DocumentTreeUtil.getFieldMediaDirectory(context, sanitizedTraitName)?.let { dir ->

                    if (saveState == SaveState.NEW || saveState == SaveState.SINGLE_SHOT) {

                        dir.createFile("*/*", name)?.let { file ->

                            saver.invoke(file.uri)

                            database.insertObservation(
                                plot, traitDbId, format, file.uri.toString(),
                                person,
                                location, "", studyId,
                                null,
                                null,
                                null
                            )

                            if (saveState == SaveState.SINGLE_SHOT) {

                                writeExif(file, studyId, saveTime)

                                notifyItemInserted()
                            }
                        }

                        ui.launch {

                            (context as CollectActivity).refreshRepeatedValuesToolbarIndicator()

                        }

                    } else if (saveState in setOf(SaveState.SAVING, SaveState.COMPLETE)) {

                        dir.findFile(name)?.let { file ->

                            if (saveState == SaveState.COMPLETE) {

                                writeExif(file, studyId, saveTime)

                                notifyItemInserted()

                            } else {

                                saver.invoke(file.uri)

                            }
                        }
                    }
                }
            }
        }
    }

    private fun notifyItemInserted() {

        ui.launch {

            loadAdapterItems()
        }
    }

    override fun deleteTraitListener() {

        if (!isLocked) {

            val size = (recyclerView?.adapter as? ImageAdapter)?.currentList?.size ?: 0
            val lm = recyclerView?.layoutManager as? LinearLayoutManager

            val firstVisible = lm?.findFirstVisibleItemPosition()
            val firstFullVisible = lm?.findFirstCompletelyVisibleItemPosition()

            var position = if (firstFullVisible == -1) firstVisible else firstFullVisible

            //get previous position if the first visible is preview
            position = if (position == size - 1) position - 1 else position

            //ensure position is within array bounds
            if (position != null && position in 0 until size - 1) {

                (recyclerView?.adapter as? ImageAdapter)?.currentList?.get(position)
                    ?.let { model ->

                        showDeleteImageDialog(model)

                    }
            }
        }
    }

    private fun showDeleteImageDialog(model: ImageAdapter.Model) {

        if (!isLocked) {

            DocumentsContract.getDocumentThumbnail(
                context.contentResolver,
                Uri.parse(model.uri), getThumbnailSize(), null
            )?.let { bmp ->

                val imageView = ImageView(context)
                imageView.setImageBitmap(bmp)

                AlertDialog.Builder(context, R.style.AppAlertDialog)
                    .setTitle(R.string.delete_local_photo)
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

        loader.cancel()
        loader = CoroutineScope(Dispatchers.IO)

        loader.launch {

            val thumbnailModels = getImageObservations().mapNotNull {

                //check if coroutine has canceled
                yield()

                val brapiSynced = database.isBrapiSynced(studyId, plot, traitId, rep)

                ImageAdapter.Model(
                    type = ImageAdapter.Type.IMAGE,
                    uri = it.value,
                    brapiSynced = brapiSynced)

            }.toMutableList()

            //check that the current preferences is set to default and add the preview
            if (preferences.getInt(
                    GeneralKeys.CAMERA_SYSTEM,
                    R.id.view_trait_photo_settings_camera_custom_rb
                ) != R.id.view_trait_photo_settings_camera_system_rb
            ) {

                //add preview image adapter item
                thumbnailModels.add(
                    ImageAdapter.Model(
                        type = ImageAdapter.Type.PREVIEW
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

    private fun updateObservationValueToNa(observation: ObservationModel) {

        database.updateObservation(observation.also {
            it.value = "NA"
        })
    }

    private fun deleteImage(image: DocumentFile) {

        val studyId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()

        val plot = currentRange.plot_id

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

    private fun deleteItem(model: ImageAdapter.Model) {

        getImageObservations().firstOrNull { it.value == model.uri }?.let { observation ->

            try {

                DocumentFile.fromSingleUri(context, Uri.parse(observation.value))
                    ?.let { image ->

                        if (model.brapiSynced == true) {

                            image.delete()

                            updateObservationValueToNa(observation)

                        } else deleteImage(image)

                        loadAdapterItems()

                    }

            } catch (e: Exception) {

                Log.e(TAG, "Failed to delete images.", e)

            }

            (context as CollectActivity).refreshRepeatedValuesToolbarIndicator()

        }
    }

    override fun onItemClicked(model: ImageAdapter.Model) {

        if (!isLocked) {

            getImageObservations().firstOrNull { it.value == model.uri }?.let { observation ->

                DocumentFile.fromSingleUri(context, Uri.parse(observation.value))?.let { image ->

                    activity?.startActivity(Intent(Intent.ACTION_VIEW, image.uri).also {
                        it.setDataAndType(image.uri, "image/*")
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