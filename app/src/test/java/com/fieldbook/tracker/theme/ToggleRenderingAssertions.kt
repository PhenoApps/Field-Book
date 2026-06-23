package com.fieldbook.tracker.theme

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toPixelMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue

internal object ToggleRenderingAssertions {

    fun assertNotBlack(rgb: Int, label: String) {
        assertNotEquals("$label must not render black", 0x000000, rgb and 0x00FFFFFF)
    }

    fun assertRgbNear(expectedRgb: Int, actualRgb: Int, label: String, tolerance: Int = 48) {
        val er = (expectedRgb shr 16) and 0xFF
        val eg = (expectedRgb shr 8) and 0xFF
        val eb = expectedRgb and 0xFF
        val ar = (actualRgb shr 16) and 0xFF
        val ag = (actualRgb shr 8) and 0xFF
        val ab = actualRgb and 0xFF
        assertTrue(
            "$label rgb expected ~${expectedRgb.toString(16)} got ${actualRgb.toString(16)}",
            kotlin.math.abs(er - ar) <= tolerance
                && kotlin.math.abs(eg - ag) <= tolerance
                && kotlin.math.abs(eb - ab) <= tolerance,
        )
    }

    fun ImageBitmap.dominantOpaqueRgb(): Int {
        val map = toPixelMap()
        var bestArgb = 0
        var bestAlpha = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                val c = map[x, y]
                val a = (c.alpha * 255f + 0.5f).toInt().coerceIn(0, 255)
                val r = (c.red   * 255f + 0.5f).toInt().coerceIn(0, 255)
                val g = (c.green * 255f + 0.5f).toInt().coerceIn(0, 255)
                val b = (c.blue  * 255f + 0.5f).toInt().coerceIn(0, 255)
                val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
                if (a > bestAlpha) {
                    bestAlpha = a
                    bestArgb = argb
                }
            }
        }
        assertTrue("expected opaque icon pixels", bestAlpha > 0)
        return bestArgb and 0x00FFFFFF
    }

    fun assertEvenSlotSpacing(photo: Rect, video: Rect, audio: Rect, tolerancePx: Float = 6f) {
        val photoCenter = (photo.left + photo.right) / 2f
        val videoCenter = (video.left + video.right) / 2f
        val audioCenter = (audio.left + audio.right) / 2f
        assertTrue(photoCenter < videoCenter)
        assertTrue(videoCenter < audioCenter)
        val leftGap = videoCenter - photoCenter
        val rightGap = audioCenter - videoCenter
        assertEquals("slot centers should be evenly spaced", leftGap, rightGap, tolerancePx)
    }
}
