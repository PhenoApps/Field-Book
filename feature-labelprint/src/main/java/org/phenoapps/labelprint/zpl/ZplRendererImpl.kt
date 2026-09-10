package org.phenoapps.labelprint.zpl

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.LruCache
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.oned.Code128Writer
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Implementation of [ZplRenderer] that renders ZPL labels onto an Android Canvas.
 */
class ZplRendererImpl : ZplRenderer {

    private val bitMatrixCache = LruCache<String, BitMatrix>(MAX_CACHE_ENTRIES)

    private val textPaint = Paint().apply {
        isAntiAlias = true
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val barcodePaint = Paint().apply {
        isAntiAlias = false
        color = Color.BLACK
        style = Paint.Style.FILL
    }

    private val backgroundPaint = Paint().apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val placeholderPaint = Paint().apply {
        isAntiAlias = true
        color = Color.RED
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }

    companion object {
        private const val MAX_CACHE_ENTRIES = 32
    }

    fun clearCache() {
        bitMatrixCache.evictAll()
    }

    override fun render(
        canvas: Canvas,
        label: ZplLabel,
        scaleFactor: Float
    ) {
        val labelWidth = label.widthDots * scaleFactor
        val labelHeight = label.heightDots * scaleFactor
        canvas.save()
        canvas.clipRect(0f, 0f, labelWidth, labelHeight)
        canvas.drawRect(0f, 0f, labelWidth, labelHeight, backgroundPaint)

        for (element in label.elements) {
            try {
                when (element) {
                    is ZplElement.Text -> renderText(canvas, element, scaleFactor)
                    is ZplElement.TextBlock -> renderTextBlock(canvas, element, scaleFactor)
                    is ZplElement.QrCode -> renderQrCode(canvas, element, scaleFactor)
                    is ZplElement.Barcode128 -> renderBarcode128(canvas, element, scaleFactor)
                }
            } catch (_: Exception) {
                drawPlaceholder(
                    canvas,
                    element.x * scaleFactor,
                    element.y * scaleFactor,
                    50f * scaleFactor,
                    50f * scaleFactor
                )
            }
        }

        canvas.restore()
    }

    private fun renderText(canvas: Canvas, element: ZplElement.Text, scaleFactor: Float) {
        val cellHeight = element.fontHeight * scaleFactor
        textPaint.textSize = cellHeight
        val metrics = textPaint.fontMetrics
        val naturalHeight = -metrics.ascent + metrics.descent
        if (naturalHeight > 0) {
            textPaint.textSize = cellHeight * (cellHeight / naturalHeight)
        }
        val x = element.x * scaleFactor
        val y = element.y * scaleFactor + (-textPaint.fontMetrics.ascent)
        canvas.drawText(element.data, x, y, textPaint)
    }

    private fun renderTextBlock(canvas: Canvas, element: ZplElement.TextBlock, scaleFactor: Float) {
        val cellHeight = element.fontHeight * scaleFactor
        textPaint.textSize = cellHeight
        val metrics = textPaint.fontMetrics
        val naturalHeight = -metrics.ascent + metrics.descent
        if (naturalHeight > 0) {
            textPaint.textSize = cellHeight * (cellHeight / naturalHeight)
        }

        val lines = wrapText(
            text = element.data,
            blockWidthDots = element.blockWidth,
            maxLines = element.maxLines,
            paint = textPaint,
            scaleFactor = scaleFactor
        )

        val baseX = element.x * scaleFactor
        val baseY = element.y * scaleFactor
        val blockWidthPx = element.blockWidth * scaleFactor
        val lineSpacingPx = element.lineSpacing * scaleFactor
        val ascent = -textPaint.fontMetrics.ascent

        for ((index, line) in lines.withIndex()) {
            val lineWidth = textPaint.measureText(line)

            val x = when (element.justify) {
                Justify.LEFT -> baseX
                Justify.CENTER -> baseX + (blockWidthPx - lineWidth) / 2f
                Justify.RIGHT -> baseX + blockWidthPx - lineWidth
            }

            val y = baseY + ascent + index * (cellHeight + lineSpacingPx)
            canvas.drawText(line, x, y, textPaint)
        }
    }

    private fun renderQrCode(canvas: Canvas, element: ZplElement.QrCode, scaleFactor: Float) {
        val data = element.data
        if (data.isEmpty()) {
            drawPlaceholder(
                canvas,
                element.x * scaleFactor,
                element.y * scaleFactor,
                50f * scaleFactor,
                50f * scaleFactor
            )
            return
        }

        try {
            val qrContent = stripZplQrPrefix(data)

            val dataLen = qrContent.length
            val version = when {
                dataLen <= 17 -> 1
                dataLen <= 32 -> 2
                dataLen <= 53 -> 3
                dataLen <= 78 -> 4
                dataLen <= 106 -> 5
                dataLen <= 134 -> 6
                dataLen <= 154 -> 7
                dataLen <= 192 -> 8
                dataLen <= 230 -> 9
                else -> 10
            }
            val modules = 4 * version + 17

            val cacheKey = "qr:${qrContent}:${element.magnification}:${modules}"

            val bitMatrix: BitMatrix = bitMatrixCache.get(cacheKey) ?: run {
                val writer = QRCodeWriter()
                val hints = mapOf(EncodeHintType.MARGIN to 0)
                val matrix =
                    writer.encode(qrContent, BarcodeFormat.QR_CODE, modules, modules, hints)
                bitMatrixCache.put(cacheKey, matrix)
                matrix
            }

            val matrixWidth = bitMatrix.width
            val matrixHeight = bitMatrix.height

            val totalQrSizeOnScreen = modules.toFloat() * element.magnification * scaleFactor
            val pixelSize = totalQrSizeOnScreen / matrixWidth

            val startX = element.x * scaleFactor
            val startY = element.y * scaleFactor

            for (row in 0 until matrixHeight) {
                for (col in 0 until matrixWidth) {
                    if (bitMatrix.get(col, row)) {
                        val left = startX + col * pixelSize
                        val top = startY + row * pixelSize
                        canvas.drawRect(left, top, left + pixelSize, top + pixelSize, barcodePaint)
                    }
                }
            }
        } catch (_: Exception) {
            val size = element.magnification * 21f * scaleFactor
            drawPlaceholder(canvas, element.x * scaleFactor, element.y * scaleFactor, size, size)
        }
    }

    private fun renderBarcode128(
        canvas: Canvas,
        element: ZplElement.Barcode128,
        scaleFactor: Float
    ) {
        val data = element.data
        if (data.isEmpty()) {
            drawPlaceholder(
                canvas,
                element.x * scaleFactor,
                element.y * scaleFactor,
                100f * scaleFactor,
                element.height * scaleFactor
            )
            return
        }

        try {
            val cacheKey = "bc128:${data}"

            val bitMatrix: BitMatrix = bitMatrixCache.get(cacheKey) ?: run {
                val writer = Code128Writer()
                val matrix = writer.encode(data, BarcodeFormat.CODE_128, 0, 0)
                bitMatrixCache.put(cacheKey, matrix)
                matrix
            }

            val matrixWidth = bitMatrix.width
            val moduleWidthPx = element.moduleWidth * scaleFactor
            val barcodeHeight = element.height * scaleFactor
            val startX = element.x * scaleFactor
            val startY = element.y * scaleFactor

            for (col in 0 until matrixWidth) {
                if (bitMatrix.get(col, 0)) {
                    val left = startX + col * moduleWidthPx
                    canvas.drawRect(
                        left,
                        startY,
                        left + moduleWidthPx,
                        startY + barcodeHeight,
                        barcodePaint
                    )
                }
            }

            if (element.printInterpretation) {
                textPaint.textSize = 20f * scaleFactor
                val textY = if (element.interpretationAbove) {
                    startY - 4f * scaleFactor
                } else {
                    startY + barcodeHeight + 20f * scaleFactor
                }
                canvas.drawText(data, startX, textY, textPaint)
            }
        } catch (_: Exception) {
            val width = 100f * scaleFactor
            val height = element.height * scaleFactor
            drawPlaceholder(canvas, element.x * scaleFactor, element.y * scaleFactor, width, height)
        }
    }

    private fun drawPlaceholder(canvas: Canvas, x: Float, y: Float, width: Float, height: Float) {
        val rect = RectF(x, y, x + width, y + height)
        canvas.drawRect(rect, placeholderPaint)
        canvas.drawLine(rect.left, rect.top, rect.right, rect.bottom, placeholderPaint)
        canvas.drawLine(rect.right, rect.top, rect.left, rect.bottom, placeholderPaint)
    }

    override fun calculateScaleFactor(
        labelWidthDots: Int,
        labelHeightDots: Int,
        canvasWidth: Float,
        canvasHeight: Float
    ): Float {
        val scaleX = canvasWidth / labelWidthDots.toFloat()
        val scaleY = canvasHeight / labelHeightDots.toFloat()
        return minOf(scaleX, scaleY)
    }

    fun wrapText(
        text: String,
        blockWidthDots: Int,
        maxLines: Int,
        paint: Paint,
        scaleFactor: Float
    ): List<String> {
        val maxWidthPx = blockWidthDots * scaleFactor
        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word
            else "${currentLine} $word"
            if (paint.measureText(testLine) <= maxWidthPx) {
                currentLine = StringBuilder(testLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    if (lines.size >= maxLines) return lines
                }
                currentLine = StringBuilder(word)
            }
        }
        if (currentLine.isNotEmpty() && lines.size < maxLines) {
            lines.add(currentLine.toString())
        }

        return lines
    }

    private fun stripZplQrPrefix(data: String): String {
        val commaIndex = data.indexOf(',')
        if (commaIndex in 1..2) {
            return data.substring(commaIndex + 1)
        }
        return data
    }
}
