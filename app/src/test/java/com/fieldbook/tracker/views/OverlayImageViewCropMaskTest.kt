package com.fieldbook.tracker.views

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Smoke / policy: define-crop mask uses four-rect theme dim, not broken PorterDuff overlay.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayImageViewCropMaskTest {

    @Test
    fun source_usesFourRectDim_notPorterDuff() {
        val source = File("src/main/java/com/fieldbook/tracker/views/OverlayImageView.kt").readText()
        assertFalse("PorterDuff overlay must not remain", source.contains("PorterDuff"))
        assertFalse(source.contains("PorterDuffXfermode"))
        assertTrue(source.contains("fb_inverse_crop_region_color"))
        assertTrue(source.contains("dimPaint"))
        // Four dim rects outside the crop window
        assertEqualsFourDimDraws(source)
    }

    @Test
    fun drawRectangle_doesNotThrow() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val view = OverlayImageView(context)
        view.measure(
            android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
            android.view.View.MeasureSpec.makeMeasureSpec(200, android.view.View.MeasureSpec.EXACTLY),
        )
        view.layout(0, 0, 200, 200)
        val bmp = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).also {
            it.eraseColor(Color.GREEN)
        }
        view.drawRectangle(bmp, 0f, 0f, 200, 200, 40f, 40f, 160f, 160f)
        val canvasBmp = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        view.draw(android.graphics.Canvas(canvasBmp))
        // Smoke: draw completed without throwing (mask uses theme dim, not PorterDuff).
        assertTrue(canvasBmp.width == 200 && canvasBmp.height == 200)
    }

    private fun assertEqualsFourDimDraws(source: String) {
        val onDraw = source.substringAfter("override fun onDraw").substringBefore("fun drawRectangle")
            .ifBlank { source.substringAfter("override fun onDraw") }
        val draws = Regex("""canvas\.drawRect\(""").findAll(onDraw).count()
        // 4 dim rects + 1 border stroke
        assertTrue("expected ≥5 drawRect calls in onDraw, got $draws", draws >= 5)
    }
}
