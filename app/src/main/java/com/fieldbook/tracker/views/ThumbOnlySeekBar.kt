package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatSeekBar

/**
 * A SeekBar that only responds to touches that start on or near the thumb.
 * Tapping elsewhere on the track is ignored, preventing accidental jumps.
 */
class ThumbOnlySeekBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.seekBarStyle
) : AppCompatSeekBar(context, attrs, defStyleAttr) {

    private var isDraggingThumb = false

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val trackWidth = width - paddingLeft - paddingRight
                val thumbX = if (max > 0) {
                    paddingLeft + (trackWidth * progress / max.toFloat())
                } else {
                    paddingLeft.toFloat()
                }
                val slop = 24 * resources.displayMetrics.density
                isDraggingThumb = kotlin.math.abs(event.x - thumbX) <= slop
                if (!isDraggingThumb) return false
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDraggingThumb = false
            }
        }
        return super.onTouchEvent(event)
    }
}
