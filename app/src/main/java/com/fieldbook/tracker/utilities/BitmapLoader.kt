package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.provider.DocumentsContract
import com.fieldbook.tracker.R

/**
 * Uses functions from android documentation:
 * https://developer.android.com/topic/performance/graphics/load-bitmap
 *
 * This class is used to load bitmaps from a uri, and resize them to a thumbnail size.
 * In the case of internal cameras, the thumbnail is loaded directly from the uri using the
 * content provider. In the case of external cameras, the bitmap is loaded from the uri, resized,
 * and returned.
 */
class BitmapLoader {

    companion object {

        private fun resizeBitmap(bitmap: Bitmap, width: Int, height: Int): Bitmap {
            return Bitmap.createScaledBitmap(bitmap, width, height, false)
        }

        private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
            // Raw height and width of image
            val (height: Int, width: Int) = options.run { outHeight to outWidth }
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {

                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2

                // Calculate the largest inSampleSize value that is a power of 2 and keeps both
                // height and width larger than the requested height and width.
                while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                    inSampleSize *= 2
                }
            }

            return inSampleSize
        }

        fun getPreview(context: Context, uri: String?, orientation: Int = Configuration.ORIENTATION_PORTRAIT): Bitmap? {

            //get thumbnail size from camera dimension resources
            val thumbnailWidth = context.resources.getDimensionPixelSize(R.dimen.camera_preview_width)
            val thumbnailHeight = context.resources.getDimensionPixelSize(R.dimen.camera_preview_height)

            val (reqWidth: Int, reqHeight: Int) = (thumbnailWidth to thumbnailHeight)

            return if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                getSampledPreview(context, uri, reqWidth, reqHeight)
            } else {
                DocumentsContract.getDocumentThumbnail(
                    context.contentResolver,
                    Uri.parse(uri), Point(reqWidth, reqHeight), null
                )
            }
        }

        private fun getSampledPreview(context: Context, uri: String?, reqWidth: Int, reqHeight: Int): Bitmap {

            return BitmapFactory.Options().run {

                //first decode the bitmap stream to read its size
                inJustDecodeBounds = true

                context.contentResolver.openInputStream(Uri.parse(uri))?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, this)
                }

                inSampleSize = calculateInSampleSize(this, reqHeight, reqWidth)

                inJustDecodeBounds = false

                context.contentResolver.openInputStream(Uri.parse(uri))?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, this)
                } ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }
    }
}