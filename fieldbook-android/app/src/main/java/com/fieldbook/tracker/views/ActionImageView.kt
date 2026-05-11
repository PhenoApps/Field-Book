package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import androidx.appcompat.widget.AppCompatImageView

/**
 * Performs an action when overriding onTouchEvent,
 * adapted from https://stackoverflow.com/questions/47107105/android-button-has-setontouchlistener-called-on-it-but-does-not-override-perform to avoid lint warnings
 */
class ActionImageView : AppCompatImageView {

    interface OnActionListener {
        fun onActionDown()
        fun onActionUp()
    }

    private var listener: OnActionListener? = null

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    fun setOnActionListener(listener: OnActionListener) {
        this.listener = listener
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        super.onTouchEvent(event)

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                onActionDown()
                return true
            }

            MotionEvent.ACTION_UP -> {
                onActionUp()
                performClick()
                return true
            }
        }
        return false
    }

    // Because we call this from onTouchEvent, this code will be executed for both
    // normal touch events and for when the system calls this using Accessibility
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun onActionUp() {
        listener?.onActionUp()
    }

    private fun onActionDown() {
        listener?.onActionDown()
    }
}