package com.fieldbook.tracker.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class BarcodeOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyle: Int = 0
) : View(context, attrs, defStyle) {

    private val rects = mutableListOf<RectF>()
    private val paints = mutableListOf<Paint>()
    private var listener: ((index: Int) -> Unit)? = null

    fun setDetections(detections: List<Array<PointF>>) {
        rects.clear()
        paints.clear()
        for (pts in detections.take(2)) {
            if (pts.isNotEmpty()) {
                val left = pts.minOf { it.x }
                val right = pts.maxOf { it.x }
                val top = pts.minOf { it.y }
                val bottom = pts.maxOf { it.y }
                rects.add(RectF(left, top, right, bottom))
                val p = Paint().apply {
                    style = Paint.Style.STROKE
                    strokeWidth = 6f
                    color = 0xFFFFC107.toInt()
                }
                paints.add(p)
            }
        }
        invalidate()
    }

    fun setOnDetectionClickListener(l: (index: Int) -> Unit) {
        listener = l
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        for (i in rects.indices) {
            canvas.drawRect(rects[i], paints[i])
        }
    }

    // only claim touches that start on a detection, so other touches reach the preview underneath (tap-to-focus, re-meter)
    override fun onTouchEvent(event: MotionEvent): Boolean {
        val index = rects.indexOfFirst { it.contains(event.x, event.y) }
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> index >= 0
            MotionEvent.ACTION_UP -> {
                if (index >= 0) listener?.invoke(index)
                true
            }
            else -> true
        }
    }
}