package com.fieldbook.tracker.theme

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.ShapeDrawable
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.robolectric.shadows.ShadowLooper

internal object DialogChromeAssertions {

    fun idleAfterShow() {
        ShadowLooper.idleMainLooper()
    }

    fun assertColorEquals(message: String, expected: Int, actual: Int?) {
        assertNotNull(message, actual)
        assertEquals(message, expected.toLong(), actual!!.toLong())
    }

    fun assertNotWhite(color: Int, label: String, whiteRgb: Int) {
        assertNotEquals("$label must not be white", whiteRgb, color and 0x00FFFFFF)
    }

    fun assertRenderedAlertChrome(
        dialog: AlertDialog,
        label: String,
        sodaRow: Int,
        sodaBright: Int,
        whiteRgb: Int,
    ) {
        val panel = dialog.requireParentPanel(label)
        val panelColor = panel.backgroundColor()
        assertNotNull("$label: parentPanel must have a readable background after show", panelColor)
        assertColorEquals("$label: parentPanel background", sodaRow, panelColor)
        assertNotWhite(panelColor!!, "$label: parentPanel", whiteRgb)

        dialog.findViewById<TextView>(androidx.appcompat.R.id.alertTitle)?.let { title ->
            assertColorEquals("$label: title text", sodaBright, title.currentTextColor)
            assertNotWhite(title.currentTextColor, "$label: title text", whiteRgb)
        }

        dialog.findViewById<TextView>(android.R.id.message)?.let { message ->
            assertNotWhite(message.currentTextColor, "$label: message text", whiteRgb)
        }
    }

    fun AlertDialog.requireParentPanel(label: String): View {
        val panel = findViewById<View>(androidx.appcompat.R.id.parentPanel)
        assertNotNull("$label: parentPanel must exist after show", panel)
        return panel!!
    }

    fun View.backgroundColor(): Int? = when (val bg = background) {
        is ColorDrawable -> bg.color
        is GradientDrawable -> bg.color?.defaultColor
        is ShapeDrawable -> bg.paint.color
        else -> null
    }
}
