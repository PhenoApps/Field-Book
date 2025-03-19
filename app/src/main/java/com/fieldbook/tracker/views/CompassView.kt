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
import androidx.core.graphics.withRotation

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
    private var currentAngle = prevAngle
    private var boxWidth = 0

    private val needleHeadPath = Path()
    private val needleTailPath = Path()

    private val semiCircle = Paint(Paint.ANTI_ALIAS_FLAG).apply {
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
        boxWidth = MeasureSpec.getSize(widthMeasureSpec)

        val radius = (boxWidth / 2f) - 10f
        val tailLength = radius * 0.3f

        val height = boxWidth/2 + tailLength.toInt() + 20 // 50dp padding below
        setMeasuredDimension(boxWidth, height)
    }

    fun setYawAngle(angle: Float) {
        currentAngle = angle
        prevAngle = angle
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        needleHeadPath.reset()
        needleTailPath.reset()

        val centerX = boxWidth / 2f
        val centerY = boxWidth / 2f
        val radius = (boxWidth / 2f) - 10f // 10f is for margin

        // compass circle
        val startAngle = 180f
        val sweepAngle = 180f
        canvas.drawArc(
            centerX - radius,
            centerY - radius,
            centerX + radius,
            centerY + radius,
            startAngle,
            sweepAngle,
            false,
            semiCircle
        )

        angleText.textSize = radius / 8f

        for (angle in -90..90 step ANGLE_INTERVAL) {
            val adjustedAngle = angle - 90

            val radian = Math.toRadians(adjustedAngle.toDouble()).toFloat()

            /*
                x = centerX + cos(theta) * r
                y = centerY + sin(theta) * r
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
            val textY = centerY + sin(radian) * (radius - 40) + heightOfText / 2
            canvas.drawText(angle.toString(), textX, textY, angleText)
        }

        // needle
        canvas.withRotation(currentAngle, centerX, centerY) {
            val headLength = radius * 0.8f
            val tailLength = radius * 0.3f
            val arrowWidth = radius * 0.15f

            // needleHead
            needleHeadPath.apply {
                moveTo(centerX, centerY - headLength)
                lineTo(centerX - arrowWidth, centerY)
                lineTo(centerX + arrowWidth, centerY)
                close()
                drawPath(this, needleHead)
            }

            // needleTail
            needleTailPath.apply {
                moveTo(centerX, centerY + tailLength)
                lineTo(centerX - arrowWidth, centerY)
                lineTo(centerX + arrowWidth, centerY)
                close()
                drawPath(this, needleTail)
            }
        }
    }
}