package com.fieldbook.tracker.screenshots

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.shadows.ShadowLooper

fun AppCompatActivity.captureReviewRoboImage(filePath: String, target: View? = null) {
    ShadowLooper.idleMainLooper()
    val view = target ?: (this as? SodaDarkReviewHarness)?.captureTarget
        ?: window.decorView
    if (target == null && view === window.decorView) {
        val content = findViewById<View>(android.R.id.content)
        val widthSpec = View.MeasureSpec.makeMeasureSpec(
            content.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels,
            View.MeasureSpec.EXACTLY,
        )
        content.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val width = content.measuredWidth.coerceAtLeast(1)
        val height = content.measuredHeight.coerceAtLeast(resources.displayMetrics.heightPixels)
        window.setLayout(width, height)
        content.layout(0, 0, width, height)
        ShadowLooper.idleMainLooper()
    }
    view.captureRoboImage(filePath)
}
