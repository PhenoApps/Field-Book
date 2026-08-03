package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import java.io.File
import java.io.FileOutputStream

/**
 * Shared crop helpers for tree node photos so they match Collect photo traits:
 * same [GeneralKeys.getCropCoordinatesKey] ROI prefs and [BitmapLoader.cropBitmap].
 */
object TreeNodePhotoCrop {

    private const val TAG = "TreeNodePhotoCrop"

    enum class Decision {
        /** Trait does not request cropping — save the captured file as-is. */
        NotRequired,
        /** Crop enabled and ROI prefs exist — apply before sidecar save. */
        ApplyExistingRoi,
        /** Crop enabled but ROI missing — show define-crop dialog first. */
        NeedsDefinition,
    }

    fun decision(photoTrait: TraitObject?, prefs: SharedPreferences): Decision {
        if (photoTrait == null || !photoTrait.cropImage) return Decision.NotRequired
        val traitId = photoTrait.id.toIntOrNull() ?: return Decision.NotRequired
        val roi = prefs.getString(GeneralKeys.getCropCoordinatesKey(traitId), "") ?: ""
        return if (roi.isBlank()) Decision.NeedsDefinition else Decision.ApplyExistingRoi
    }

    fun readRoi(prefs: SharedPreferences, traitId: Int): String =
        prefs.getString(GeneralKeys.getCropCoordinatesKey(traitId), "") ?: ""

    /**
     * Apply [roi] in-place to [mediaPath] (absolute path, file://, or content://).
     * Mirrors Collect [AbstractCameraTrait] / REQUEST_MEDIA_CODE JPEG quality 80.
     *
     * @return true when a crop was written; false on blank ROI or failure (caller may still save).
     */
    fun applyCropToPath(context: Context, mediaPath: String, roi: String): Boolean {
        if (roi.isBlank() || mediaPath.isBlank()) return false
        return try {
            val uri = mediaPathToUri(mediaPath)
            val cropped = BitmapLoader.cropBitmap(context, uri, roi)
            writeJpeg(context, uri, mediaPath, cropped)
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to crop node photo at $mediaPath", e)
            false
        }
    }

    fun mediaPathToUri(mediaPath: String): Uri =
        when {
            mediaPath.startsWith("content://") || mediaPath.startsWith("file://") -> mediaPath.toUri()
            else -> Uri.fromFile(File(mediaPath))
        }

    private fun writeJpeg(context: Context, uri: Uri, mediaPath: String, bitmap: Bitmap) {
        val wrote = runCatching {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.flush()
                true
            } ?: false
        }.getOrDefault(false)
        if (!wrote && !mediaPath.startsWith("content://")) {
            val file = if (mediaPath.startsWith("file://")) {
                File(mediaPath.removePrefix("file://"))
            } else {
                File(mediaPath)
            }
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.flush()
            }
        }
    }
}
