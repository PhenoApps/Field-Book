package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Point
import android.net.Uri
import android.provider.DocumentsContract
import com.fieldbook.tracker.R
import kotlin.math.max
import kotlin.math.min

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


            return if (orientation == Configuration.ORIENTATION_LANDSCAPE) {

                //get thumbnail size from camera dimension resources
                val thumbnailWidth = context.resources.getDimensionPixelSize(R.dimen.camera_preview_landscape_width)
                val thumbnailHeight = context.resources.getDimensionPixelSize(R.dimen.camera_preview_landscape_height)

                getSampledPreview(context, uri, thumbnailWidth, thumbnailHeight)

            } else {

                //get thumbnail size from camera dimension resources
                val thumbnailWidth = context.resources.getDimensionPixelSize(R.dimen.camera_preview_portrait_width)
                val thumbnailHeight = context.resources.getDimensionPixelSize(R.dimen.camera_preview_portrait_height)

                DocumentsContract.getDocumentThumbnail(
                    context.contentResolver,
                    Uri.parse(uri), Point(thumbnailWidth, thumbnailHeight), null
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

                inSampleSize = calculateInSampleSize(this, reqWidth, reqHeight)

                inJustDecodeBounds = false

                context.contentResolver.openInputStream(Uri.parse(uri))?.use { inputStream ->
                    BitmapFactory.decodeStream(inputStream, null, this)
                } ?: Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
            }
        }

        fun cropBitmap(context: Context?, imageUri: Uri, rectCoordinates: String): Bitmap {

            val (tlx, tly, blx, bly) = rectCoordinates.split(",").map { it.toFloat() }

            //load bmp from uri
            var bmp = BitmapFactory.decodeStream(context?.contentResolver?.openInputStream(imageUri))

            val (h, w) = if (bmp.width > bmp.height) (bmp.width to bmp.height) else (bmp.height to bmp.width)

            if (h != bmp.height) {
                //rotate the bitmap 90
                val matrix = android.graphics.Matrix()
                matrix.postRotate(90f)
                bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            }

            //convert normalized coordinates to relative image coordinates
            val rtlx = min(max((tlx * w).toInt(), 0), w)
            val rtly = min(max((tly * h).toInt(), 0), h)
            val rblx = min(max((blx * w).toInt(), 0), w)
            val rbly = min(max((bly * h).toInt(), 0), h)

            var iw = max(0, min(w, rblx - rtlx))
            var ih = max(0, min(h, rbly - rtly))

            if (iw + rtlx > w) {
                iw = w
            }

            if (ih + rtly > h) {
                ih = h
            }

            //crop bmp
            val croppedBmp = Bitmap.createBitmap(bmp, rtlx, rtly, iw, ih)

            return croppedBmp
        }
    }
}