package com.fieldbook.tracker.screenshots

import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.compose.ui.platform.ComposeView
import com.fieldbook.tracker.R
import com.github.takahirom.roborazzi.captureRoboImage
import org.robolectric.shadows.ShadowLooper

object IntegratedScreenshotCapture {
    const val EXTRA_FULL_LENGTH = "full_length"
}

fun AppCompatActivity.applyConstructorFullLengthChrome() {
    findViewById<View>(R.id.constructor_scrim).visibility = View.GONE
    val host = findViewById<ComposeView>(R.id.constructor_compose_host)
    (host.layoutParams as ConstraintLayout.LayoutParams).apply {
        height = ConstraintLayout.LayoutParams.WRAP_CONTENT
        matchConstraintPercentHeight = 0f
        bottomToBottom = ConstraintLayout.LayoutParams.UNSET
        topToBottom = R.id.create_trait_form
    }
    host.requestLayout()
}

fun AppCompatActivity.applyCollectFullLengthChrome() {
    val host = findViewById<ComposeView>(R.id.tree_compose_host)
    (host.layoutParams as ConstraintLayout.LayoutParams).apply {
        height = ConstraintLayout.LayoutParams.WRAP_CONTENT
        bottomToTop = R.id.toolbarBottom
        topToBottom = R.id.act_collect_range_box
    }
    val bottom = findViewById<View>(R.id.toolbarBottom)
    (bottom.layoutParams as ConstraintLayout.LayoutParams).apply {
        topToBottom = R.id.tree_compose_host
        bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
    }
    host.requestLayout()
}

fun AppCompatActivity.captureFullLengthRoboImage(filePath: String) {
    val content = findViewById<View>(android.R.id.content)
    val widthSpec = View.MeasureSpec.makeMeasureSpec(
        content.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels,
        View.MeasureSpec.EXACTLY,
    )
    content.measure(widthSpec, View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    val width = content.measuredWidth
    val height = content.measuredHeight.coerceAtLeast(resources.displayMetrics.heightPixels)
    window.setLayout(width, height)
    content.layout(0, 0, width, height)
    ShadowLooper.idleMainLooper()
    window.decorView.captureRoboImage(filePath)
}
