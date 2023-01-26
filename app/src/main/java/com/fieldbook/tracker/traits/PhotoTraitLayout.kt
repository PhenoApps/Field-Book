package com.fieldbook.tracker.traits

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.MediaStore
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.AdapterView.OnItemClickListener
import android.widget.Gallery
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.documentfile.provider.DocumentFile
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.activities.ConfigActivity
import com.fieldbook.tracker.adapters.GalleryImageAdapter
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.DialogUtils
import com.fieldbook.tracker.utilities.DocumentTreeUtil.Companion.getFieldMediaDirectory
import com.fieldbook.tracker.utilities.DocumentTreeUtil.Companion.getPlotMedia
import com.fieldbook.tracker.utilities.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.phenoapps.utils.BaseDocumentTreeUtil.Companion.getStem
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*

class PhotoTraitLayout : BaseTraitLayout {

    companion object {
        const val TAG = "PhotoTrait"
        const val type = "photo"
        const val PICTURE_REQUEST_CODE = 252
    }

    private var scope = CoroutineScope(Dispatchers.IO)

    private var drawables: ArrayList<Bitmap>? = null
    private var uris = arrayListOf<Uri>()

    private var photo: Gallery? = null
    private var photoAdapter: GalleryImageAdapter? = null
    private var currentPhotoPath: Uri? = null
    private var activity: Activity? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun setNaTraitsText() {}
    override fun type() = type

    override fun init() {

    }

    override fun init(act: Activity?) {
        super.init(act)
        val capture = findViewById<ImageButton>(R.id.capture)
        capture.setOnClickListener(PhotoTraitOnClickListener())
        photo = findViewById(R.id.photo)
        activity = act
    }

    override fun loadLayout() {

        loadLayoutWork()

        super.loadLayout()
    }

    private fun loadLayoutWork() {

        scope.cancel()
        scope = CoroutineScope(Dispatchers.IO)
        scope.launch {

            // Always set to null as default, then fill in with trait value
            drawables = ArrayList()
            uris = arrayListOf()

            currentTrait.trait?.let { traitName ->

                val photosDir = getFieldMediaDirectory(context, traitName)

                try {

                    val thumbDir = photosDir?.findFile(".thumbnails")
                    //back down to the photos directory if thumbnails don't exist
                    if (thumbDir == null || thumbDir.listFiles().isEmpty()) {
                        generateThumbnails()
                    }
                    if (thumbDir != null) {
                        val plot = currentRange.plot_id
                        val locations = getPlotMedia(thumbDir, plot, ".jpg")

                        if (locations.isNotEmpty()) {
                            locations.forEach { image ->
                                if (image.exists()) {

                                    val name = image.name

                                    if (name != null) {

                                        if (plot in name) {

                                            val bmp = decodeBitmap(image.uri)

                                            if (bmp != null) {

                                                if (image.uri !in uris) {
                                                    uris.add(image.uri)
                                                    drawables?.add(bmp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        loadGallery()

                    }

                    scope.cancel()

                } catch (e: Exception) {

                    e.printStackTrace()

                }
            }
        }
    }

    private fun loadGallery() {

        currentTrait.trait?.let { traitName ->

            val photosDir = getFieldMediaDirectory(context, traitName)
            if (photosDir != null) {
                val photos = getPlotMedia(photosDir, currentRange.plot_id, ".jpg")

                activity?.runOnUiThread {

                    photoAdapter = GalleryImageAdapter(context as Activity, drawables)
                    photo?.adapter = photoAdapter

                    if (photos.isNotEmpty()) {

                        photo?.setSelection((photo?.count ?: 1) - 1)
                        photo?.onItemClickListener =
                            OnItemClickListener { _: AdapterView<*>?, _: View?, pos: Int, _: Long ->
                                displayPlotImage(
                                    photos[pos].uri
                                )
                            }
                    }

                    photoAdapter?.notifyDataSetChanged()
                }
            }
        }
    }

    private fun decodeBitmap(uri: Uri): Bitmap? {
        return try {
            context.contentResolver.openInputStream(uri).use { input ->
                var bmp = BitmapFactory.decodeStream(input)
                val mat = Matrix().apply {
                    postRotate(90f)
                }
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, mat, false)
                input?.close()
                bmp
            }
        } catch (e: IOException) {
            e.printStackTrace()
            null
        }
    }

    private fun generateThumbnails() {
        currentTrait.trait?.let { traitName ->
            val photosDir = getFieldMediaDirectory(context, traitName)
            if (photosDir != null) {
                val files = photosDir.listFiles()
                for (doc in files) {
                    createThumbnail(photosDir, doc.uri)
                }
            }
        }
    }

    private fun createThumbnail(photosDir: DocumentFile, uri: Uri) {

        //create thumbnail
        try {
            var thumbsDir = photosDir.findFile(".thumbnails")
            val name: String = uri.getStem(context)

            if (thumbsDir == null) {
                thumbsDir = photosDir.createDirectory(".thumbnails")
            }

            if (thumbsDir != null) {
                val nomedia = thumbsDir.findFile(".nomedia")
                if (nomedia == null || !nomedia.exists()) {
                    thumbsDir.createFile("*/*", ".nomedia")
                }
                var bmp = MediaStore.Images.Media.getBitmap(context.contentResolver, uri)
                bmp = Bitmap.createScaledBitmap(bmp, 256, 256, true)
                val thumbnail = thumbsDir.createFile("image/*", "$name.jpg")
                if (thumbnail != null) {
                    context.contentResolver.openOutputStream(thumbnail.uri).use { output ->
                        bmp.compress(Bitmap.CompressFormat.JPEG, 80, output)
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun deleteTraitListener() {
        deletePhotoWarning(false, null)
    }

    fun brapiDelete(newTraits: MutableMap<String, String>?) {
        deletePhotoWarning(true, newTraits)
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

        currentTrait.trait?.let { traitName ->

            val photosDir = getFieldMediaDirectory(context, traitName)

            try {

                if (photosDir != null) {

                    currentPhotoPath?.let { path ->

                        DocumentFile.fromSingleUri(context, path)?.let { file ->

                            if (success) {

                                try {

                                    Utils.scanFile(context, file.uri.toString(), "image/*")
                                    createThumbnail(photosDir, file.uri)
                                    updateTraitAllowDuplicates(
                                        traitName,
                                        type,
                                        path.toString(),
                                        null,
                                        newTraits
                                    )
                                    loadLayoutWork()

                                } catch (e: Exception) {

                                    e.printStackTrace()

                                }

                            } else {

                                file.delete()

                            }
                        }
                    }
                }

            } catch (e: Exception) {

                e.printStackTrace()

            }

            (context as CollectActivity).refreshRepeatedValuesToolbarIndicator()
        }
    }

    private fun updateTraitAllowDuplicates(
        traitName: String,
        format: String,
        value: String?,
        newValue: String?,
        newTraits: MutableMap<String, String>?
    ) {
        if (value != newValue) {
            if (currentRange == null || currentRange.plot_id.isEmpty()) {
                return
            }

            value?.let { v ->

                Log.d(TAG, "$format $v")
                newTraits?.remove(traitName)
                newTraits?.set(traitName, v)
                val expId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
                val observation =
                    ConfigActivity.dt.getObservationByValue(expId, currentRange.plot_id, traitName, v)
                ConfigActivity.dt.deleteTraitByValue(expId, currentRange.plot_id, traitName, v)
                ConfigActivity.dt.insertObservation(
                    currentRange.plot_id,
                    traitName,
                    format,
                    newValue ?: v,
                    prefs.getString(
                        GeneralKeys.FIRST_NAME,
                        ""
                    ) + " " + prefs.getString(GeneralKeys.LAST_NAME, ""),
                    (activity as? CollectActivity)?.locationByPreferences,
                    "",
                    expId,
                    observation.dbId,
                    observation.lastSyncedTime,
                    null
                )
            }
        }
    }

    private fun deletePhotoWarning(brapiDelete: Boolean, newTraits: MutableMap<String, String>?) {
        if (!isLocked) {
            val expId = prefs.getInt(GeneralKeys.SELECTED_FIELD_ID, 0).toString()
            val builder = AlertDialog.Builder(context)
            builder.setTitle(context.getString(R.string.dialog_warning))
            builder.setMessage(context.getString(R.string.trait_delete_warning_photo))
            builder.setPositiveButton(context.getString(R.string.dialog_yes)) { dialog, _ ->
                dialog.dismiss()
                if (brapiDelete) {
                    Toast.makeText(
                        context.applicationContext,
                        context.getString(R.string.brapi_delete_message),
                        Toast.LENGTH_SHORT
                    ).show()
                    //updateTrait(parent, currentTrait.getFormat(), getString(R.string.brapi_na));
                }
                if ((photo?.count ?: 0) > 0) {

                    currentTrait.trait?.let { traitName ->

                        val photosDir = getFieldMediaDirectory(context, traitName)

                        try {

                            val thumbsDir = photosDir?.findFile(".thumbnails")
                            val photosList = getPlotMedia(photosDir, currentRange.plot_id, ".jpg").toMutableList()
                            val thumbsList = getPlotMedia(thumbsDir, currentRange.plot_id, ".jpg")
                            val index = photo?.selectedItemPosition ?: 0
                            val selected = photosList[index]
                            val thumbSelected = thumbsList[index]
                            val item = selected.uri
                            if (!brapiDelete) {
                                selected.delete()
                                thumbSelected.delete()
                                photosList.removeAt(index)
                            }
                            val file = DocumentFile.fromSingleUri(context, item)
                            if (file != null && file.exists()) {
                                file.delete()
                            }

                            // Remove individual images
                            if (brapiDelete) {
                                updateTraitAllowDuplicates(
                                    currentTrait.trait,
                                    "photo",
                                    item.toString(),
                                    "NA",
                                    newTraits
                                )
                                loadLayout()
                            } else {
                                ConfigActivity.dt.deleteTraitByValue(
                                    expId,
                                    currentRange.plot_id,
                                    currentTrait.trait,
                                    item.toString()
                                )
                            }

                            // Only do a purge by trait when there are no more images left
                            if (!brapiDelete) {
                                if (photosList.size == 0) removeTrait(currentTrait.trait)
                            }

                        } catch (e: Exception) {

                            e.printStackTrace()

                        }
                    }

                } else {

                    // If an NA exists, delete it
                    ConfigActivity.dt.deleteTraitByValue(
                        expId,
                        currentRange.plot_id,
                        currentTrait.trait,
                        "NA"
                    )
                }
                loadLayoutWork()
                (context as CollectActivity).refreshRepeatedValuesToolbarIndicator()
            }
            builder.setNegativeButton(context.getString(R.string.dialog_no)) { dialog, _ -> dialog.dismiss() }
            val alert = builder.create()
            alert.show()
            DialogUtils.styleDialogs(alert)
        }
    }

    private fun takePicture() {

        val timeStamp = SimpleDateFormat(
            "yyyy-MM-dd-hh-mm-ss", Locale.getDefault()
        )

        currentTrait.trait?.let { traitName ->

            val rep = (context as CollectActivity).rep

            val dir = getFieldMediaDirectory(context, traitName)
            if (dir != null) {
                val generatedName =
                    currentRange.plot_id + "_" + traitName + "_" + rep + "_" + timeStamp.format(
                        Calendar.getInstance().time
                    ) + ".jpg"
                Log.w(TAG, dir.uri.toString() + generatedName)
                val file = dir.createFile("image/jpg", generatedName)
                if (file != null) {

                    currentPhotoPath = file.uri

                    val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    // Ensure that there's a camera activity to handle the intent
                    if (takePictureIntent.resolveActivity(context.packageManager) != null) {
                        takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, file.uri)
                        (context as Activity).startActivityForResult(
                            takePictureIntent,
                            PICTURE_REQUEST_CODE
                        )
                    }
                }
            }
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
}