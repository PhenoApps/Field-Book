package com.fieldbook.tracker.traits

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ImageButton
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.adapters.ImageTraitAdapter
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.provider.GenericFileProvider
import com.fieldbook.tracker.utilities.DialogUtils
import com.fieldbook.tracker.utilities.DocumentTreeUtil.Companion.getFieldMediaDirectory
import com.fieldbook.tracker.utilities.DocumentTreeUtil.Companion.getPlotMedia
import com.fieldbook.tracker.utilities.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class PhotoTraitLayout : BaseTraitLayout, ImageTraitAdapter.ImageItemHandler {

    companion object {
        const val TAG = "PhotoTrait"
        const val type = "photo"
        const val PICTURE_REQUEST_CODE = 252
    }

    private val scope = CoroutineScope(Dispatchers.IO)

    private var uris = arrayListOf<Uri>()

    private var currentPhotoPath: Uri? = null
    private var activity: Activity? = null

    private lateinit var recyclerView: RecyclerView


    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun setNaTraitsText() {}
    override fun type() = type

    override fun layoutId(): Int {
        return R.layout.trait_photo
    }

    override fun init(act: Activity) {

        val capture = act.findViewById<ImageButton>(R.id.capture)
        capture.setOnClickListener(PhotoTraitOnClickListener())

        activity = act

        recyclerView = act.findViewById(R.id.trait_photo_rv)
        recyclerView.adapter = ImageTraitAdapter(context, this)

        recyclerView.requestFocus();
    }

    override fun loadLayout() {

        loadLayoutWork()

    }

    private fun loadLayoutWork() {

        val studyId = (context as CollectActivity).studyId

        uris = arrayListOf()

        currentTrait.trait?.let { traitName ->

            try {

                scope.launch {

                    val plot = currentRange.plot_id
                    val toc = System.currentTimeMillis()
                    val uris = database.getAllObservations(studyId, plot, traitName)
                    val tic = System.currentTimeMillis()
                    Log.d(TAG, "Photo trait query time ${uris.size} photos: ${(tic-toc)*1e-3}")

                    val models = uris.mapIndexed { index, model -> ImageTraitAdapter.Model(model.value, index) }

                    activity?.runOnUiThread {
                        (recyclerView.adapter as ImageTraitAdapter).submitList(models)
                        recyclerView.adapter?.notifyItemRangeChanged(0, models.size)
                    }
                }

            } catch (e: Exception) {

                e.printStackTrace()

            }
        }
    }

    override fun deleteTraitListener() {
        deletePhoto(isBrapi = false)
    }

    fun brapiDelete() {
        deletePhoto(isBrapi = true)
    }

    private fun displayPlotImage(path: Uri) {
        try {
            Log.w(TAG, path.toString())
            val intent = Intent()
            intent.action = Intent.ACTION_VIEW
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            intent.setDataAndType(path, "image/*")
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun makeImage(currentTrait: TraitObject, newTraits: MutableMap<String, String>?, success: Boolean) {

        val timeStamp = SimpleDateFormat(
            "yyyy-MM-dd-hh-mm-ss", Locale.getDefault()
        )

        scope.launch {

            currentTrait.trait?.let { traitName ->

                val studyId = (context as CollectActivity).studyId
                val photosDir = getFieldMediaDirectory(context, traitName)
                val unit = currentRange.plot_id
                val dir = getFieldMediaDirectory(context, traitName)

                if (dir != null) {

                    try {

                        if (photosDir != null) {

                            val cache = File(context.cacheDir, "temp.jpg")

                            val uri = GenericFileProvider.getUriForFile(context, "com.fieldbook.tracker.fileprovider", cache)

                            val rep = database.getNextRep(studyId, unit, traitName)

                            val generatedName =
                                currentRange.plot_id + "_" + traitName + "_" + rep + "_" + timeStamp.format(
                                    Calendar.getInstance().time
                                ) + ".jpg"

                            Log.w(TAG, dir.uri.toString() + generatedName)

                            val file = dir.createFile("image/jpg", generatedName)

                            if (file != null) {

                                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                    context.contentResolver.openOutputStream(file.uri)?.use { outputStream ->
                                        inputStream.copyTo(outputStream)
                                    }
                                }

                                if (success) {

                                    try {

                                        Utils.scanFile(context, file.uri.toString(), "image/*")

                                        updateTraitAllowDuplicates(
                                            plotId = unit,
                                            traitName,
                                            type,
                                            file.uri.toString(),
                                            null,
                                            newTraits,
                                            rep
                                        )

                                    } catch (e: Exception) {

                                        e.printStackTrace()

                                    }

                                } else {

                                    file.delete()

                                }
                            }
                        }

                    } catch (e: Exception) {

                        e.printStackTrace()

                    }
                }

                activity?.runOnUiThread {

                    loadLayoutWork()

                    (context as CollectActivity).refreshRepeatedValuesToolbarIndicator()

                }
            }
        }
    }

    private fun updateTraitAllowDuplicates(
        plotId: String,
        traitName: String,
        format: String,
        value: String?,
        newValue: String?,
        newTraits: MutableMap<String, String>?,
        rep: String,
    ) {
        if (value != newValue) {
            if (currentRange == null || currentRange.plot_id.isEmpty()) {
                return
            }

            value?.let { v ->

                Log.d(TAG, "$format $v")
                newTraits?.remove(traitName)
                newTraits?.set(traitName, v)
                val studyId = (context as CollectActivity).studyId
                val observation =
                    database.getObservationByValue(studyId, plotId, traitName, v)
                database.deleteTraitByValue(studyId, plotId, traitName, v)
                database.insertObservation(
                    plotId,
                    traitName,
                    format,
                    newValue ?: v,
                    prefs.getString(
                        GeneralKeys.FIRST_NAME,
                        ""
                    ) + " " + prefs.getString(GeneralKeys.LAST_NAME, ""),
                    (activity as? CollectActivity)?.locationByPreferences,
                    "",
                    studyId,
                    observation.dbId,
                    observation.lastSyncedTime,
                    rep
                )
            }
        }
    }

    private fun deletePhoto(isBrapi: Boolean, modelToDelete: ImageTraitAdapter.Model? = null) {

        var model = modelToDelete
        if (!isLocked) {

            //if pressing the delete button bottom button, find the first visible photo to delete
            if (model == null) {
                val position = (recyclerView.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition()
                model = (recyclerView.adapter as ImageTraitAdapter).currentList[position]
            }

            model?.let { m ->

                val studyId = (context as CollectActivity).studyId

                val builder = AlertDialog.Builder(context)

                builder.setTitle(context.getString(R.string.dialog_warning))
                builder.setMessage(context.getString(R.string.trait_delete_warning_photo))
                builder.setPositiveButton(context.getString(R.string.dialog_yes)) { dialog, _ ->
                    dialog.dismiss()

                    if ((recyclerView.adapter?.itemCount ?: 0) > 0) {

                        try {

                            val file = DocumentFile.fromSingleUri(context, Uri.parse(m.uri))
                            if (file != null && file.exists()) {
                                file.delete()
                            }

                            // Remove individual images
                            if (isBrapi) {
                                updateTraitAllowDuplicates(
                                    currentRange.plot_id,
                                    currentTrait.trait,
                                    "photo",
                                    m.uri,
                                    "NA",
                                    newTraits,
                                    (context as CollectActivity).rep
                                )
                            } else {
                                database.deleteTraitByValue(
                                    studyId,
                                    currentRange.plot_id,
                                    currentTrait.trait,
                                    m.uri
                                )
                            }

                        } catch (e: Exception) {

                            e.printStackTrace()

                        }

                    } else {

                        // If an NA exists, delete it
                        database.deleteTraitByValue(
                            studyId,
                            currentRange.plot_id,
                            currentTrait.trait,
                            "NA"
                        )
                    }

                    loadLayoutWork()

                    (context as CollectActivity).refreshRepeatedValuesToolbarIndicator()
                }

                builder.setNegativeButton(context.getString(R.string.dialog_no)) { dialog, _ -> dialog.dismiss() }

                activity?.runOnUiThread {
                    val alert = builder.create()
                    alert.show()
                    DialogUtils.styleDialogs(alert)
                }
            }
        }
    }

    /**
     * When button is pressed, create a cached image and switch to the camera intent.
     * CollectActivity will receive REQUEST_IMAGE_CAPTURE and call this layout's makeImage() method.
     */
    private fun takePicture() {

        val file = File(context.cacheDir, "temp.jpg")

        file.createNewFile()

        val uri = GenericFileProvider.getUriForFile(context, "com.fieldbook.tracker.fileprovider", file)

        val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(context.packageManager) != null) {
            takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
            (context as Activity).startActivityForResult(
                takePictureIntent,
                PICTURE_REQUEST_CODE
            )
        }
    }

    override fun refreshLock() {
        super.refreshLock()
        (context as CollectActivity).traitLockData()
        try {
            loadLayout()
        } catch (e: java.lang.Exception) {
            e.printStackTrace()
        }
    }

    private inner class PhotoTraitOnClickListener : OnClickListener {
        override fun onClick(view: View) {
            if (!isLocked) {
                try {
                    val m: Int = try {
                        currentTrait.details.toInt()
                    } catch (n: Exception) {
                        0
                    }
                    val photosDir = getFieldMediaDirectory(context, "photos")
                    val plot = currentRange.plot_id
                    val locations = getPlotMedia(photosDir, plot, ".jpg")
                    if (photosDir != null) {
                        // Do not take photos if limit is reached
                        if (m == 0 || locations.size < m) {
                            takePicture()
                        } else Utils.makeToast(
                            context,
                            context.getString(R.string.traits_create_photo_maximum)
                        )
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    Utils.makeToast(context, context.getString(R.string.trait_error_hardware_missing))
                }
            }
        }
    }

    override fun onItemClicked(model: ImageTraitAdapter.Model) {

        displayPlotImage(Uri.parse(model.uri))

    }

    override fun onItemDeleted(model: ImageTraitAdapter.Model) {

        val studyId = (context as CollectActivity).studyId
        val rep = (context as CollectActivity).rep
        val status = database.isBrapiSynced(studyId, currentRange.plot_id, currentTrait.trait, rep)

        deletePhoto(status, model)

    }
}