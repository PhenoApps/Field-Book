package com.fieldbook.tracker.views

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.util.AttributeSet
import android.view.View
import kotlin.math.cos
import kotlin.math.sin

class CompassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val CIRCLE_THICKNESS = 4f
        private const val ANGLE_MARKER_THICKNESS = 2f
        private const val ANGLE_INTERVAL = 45
        private var prevAngle = 0f
    }

    // use prevAngle to avoid resetting to 0f everytime
    private var currentYawAngle = prevAngle
    private var size = 0

    private val needleHeadPath = Path()
    private val needleTailPath = Path()

    private val circle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = CIRCLE_THICKNESS
        color = Color.BLACK
    }

    private val needleHead = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.RED
    }

    private val needleTail = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.BLACK
    }

    private val angleText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textAlign = Paint.Align.CENTER
    }

    private val angleMarker = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = ANGLE_MARKER_THICKNESS
        color = Color.BLACK
    }

    // calculates the size of the compass
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        size = minOf(
            MeasureSpec.getSize(widthMeasureSpec),
            MeasureSpec.getSize(heightMeasureSpec)
        )
        setMeasuredDimension(size, size)
    }

    fun setYawAngle(angle: Float) {
        currentYawAngle = angle
        prevAngle = angle
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        needleHeadPath.reset()
        needleTailPath.reset()

        val centerX = size / 2f
        val centerY = size / 2f
        val radius = (size / 2f) - 10f // 10f is for margin

        // compass circle
        canvas.drawCircle(centerX, centerY, radius, circle)

        angleText.textSize = radius / 8f

        for (angle in 0 until 360 step ANGLE_INTERVAL) {
            val radian = Math.toRadians((angle + 180).toDouble()).toFloat()

            /*
                x = centerX + cos(theta) * r
                y = centerY = sin(theta) * r
             */

            // angle markers
            val startX = centerX + cos(radian) * (radius - 20)
            val startY = centerY + sin(radian) * (radius - 20)
            val endX = centerX + cos(radian) * radius
            val endY = centerY + sin(radian) * radius

            canvas.drawLine(startX, startY, endX, endY, angleMarker)

            // angle texts
            val textX = centerX + cos(radian) * (radius - 40)
            val heightOfText = (angleText.descent() - angleText.ascent())
            val textY = centerY + sin(radian) * (radius - 40) +  heightOfText/ 2
            canvas.drawText(angle.toString(), textX, textY, angleText)
        }

        canvas.save()
        canvas.rotate(currentYawAngle-90, centerX, centerY)

        // needle
        val headLength = radius * 0.8f
        val tailLength = radius * 0.3f
        val arrowWidth = radius * 0.15f

        // needleHead
        needleHeadPath.apply {
            moveTo(centerX, centerY - headLength)
            lineTo(centerX - arrowWidth, centerY)
            lineTo(centerX + arrowWidth, centerY)
            close()
            canvas.drawPath(this, needleHead)
        }

        // needleTail
        needleTailPath.apply {
            moveTo(centerX, centerY + tailLength)
            lineTo(centerX - arrowWidth, centerY)
            lineTo(centerX + arrowWidth, centerY)
            close()
            canvas.drawPath(this, needleTail)
        }

        canvas.restore()
    }
}