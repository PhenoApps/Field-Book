package com.fieldbook.tracker.traits

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.DocumentsContract
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.CollectActivity
import com.fieldbook.tracker.canon.CameraControlFactory
import com.fieldbook.tracker.canon.Controller
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.dialogs.CanonConnectDialog
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.DocumentTreeUtil
import com.fieldbook.tracker.utilities.ExifUtil
import com.fieldbook.tracker.utilities.FileUtil
import com.google.android.material.floatingactionbutton.FloatingActionButton
import dagger.hilt.android.AndroidEntryPoint
import org.phenoapps.adapters.ImageAdapter
import org.phenoapps.androidlibrary.Utils
import java.io.FileNotFoundException
import javax.inject.Inject

@AndroidEntryPoint
class CanonTrait : BaseTraitLayout, ImageAdapter.ImageItemHandler {

    @Inject
    lateinit var preferences: SharedPreferences

    private val canonController by lazy {

        CameraControlFactory(PreferenceManager.getDefaultSharedPreferences(context)).create()?.let { api ->

            Controller(api)
        }
    }

    companion object {
        const val TAG = "Canon"
        const val type = "canon"
    }

    private var activity: Activity? = null
    private var connectBtn: FloatingActionButton? = null
    private var captureBtn: FloatingActionButton? = null
    private var imageView: ImageView? = null
    private var recyclerView: RecyclerView? = null

    constructor(context: Context?) : super(context)
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    )

    override fun layoutId(): Int {
        return R.layout.trait_canon
    }

    override fun setNaTraitsText() {}
    override fun type(): String {
        return type
    }

    override fun init(act: Activity) {

        connectBtn = act.findViewById(R.id.canon_fragment_connect_btn)
        captureBtn = act.findViewById(R.id.canon_fragment_capture_btn)
        imageView = act.findViewById(R.id.trait_canon_iv)
        recyclerView = act.findViewById(R.id.canon_fragment_rv)

        recyclerView?.adapter = ImageAdapter(this)

        activity = act
    }

    private fun setup() {

        activity?.runOnUiThread {

            captureBtn?.visibility = View.INVISIBLE

            imageView?.visibility = View.INVISIBLE

            connectBtn?.visibility = View.VISIBLE

            connectBtn?.setOnClickListener {

                CanonConnectDialog(activity!!) {

                    //startConnection()

                }.show((activity as CollectActivity).supportFragmentManager, "Canon")
            }
        }

        canonController?.awaitConnection {

            startConnection()

        }
    }

    private fun startConnection() {

        connectBtn?.visibility = View.INVISIBLE

        waitForCanonApi()

    }

    private val bridge = object : Controller.ControllerBridge {

        override fun onConnected() {
            captureBtn?.visibility = View.VISIBLE
        }

        override fun onStartCaptureUi() {
            startCaptureUi()
        }

        override fun onReceiveStreamImage(bmp: Bitmap) {
            imageView?.visibility = View.VISIBLE
            imageView?.setImageBitmap(bmp)
        }

        override fun onFail() {
            setup()
        }

        override fun saveBitmap(bmp: Bitmap) {
            saveBitmapToStorage(bmp)
            scrollToLast()
        }
    }

    /**
     * 1. check for device connection NUMBER_OF_CONNECTION_ATTEMPTS times
     * 2. turn movie mode to off (starts still picture capture)
     * 3. turn on live view (turns on lcd)
     * 4.
     */
    private fun waitForCanonApi() {

        canonController?.establishStream(bridge)

    }

    private fun startCaptureUi() {

        captureBtn?.visibility = View.VISIBLE

        captureBtn?.setOnClickListener {

            canonController?.postCameraShutter(bridge)

        }
    }

    private fun saveBitmapToStorage(bmp: Bitmap) {

        //get current trait's trait name, use it as a plot_media directory
        currentTrait.name?.let { traitName ->

            val sanitizedTraitName = FileUtil.sanitizeFileName(traitName)

            val traitDbId = currentTrait.id

            //get the bitmap from the texture view, only use it if its not null

            DocumentTreeUtil.getFieldMediaDirectory(context, sanitizedTraitName)?.let { dir ->

                val plot = collectActivity.observationUnit
                val studyId = collectActivity.studyId
                val time = Utils.getDateTime()
                val name = "${sanitizedTraitName}_${plot}_$time.png"

                dir.createFile("*/*", name)?.let { file ->

                    context.contentResolver.openOutputStream(file.uri)?.let { output ->

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

    override fun loadLayout() {

        //slight delay to make navigation a bit faster
        Handler(Looper.getMainLooper()).postDelayed({

            loadAdapterItems()

        }, 500)

        setup()

        super.loadLayout()
    }

    private fun loadAdapterItems() {

        activity?.runOnUiThread {

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

                Log.e(UsbCameraTraitLayout.TAG, "Failed to delete images.", e)

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