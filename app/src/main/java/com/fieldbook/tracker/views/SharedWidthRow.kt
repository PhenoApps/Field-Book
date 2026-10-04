package com.fieldbook.tracker.views

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import kotlin.math.max
import kotlin.math.min

/**
 * Lays its visible children out on a single line, sharing the width fairly when they don't all fit:
 * children narrower than an even share keep their natural width, and the leftover space is split
 * evenly between the wider ones, which should ellipsize their text (e.g. chips with ellipsize="end").
 * Child margins are respected.
 */
class SharedWidthRow @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : ViewGroup(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {

        val children = (0 until childCount).map { getChildAt(it) }.filter { it.visibility != View.GONE }

        val widthMode = MeasureSpec.getMode(widthMeasureSpec)
        val available = MeasureSpec.getSize(widthMeasureSpec) - paddingLeft - paddingRight
        val margins = children.sumOf { it.horizontalMargins() }

        //natural widths first
        children.forEach {
            measureChild(it, MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED), heightMeasureSpec)
        }

        if (widthMode != MeasureSpec.UNSPECIFIED) {

            //give the narrowest children their natural width while it fits an even share of what's left
            var remaining = max(0, available - margins)
            var remainingCount = children.size

            children.sortedBy { it.measuredWidth }.forEach { child ->
                val width = min(child.measuredWidth, remaining / max(1, remainingCount))
                child.measure(
                    MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                    getChildMeasureSpec(heightMeasureSpec, paddingTop + paddingBottom, child.layoutParams.height)
                )
                remaining -= width
                remainingCount--
            }
        }

        val contentWidth = children.sumOf { it.measuredWidth } + margins
        val contentHeight = children.maxOfOrNull { it.measuredHeight + it.verticalMargins() } ?: 0

        setMeasuredDimension(
            resolveSize(contentWidth + paddingLeft + paddingRight, widthMeasureSpec),
            resolveSize(contentHeight + paddingTop + paddingBottom, heightMeasureSpec)
        )
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {

        val contentHeight = b - t - paddingTop - paddingBottom
        var x = paddingLeft

        for (i in 0 until childCount) {

            val child = getChildAt(i)
            if (child.visibility == View.GONE) continue

            val params = child.layoutParams as MarginLayoutParams
            x += params.leftMargin

            //vertically centered
            val top = paddingTop + (contentHeight - child.measuredHeight) / 2 + params.topMargin - params.bottomMargin
            child.layout(x, top, x + child.measuredWidth, top + child.measuredHeight)

            x += child.measuredWidth + params.rightMargin
        }
    }

    private fun View.horizontalMargins() = (layoutParams as MarginLayoutParams).let { it.leftMargin + it.rightMargin }

    private fun View.verticalMargins() = (layoutParams as MarginLayoutParams).let { it.topMargin + it.bottomMargin }

    override fun generateLayoutParams(attrs: AttributeSet?): LayoutParams = MarginLayoutParams(context, attrs)

    override fun generateDefaultLayoutParams(): LayoutParams =
        MarginLayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)

    override fun generateLayoutParams(p: LayoutParams?): LayoutParams = MarginLayoutParams(p)

    override fun checkLayoutParams(p: LayoutParams?) = p is MarginLayoutParams
}
