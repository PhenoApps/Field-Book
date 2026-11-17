package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import androidx.viewpager.widget.ViewPager

/**
 * ViewPager subclass that supports wrap_content height by measuring the tallest child page.
 */
class WrapContentViewPager @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null
) : ViewPager(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val unbounded = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        var maxHeight = 0
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.measure(widthMeasureSpec, unbounded)
            if (child.measuredHeight > maxHeight) maxHeight = child.measuredHeight
        }
        val resolvedHeight = if (maxHeight > 0) {
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.EXACTLY)
        } else {
            heightMeasureSpec
        }
        super.onMeasure(widthMeasureSpec, resolvedHeight)
    }
}
