package com.fieldbook.tracker.utilities

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.preference.PreferenceManager
import androidx.test.core.app.ApplicationProvider
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.FileOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TreeNodePhotoCropTest {

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val prefs
        get() = PreferenceManager.getDefaultSharedPreferences(context)

    @Test
    fun decision_notRequired_whenCropDisabled() {
        val trait = TraitObject().apply {
            id = "42"
            cropImage = false
        }
        prefs.edit().putString(GeneralKeys.getCropCoordinatesKey(42), "0.1,0.1,0.9,0.9").apply()
        assertEquals(
            TreeNodePhotoCrop.Decision.NotRequired,
            TreeNodePhotoCrop.decision(trait, prefs),
        )
        assertEquals(
            TreeNodePhotoCrop.Decision.NotRequired,
            TreeNodePhotoCrop.decision(null, prefs),
        )
    }

    @Test
    fun decision_needsDefinition_whenCropEnabledAndRoiMissing() {
        val trait = TraitObject().apply {
            id = "7"
            cropImage = true
        }
        prefs.edit().remove(GeneralKeys.getCropCoordinatesKey(7)).apply()
        assertEquals(
            TreeNodePhotoCrop.Decision.NeedsDefinition,
            TreeNodePhotoCrop.decision(trait, prefs),
        )
    }

    @Test
    fun decision_applyExistingRoi_whenCropPrefsPresent() {
        val trait = TraitObject().apply {
            id = "9"
            cropImage = true
        }
        prefs.edit().putString(GeneralKeys.getCropCoordinatesKey(9), "0,0,0.5,0.5").apply()
        assertEquals(
            TreeNodePhotoCrop.Decision.ApplyExistingRoi,
            TreeNodePhotoCrop.decision(trait, prefs),
        )
    }

    @Test
    fun applyCropToPath_writesCroppedJpeg_usingSamePrefsKey() {
        val traitId = 11
        val source = File.createTempFile("node_crop_src", ".jpg")
        try {
            // Solid 200x200 JPEG so BitmapLoader can decode/crop.
            val bmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888).also {
                it.eraseColor(Color.RED)
            }
            FileOutputStream(source).use { out ->
                bmp.compress(Bitmap.CompressFormat.JPEG, 100, out)
            }
            val roi = "0.25,0.25,0.75,0.75"
            prefs.edit().putString(GeneralKeys.getCropCoordinatesKey(traitId), roi).apply()

            assertTrue(TreeNodePhotoCrop.applyCropToPath(context, source.absolutePath, roi))
            assertTrue(source.exists())
            val cropped = android.graphics.BitmapFactory.decodeFile(source.absolutePath)
            assertTrue(cropped != null)
            // ROI is half width/height → ~100x100 (BitmapLoader may rotate landscape frames).
            assertTrue(
                "expected cropped dims near 100, got ${cropped!!.width}x${cropped.height}",
                cropped.width in 90..110 && cropped.height in 90..110,
            )
            assertEquals(roi, TreeNodePhotoCrop.readRoi(prefs, traitId))
        } finally {
            source.delete()
        }
    }

    @Test
    fun applyCropToPath_blankRoi_returnsFalse() {
        val source = File.createTempFile("node_crop_blank", ".jpg")
        try {
            source.writeBytes(byteArrayOf(1, 2, 3))
            assertFalse(TreeNodePhotoCrop.applyCropToPath(context, source.absolutePath, ""))
        } finally {
            source.delete()
        }
    }
}
