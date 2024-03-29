package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import android.util.AttributeSet
import android.util.Log
import android.widget.ImageView
import android.widget.Toast
import androidx.camera.view.PreviewView
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
import kotlinx.coroutines.launch
import org.phenoapps.androidlibrary.Utils
import java.io.FileNotFoundException
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

    protected var activity: Activity? = null
    protected var connectBtn: FloatingActionButton? = null
    protected var captureBtn: FloatingActionButton? = null
    protected var settingsBtn: FloatingActionButton? = null
    protected var previewView: PreviewView? = null
    protected var imageView: ImageView? = null
    protected var recyclerView: RecyclerView? = null

    protected val background = CoroutineScope(Dispatchers.IO)
    protected val ui = CoroutineScope(Dispatchers.Main)

    open val thumbnailWidth = 256
    open val thumbnailHeight = 256

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
        captureBtn = act.findViewById(R.id.camera_fragment_capture_btn)
        imageView = act.findViewById(R.id.trait_camera_iv)
        recyclerView = act.findViewById(R.id.camera_fragment_rv)
        settingsBtn = act.findViewById(R.id.camera_fragment_settings_btn)
        previewView = act.findViewById(R.id.trait_camera_pv)

        recyclerView?.adapter = ImageAdapter(this)

        activity = act

        settingsBtn?.setOnClickListener {
            showSettings()
        }
    }

    abstract fun showSettings()

    abstract fun onSettingsChanged()

    open fun getThumbnailSize() = Point(thumbnailWidth, thumbnailHeight)

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

    private fun writeExif(file: DocumentFile) {

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

                                writeExif(file)

                                notifyItemInserted()
                            }
                        }

                    } else if (saveState in setOf(SaveState.SAVING, SaveState.COMPLETE)) {

                        dir.findFile(name)?.let { file ->

                            if (saveState == SaveState.COMPLETE) {

                                writeExif(file)

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

            (recyclerView?.layoutManager as? LinearLayoutManager)?.findFirstCompletelyVisibleItemPosition()
                ?.let { index ->

                    if (index > -1) {

                        (recyclerView?.adapter as? ImageAdapter)?.currentList?.get(index)
                            ?.let { model ->

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

    private fun loadAdapterItems() {

        val studyId = collectActivity.studyId
        val plot = collectActivity.observationUnit
        val traitId = currentTrait.id
        val rep = collectActivity.rep

        background.launch {

            val thumbnailModels = getImageObservations().mapNotNull {

                val brapiSynced = database.isBrapiSynced(studyId, plot, traitId, rep)

                var model: ImageAdapter.Model? = null

                try {

                    DocumentsContract.getDocumentThumbnail(
                        context.contentResolver,
                        Uri.parse(it.value), getThumbnailSize(), null
                    )?.let { bmp ->

                        model = ImageAdapter.Model(it.value, if (bmp.height > bmp.width) {

                            val matrix = Matrix()

                            matrix.postRotate(90f)

                            Bitmap.createBitmap(
                                bmp,
                                0,
                                0,
                                bmp.width,
                                bmp.height,
                                matrix,
                                true
                            )

                        } else bmp, brapiSynced)

                    }

                } catch (f: FileNotFoundException) {

                    f.printStackTrace()

                    model = null
                }

                model
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

                        if (model.brapiSynced) {

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