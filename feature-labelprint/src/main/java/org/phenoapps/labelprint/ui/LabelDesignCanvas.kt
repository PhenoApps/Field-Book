package org.phenoapps.labelprint.ui

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import org.phenoapps.labelprint.model.LabelDesignElement
import org.phenoapps.labelprint.model.LabelDesignElementType
import org.phenoapps.labelprint.zpl.ZplLabel
import org.phenoapps.labelprint.zpl.ZplRendererImpl
import kotlin.math.absoluteValue

private const val SNAP_THRESHOLD_DOTS = 8

private data class Guideline(val position: Float, val isHorizontal: Boolean)

@Composable
fun LabelDesignCanvas(
    elements: List<LabelDesignElement>,
    labelWidthDots: Int,
    labelHeightDots: Int,
    selectedElementId: String?,
    onElementSelected: (String?) -> Unit,
    onElementMoved: (id: String, newX: Int, newY: Int) -> Unit,
    parsedLabel: ZplLabel? = null,
    modifier: Modifier = Modifier
) {
    val currentElements by rememberUpdatedState(elements)
    val currentLabelWidth by rememberUpdatedState(labelWidthDots)
    val currentLabelHeight by rememberUpdatedState(labelHeightDots)
    val currentOnElementSelected by rememberUpdatedState(onElementSelected)
    val currentOnElementMoved by rememberUpdatedState(onElementMoved)

    var scale by remember { mutableFloatStateOf(1f) }
    var labelOffsetX by remember { mutableFloatStateOf(0f) }
    var labelOffsetY by remember { mutableFloatStateOf(0f) }

    var activeGuidelines by remember { mutableStateOf(emptyList<Guideline>()) }
    val currentGuidelines = activeGuidelines

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val down = awaitPointerEvent().changes.firstOrNull() ?: continue
                        if (!down.pressed) continue
                        down.consume()

                        val dotX = ((down.position.x - labelOffsetX) / scale).toInt()
                        val dotY = ((down.position.y - labelOffsetY) / scale).toInt()
                        val hitElement = hitTest(currentElements, dotX, dotY)

                        if (hitElement == null) {
                            currentOnElementSelected(null)
                            activeGuidelines = emptyList()
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                change.consume()
                                if (!change.pressed) break
                            }
                            continue
                        }

                        currentOnElementSelected(hitElement.id)

                        val freshElement = currentElements.find { it.id == hitElement.id }
                        val startX = freshElement?.x ?: hitElement.x
                        val startY = freshElement?.y ?: hitElement.y

                        var accumulatedDx = 0f
                        var accumulatedDy = 0f
                        var isDragging = false
                        val touchSlop = viewConfiguration.touchSlop

                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull() ?: break
                            if (!change.pressed) {
                                change.consume()
                                activeGuidelines = emptyList()
                                break
                            }

                            val delta = change.positionChange()
                            if (delta != Offset.Zero) {
                                accumulatedDx += delta.x
                                accumulatedDy += delta.y

                                if (!isDragging) {
                                    if (accumulatedDx.absoluteValue > touchSlop || accumulatedDy.absoluteValue > touchSlop) {
                                        isDragging = true
                                    }
                                }

                                if (isDragging) {
                                    change.consume()
                                    var newDotX = (startX + (accumulatedDx / scale).toInt())
                                        .coerceIn(0, (currentLabelWidth - 20).coerceAtLeast(0))
                                    var newDotY = (startY + (accumulatedDy / scale).toInt())
                                        .coerceIn(0, (currentLabelHeight - 20).coerceAtLeast(0))

                                    val dragBounds = getElementVisualBounds(
                                        hitElement.copy(x = newDotX, y = newDotY)
                                    )
                                    val dragH = dragBounds.height()

                                    val guideW: Float
                                    if (hitElement.type == LabelDesignElementType.TEXT) {
                                        hitTestPaint.textSize = hitElement.fontSize.toFloat()
                                        val displayText = "${hitElement.prefix}${hitElement.placeholder}${hitElement.suffix}"
                                        guideW = hitTestPaint.measureText(displayText)
                                    } else {
                                        guideW = dragBounds.width()
                                    }
                                    val dragCx = newDotX + guideW / 2f
                                    val dragCy = newDotY + dragH / 2f

                                    val xCandidates = mutableListOf<Float>()
                                    val yCandidates = mutableListOf<Float>()

                                    xCandidates.add(currentLabelWidth / 2f)
                                    yCandidates.add(currentLabelHeight / 2f)

                                    val otherElements = currentElements.filter { it.id != hitElement.id }
                                    for (other in otherElements) {
                                        val ob = getElementVisualBounds(other)
                                        val otherCx: Float
                                        if (other.type == LabelDesignElementType.TEXT) {
                                            hitTestPaint.textSize = other.fontSize.toFloat()
                                            val displayText = "${other.prefix}${other.placeholder}${other.suffix}"
                                            val tw = hitTestPaint.measureText(displayText)
                                            otherCx = other.x + tw / 2f
                                        } else {
                                            otherCx = (ob.left + ob.right) / 2f
                                        }
                                        xCandidates.add(otherCx)
                                        yCandidates.add((ob.top + ob.bottom) / 2f)
                                    }

                                    for (other in otherElements) {
                                        val ob = getElementVisualBounds(other)
                                        xCandidates.add(ob.left / 2f)
                                        xCandidates.add((ob.right + currentLabelWidth) / 2f)
                                        yCandidates.add(ob.top / 2f)
                                        yCandidates.add((ob.bottom + currentLabelHeight) / 2f)
                                    }

                                    for (i in otherElements.indices) {
                                        for (j in i + 1 until otherElements.size) {
                                            val a = getElementVisualBounds(otherElements[i])
                                            val b = getElementVisualBounds(otherElements[j])
                                            if (a.right < b.left) xCandidates.add((a.right + b.left) / 2f)
                                            else if (b.right < a.left) xCandidates.add((b.right + a.left) / 2f)
                                            if (a.bottom < b.top) yCandidates.add((a.bottom + b.top) / 2f)
                                            else if (b.bottom < a.top) yCandidates.add((b.bottom + a.top) / 2f)
                                        }
                                    }

                                    val guides = mutableListOf<Guideline>()

                                    val bestX = xCandidates.minByOrNull { (dragCx - it).absoluteValue }
                                    if (bestX != null && (dragCx - bestX).absoluteValue <= SNAP_THRESHOLD_DOTS) {
                                        guides.add(Guideline(bestX, isHorizontal = false))
                                        newDotX = (bestX - guideW / 2f).toInt()
                                            .coerceIn(0, (currentLabelWidth - 20).coerceAtLeast(0))
                                    }

                                    val bestY = yCandidates.minByOrNull { (dragCy - it).absoluteValue }
                                    if (bestY != null && (dragCy - bestY).absoluteValue <= SNAP_THRESHOLD_DOTS) {
                                        guides.add(Guideline(bestY, isHorizontal = true))
                                        newDotY = (bestY - dragH / 2f).toInt()
                                            .coerceIn(0, (currentLabelHeight - 20).coerceAtLeast(0))
                                    }

                                    activeGuidelines = guides
                                    currentOnElementMoved(hitElement.id, newDotX, newDotY)
                                }
                            }
                            change.consume()
                        }
                    }
                }
            }
    ) {
        drawIntoCanvas { canvas ->
            val nativeCanvas = canvas.nativeCanvas
            val cw = size.width
            val ch = size.height

            val bgPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#EEEEEE")
                style = Paint.Style.FILL
            }
            nativeCanvas.drawRect(0f, 0f, cw, ch, bgPaint)

            val margin = 16f * density
            val availW = cw - margin * 2
            val availH = ch - margin * 2
            if (availW <= 0 || availH <= 0) return@drawIntoCanvas

            val scaleX = availW / labelWidthDots.toFloat()
            val scaleY = availH / labelHeightDots.toFloat()
            val s = minOf(scaleX, scaleY)
            scale = s

            val scaledW = labelWidthDots * s
            val scaledH = labelHeightDots * s
            val ox = (cw - scaledW) / 2f
            val oy = (ch - scaledH) / 2f
            labelOffsetX = ox
            labelOffsetY = oy

            val shadowPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#33000000")
                style = Paint.Style.FILL
            }
            nativeCanvas.drawRoundRect(
                RectF(ox + 3f, oy + 3f, ox + scaledW + 3f, oy + scaledH + 3f),
                4f, 4f, shadowPaint
            )

            val labelBg = Paint().apply {
                color = android.graphics.Color.WHITE
                style = Paint.Style.FILL
            }
            nativeCanvas.drawRoundRect(
                RectF(ox, oy, ox + scaledW, oy + scaledH),
                4f, 4f, labelBg
            )

            val borderPaint = Paint().apply {
                color = android.graphics.Color.parseColor("#BBBBBB")
                style = Paint.Style.STROKE
                strokeWidth = 2f
            }
            nativeCanvas.drawRoundRect(
                RectF(ox, oy, ox + scaledW, oy + scaledH),
                4f, 4f, borderPaint
            )

            if (parsedLabel != null) {
                nativeCanvas.save()
                nativeCanvas.translate(ox, oy)
                val renderer = ZplRendererImpl()
                renderer.render(nativeCanvas, parsedLabel, s)
                nativeCanvas.restore()
            }

            nativeCanvas.save()
            nativeCanvas.translate(ox, oy)

            for (element in elements) {
                val ex = element.x * s
                val ey = element.y * s
                val isSelected = element.id == selectedElementId

                if (parsedLabel != null) {
                    if (isSelected) {
                        drawSelectionHighlight(nativeCanvas, element, ex, ey, s)
                    }
                } else {
                    when (element.type) {
                        LabelDesignElementType.TEXT -> {
                            drawTextElement(nativeCanvas, element, ex, ey, s, isSelected)
                        }
                        LabelDesignElementType.QR_CODE -> {
                            drawQrElement(nativeCanvas, element, ex, ey, s, isSelected)
                        }
                        LabelDesignElementType.BARCODE_128 -> {
                            drawBarcodeElement(nativeCanvas, element, ex, ey, s, isSelected)
                        }
                    }
                }
            }

            nativeCanvas.restore()

            if (currentGuidelines.isNotEmpty()) {
                val guidePaint = Paint().apply {
                    color = android.graphics.Color.parseColor("#E91E63")
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                    pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
                    isAntiAlias = true
                }

                nativeCanvas.save()
                nativeCanvas.translate(ox, oy)
                for (guide in currentGuidelines) {
                    if (guide.isHorizontal) {
                        val py = guide.position * s
                        nativeCanvas.drawLine(0f, py, scaledW, py, guidePaint)
                    } else {
                        val px = guide.position * s
                        nativeCanvas.drawLine(px, 0f, px, scaledH, guidePaint)
                    }
                }
                nativeCanvas.restore()
            }
        }
    }
}

private fun drawTextElement(
    canvas: android.graphics.Canvas,
    element: LabelDesignElement,
    x: Float,
    y: Float,
    scale: Float,
    isSelected: Boolean
) {
    val textSize = element.fontSize * scale
    val textPaint = Paint().apply {
        color = android.graphics.Color.BLACK
        this.textSize = textSize.coerceAtLeast(10f)
        isAntiAlias = true
    }

    val displayText = "${element.prefix}${element.placeholder}${element.suffix}"
    val textWidth = textPaint.measureText(displayText)
    val metrics = textPaint.fontMetrics
    val naturalHeight = -metrics.ascent + metrics.descent
    if (naturalHeight > 0) {
        textPaint.textSize = textSize * (textSize / naturalHeight)
    }
    val adjustedMetrics = textPaint.fontMetrics
    val textHeight = -adjustedMetrics.ascent + adjustedMetrics.descent

    if (isSelected) {
        val highlightPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#332196F3")
            style = Paint.Style.FILL
        }
        canvas.drawRect(x - 4f, y - 4f, x + textWidth + 6f, y + textHeight + 6f, highlightPaint)
        val selBorder = Paint().apply {
            color = android.graphics.Color.parseColor("#2196F3")
            style = Paint.Style.STROKE
            strokeWidth = 2f
        }
        canvas.drawRect(x - 4f, y - 4f, x + textWidth + 6f, y + textHeight + 6f, selBorder)
    }

    canvas.drawText(displayText, x, y + (-adjustedMetrics.ascent), textPaint)
}

private fun drawQrElement(
    canvas: android.graphics.Canvas,
    element: LabelDesignElement,
    x: Float,
    y: Float,
    scale: Float,
    isSelected: Boolean
) {
    val qrSize = element.magnification * 21f * scale

    val bgPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#F5F5F5")
        style = Paint.Style.FILL
    }
    canvas.drawRect(x, y, x + qrSize, y + qrSize, bgPaint)

    val borderPaint = Paint().apply {
        color = if (isSelected) android.graphics.Color.parseColor("#2196F3")
        else android.graphics.Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = if (isSelected) 3f else 1.5f
    }
    canvas.drawRect(x, y, x + qrSize, y + qrSize, borderPaint)

    if (isSelected) {
        val highlightPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#332196F3")
            style = Paint.Style.FILL
        }
        canvas.drawRect(x, y, x + qrSize, y + qrSize, highlightPaint)
    }

    val labelSize = (qrSize * 0.25f).coerceAtLeast(10f)
    val labelPaint = Paint().apply {
        color = android.graphics.Color.DKGRAY
        textSize = labelSize
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    canvas.drawText("QR", x + qrSize / 2f, y + qrSize * 0.4f, labelPaint)

    val namePaint = Paint().apply {
        color = android.graphics.Color.parseColor("#666666")
        textSize = (labelSize * 0.8f).coerceAtLeast(8f)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    canvas.drawText(element.placeholder, x + qrSize / 2f, y + qrSize * 0.7f, namePaint)

    val cornerSize = qrSize * 0.2f
    val cornerPaint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    canvas.drawRect(x + 4f, y + 4f, x + cornerSize, y + cornerSize, cornerPaint)
    canvas.drawRect(x + qrSize - cornerSize, y + 4f, x + qrSize - 4f, y + cornerSize, cornerPaint)
    canvas.drawRect(x + 4f, y + qrSize - cornerSize, x + cornerSize, y + qrSize - 4f, cornerPaint)
}

private fun drawBarcodeElement(
    canvas: android.graphics.Canvas,
    element: LabelDesignElement,
    x: Float,
    y: Float,
    scale: Float,
    isSelected: Boolean
) {
    val approxWidth = (element.moduleWidth * element.placeholder.length * 11f).coerceAtLeast(100f) * scale
    val height = element.barcodeHeight * scale

    val bgPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#F5F5F5")
        style = Paint.Style.FILL
    }
    canvas.drawRect(x, y, x + approxWidth, y + height, bgPaint)

    val stripePaint = Paint().apply {
        color = android.graphics.Color.BLACK
        style = Paint.Style.FILL
    }
    val stripeWidth = (element.moduleWidth * scale).coerceAtLeast(1f)
    var sx = x + 4f
    while (sx < x + approxWidth - 4f) {
        canvas.drawRect(sx, y + 4f, sx + stripeWidth, y + height - 4f, stripePaint)
        sx += stripeWidth * 2.5f
    }

    val borderPaint = Paint().apply {
        color = if (isSelected) android.graphics.Color.parseColor("#2196F3")
        else android.graphics.Color.DKGRAY
        style = Paint.Style.STROKE
        strokeWidth = if (isSelected) 3f else 1.5f
    }
    canvas.drawRect(x, y, x + approxWidth, y + height, borderPaint)

    if (isSelected) {
        val highlightPaint = Paint().apply {
            color = android.graphics.Color.parseColor("#332196F3")
            style = Paint.Style.FILL
        }
        canvas.drawRect(x, y, x + approxWidth, y + height, highlightPaint)
    }

    val labelPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#666666")
        textSize = (height * 0.2f).coerceAtLeast(8f)
        textAlign = Paint.Align.CENTER
        isAntiAlias = true
    }
    canvas.drawText(element.placeholder, x + approxWidth / 2f, y + height + labelPaint.textSize + 2f, labelPaint)
}

private fun drawSelectionHighlight(
    canvas: android.graphics.Canvas,
    element: LabelDesignElement,
    x: Float,
    y: Float,
    scale: Float
) {
    val w: Float
    val h: Float
    when {
        element.type == LabelDesignElementType.TEXT && element.blockWidth > 0 -> {
            w = element.blockWidth * scale
            h = element.fontSize * scale
        }
        else -> {
            val bounds = getElementVisualBounds(element)
            w = bounds.width() * scale
            h = bounds.height() * scale
        }
    }

    val highlightPaint = Paint().apply {
        color = android.graphics.Color.parseColor("#332196F3")
        style = Paint.Style.FILL
    }
    canvas.drawRect(x - 4f, y - 4f, x + w + 4f, y + h + 4f, highlightPaint)

    val selBorder = Paint().apply {
        color = android.graphics.Color.parseColor("#2196F3")
        style = Paint.Style.STROKE
        strokeWidth = 2f
    }
    canvas.drawRect(x - 4f, y - 4f, x + w + 4f, y + h + 4f, selBorder)
}

private val hitTestPaint = Paint().apply { isAntiAlias = true }
private const val MIN_HIT_SIZE_DOTS = 50f
private const val HIT_PADDING_DOTS = 15f

private fun hitTest(elements: List<LabelDesignElement>, dotX: Int, dotY: Int): LabelDesignElement? {
    for (element in elements.reversed()) {
        val bounds = getElementHitBounds(element)
        if (dotX >= bounds.left && dotX <= bounds.right &&
            dotY >= bounds.top && dotY <= bounds.bottom
        ) {
            return element
        }
    }
    return null
}

private fun getElementHitBounds(element: LabelDesignElement): RectF {
    val rawBounds = when (element.type) {
        LabelDesignElementType.TEXT -> {
            hitTestPaint.textSize = element.fontSize.toFloat()
            val displayText = "${element.prefix}${element.placeholder}${element.suffix}"
            val measuredWidth = hitTestPaint.measureText(displayText)
            val width = if (element.blockWidth > 0) element.blockWidth.toFloat() else measuredWidth
            val height = element.fontSize.toFloat()
            RectF(element.x.toFloat(), element.y.toFloat(), element.x + width, element.y + height)
        }
        LabelDesignElementType.QR_CODE -> {
            val size = element.magnification * 21f
            RectF(element.x.toFloat(), element.y.toFloat(), element.x + size, element.y + size)
        }
        LabelDesignElementType.BARCODE_128 -> {
            val approxWidth = (element.moduleWidth * element.placeholder.length * 11f).coerceAtLeast(100f)
            RectF(element.x.toFloat(), element.y.toFloat(), element.x + approxWidth, element.y + element.barcodeHeight.toFloat())
        }
    }

    val currentWidth = rawBounds.width()
    val currentHeight = rawBounds.height()
    if (currentWidth < MIN_HIT_SIZE_DOTS) {
        val expand = (MIN_HIT_SIZE_DOTS - currentWidth) / 2f
        rawBounds.left -= expand
        rawBounds.right += expand
    }
    if (currentHeight < MIN_HIT_SIZE_DOTS) {
        val expand = (MIN_HIT_SIZE_DOTS - currentHeight) / 2f
        rawBounds.top -= expand
        rawBounds.bottom += expand
    }

    rawBounds.left -= HIT_PADDING_DOTS
    rawBounds.top -= HIT_PADDING_DOTS
    rawBounds.right += HIT_PADDING_DOTS
    rawBounds.bottom += HIT_PADDING_DOTS

    return rawBounds
}

private fun getElementVisualBounds(element: LabelDesignElement): RectF {
    return when (element.type) {
        LabelDesignElementType.TEXT -> {
            hitTestPaint.textSize = element.fontSize.toFloat()
            val displayText = "${element.prefix}${element.placeholder}${element.suffix}"
            val measuredWidth = hitTestPaint.measureText(displayText)
            val width = if (element.blockWidth > 0) element.blockWidth.toFloat() else measuredWidth
            val height = element.fontSize.toFloat()
            RectF(
                element.x.toFloat(),
                element.y.toFloat(),
                element.x + width,
                element.y + height
            )
        }
        LabelDesignElementType.QR_CODE -> {
            val size = element.magnification * 21f
            RectF(
                element.x.toFloat(),
                element.y.toFloat(),
                element.x + size,
                element.y + size
            )
        }
        LabelDesignElementType.BARCODE_128 -> {
            val approxWidth = (element.moduleWidth * element.placeholder.length * 11f).coerceAtLeast(100f)
            RectF(
                element.x.toFloat(),
                element.y.toFloat(),
                element.x + approxWidth,
                element.y + element.barcodeHeight.toFloat()
            )
        }
    }
}
