package com.fieldbook.tracker.fragments

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.BitmapLoader
import com.fieldbook.tracker.utilities.InsetHandler
import com.fieldbook.tracker.views.CropImageView
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.core.content.edit
import androidx.core.net.toUri
import com.fieldbook.tracker.activities.CameraActivity

/**
 * Controller code for handling user input for cropping an image.
 */
@AndroidEntryPoint
class CropImageFragment: Fragment(R.layout.crop_image_fragment), CoroutineScope by MainScope() {

    companion object {
        const val TAG = "CropImageFragment"
        const val EXTRA_TRAIT_ID = "traitId"
        const val EXTRA_IMAGE_URI = "imageUri"
    }

    @Inject
    lateinit var prefs: SharedPreferences

    private var cropImageView: CropImageView? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val traitId = requireArguments().getInt(EXTRA_TRAIT_ID)
        val imageUri = requireArguments().getString(EXTRA_IMAGE_URI) ?: ""
        val skipSave = activity?.intent?.getBooleanExtra(CameraActivity.EXTRA_SKIP_SAVE, false) ?: false

        cropImageView = view.findViewById(R.id.crop_image_view)
        setupCropImageView(traitId, imageUri, skipSave)

        cropImageView?.let { InsetHandler.setupCropImageInsets(it) }
    }

    private fun setupCropImageView(traitId: Int, imageUri: String, skipSave: Boolean) {

        cropImageView?.cropImageHandler = object: CropImageView.CropImageHandler {

            override fun onCropImageSaved(rectCoordinates: String) {

                //save the coordinate text to preferences, make the key relative to the input trait id
                prefs.edit { putString(GeneralKeys.getCropCoordinatesKey(traitId), rectCoordinates) }

                launch(Dispatchers.IO) {

                    val uri = imageUri.toUri()

                    val croppedBmp = BitmapLoader.cropBitmap(context, uri, rectCoordinates)

                    //save cropped bmp to uri
                    context?.contentResolver?.openOutputStream(uri)?.use { output ->
                        croppedBmp.compress(Bitmap.CompressFormat.JPEG, 80, output)
                    }

                    withContext(Dispatchers.Main) {

                        //finish from the crop activity
                        try {
                            activity?.setResult(Activity.RESULT_OK,
                                Intent().also {
                                    it.putExtra(CameraActivity.EXTRA_SKIP_SAVE, skipSave)
                                })
                        } catch (_: Exception) {}
                        activity?.finish()
                    }
                }
            }

            override fun getCropCoordinates(): String {

                return prefs.getString(GeneralKeys.getCropCoordinatesKey(traitId), "") ?: ""
            }

            override fun getImageUri() = imageUri
        }

        cropImageView?.post {
            cropImageView?.initialize()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        cancel()
    }
}