package org.phenoapps.labelprint.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.phenoapps.labelprint.zpl.ZplLabel
import org.phenoapps.labelprint.zpl.ZplRendererImpl

/**
 * Composable that renders a ZPL label preview onto a Compose Canvas.
 */
@Composable
fun ZplPreviewCanvas(label: ZplLabel?, modifier: Modifier = Modifier) {
    val renderer = remember { ZplRendererImpl() }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val canvasWidth = size.width
            val canvasHeight = size.height

            val canvasBgPaint = Paint().apply {
                color = Color.parseColor("#EEEEEE")
                style = Paint.Style.FILL
            }
            nativeCanvas.drawRect(0f, 0f, canvasWidth, canvasHeight, canvasBgPaint)

            val labelWidthDots = label?.widthDots ?: 812
            val labelHeightDots = label?.heightDots ?: 406

            val margin = 16f * density
            val availableWidth = canvasWidth - margin * 2
            val availableHeight = canvasHeight - margin * 2

            if (availableWidth <= 0 || availableHeight <= 0) return@drawIntoCanvas

            val scaleX = availableWidth / labelWidthDots.toFloat()
            val scaleY = availableHeight / labelHeightDots.toFloat()
            val scale = minOf(scaleX, scaleY)

            val scaledLabelWidth = labelWidthDots * scale
            val scaledLabelHeight = labelHeightDots * scale

            val offsetX = (canvasWidth - scaledLabelWidth) / 2f
            val offsetY = (canvasHeight - scaledLabelHeight) / 2f

            val shadowPaint = Paint().apply {
                color = Color.parseColor("#33000000")
                style = Paint.Style.FILL
            }
            nativeCanvas.drawRoundRect(
                RectF(offsetX + 3f, offsetY + 3f, offsetX + scaledLabelWidth + 3f, offsetY + scaledLabelHeight + 3f),
                4f, 4f, shadowPaint
            )

            val labelBgPaint = Paint().apply {
                color = Color.WHITE
                style = Paint.Style.FILL
            }
            nativeCanvas.drawRoundRect(
                RectF(offsetX, offsetY, offsetX + scaledLabelWidth, offsetY + scaledLabelHeight),
                4f, 4f, labelBgPaint
            )

            val borderPaint = Paint().apply {
                color = Color.parseColor("#BBBBBB")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            nativeCanvas.drawRoundRect(
                RectF(offsetX, offsetY, offsetX + scaledLabelWidth, offsetY + scaledLabelHeight),
                4f, 4f, borderPaint
            )

            if (label != null) {
                nativeCanvas.save()
                nativeCanvas.translate(offsetX, offsetY)
                renderer.render(nativeCanvas, label, scale)
                nativeCanvas.restore()
            } else {
                val noPreviewPaint = Paint().apply {
                    color = Color.LTGRAY
                    textSize = 14f * density
                    textAlign = Paint.Align.CENTER
                    isAntiAlias = true
                }
                nativeCanvas.drawText(
                    "No Preview",
                    offsetX + scaledLabelWidth / 2f,
                    offsetY + scaledLabelHeight / 2f + 7f * density,
                    noPreviewPaint
                )
            }
        }
    }
}
