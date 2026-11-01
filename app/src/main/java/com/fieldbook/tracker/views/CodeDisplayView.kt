package com.fieldbook.tracker.views

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.AttributeSet
import android.util.Log
import android.view.View
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import kotlin.math.floor
import kotlin.math.min

/**
 * Draws a barcode or 2D code as large as the view allows.
 * The code is encoded at one pixel per module and scaled up when drawn, so it never needs
 * re-encoding when the view changes size.
 */
class CodeDisplayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        private const val TAG = "CodeDisplayView"

        //quiet zone widths in modules, linear codes need a wider margin than matrix codes
        private const val QUIET_ZONE_MATRIX = 4
        private const val QUIET_ZONE_LINEAR = 10

        val DEFAULT_FORMAT = BarcodeFormat.QR_CODE

        //writable formats in the order offered to the user, labels are symbology names and aren't translated
        val FORMATS = linkedMapOf(
            BarcodeFormat.QR_CODE to "QR Code",
            BarcodeFormat.DATA_MATRIX to "Data Matrix",
            BarcodeFormat.AZTEC to "Aztec",
            BarcodeFormat.PDF_417 to "PDF417",
            BarcodeFormat.CODE_128 to "Code 128",
            BarcodeFormat.CODE_39 to "Code 39",
            BarcodeFormat.CODE_93 to "Code 93",
            BarcodeFormat.CODABAR to "Codabar",
            BarcodeFormat.ITF to "ITF",
            BarcodeFormat.EAN_13 to "EAN-13",
            BarcodeFormat.EAN_8 to "EAN-8",
            BarcodeFormat.UPC_A to "UPC-A",
            BarcodeFormat.UPC_E to "UPC-E"
        )

        fun formatOf(name: String?): BarcodeFormat =
            FORMATS.keys.find { it.name == name } ?: DEFAULT_FORMAT

        private val LINEAR_FORMATS = setOf(
            BarcodeFormat.CODE_128, BarcodeFormat.CODE_39, BarcodeFormat.CODE_93,
            BarcodeFormat.CODABAR, BarcodeFormat.ITF, BarcodeFormat.EAN_13,
            BarcodeFormat.EAN_8, BarcodeFormat.UPC_A, BarcodeFormat.UPC_E
        )

        fun isLinear(format: BarcodeFormat) = format in LINEAR_FORMATS
    }

    private var codeBitmap: Bitmap? = null
    private var isLinearCode = false

    //nearest neighbour sampling keeps module edges sharp when scaled
    private val paint = Paint().apply {
        isFilterBitmap = false
        isAntiAlias = false
    }

    private val destination = Rect()

    /**
     * Encodes the text in the given format.
     * @return false if the text can't be encoded in that format, s.a. letters in an EAN-13
     */
    fun setCode(text: String, format: BarcodeFormat): Boolean {
        isLinearCode = isLinear(format)
        codeBitmap = try {
            toBitmap(MultiFormatWriter().encode(text, format, 0, 0, hintsFor(text)))
        } catch (e: Exception) {
            Log.w(TAG, "Unable to encode $format", e)
            null
        }
        invalidate()
        return codeBitmap != null
    }

    fun clear() {
        codeBitmap = null
        invalidate()
    }

    //writers disagree on default margins, so disable theirs and add a consistent quiet zone here
    private fun hintsFor(text: String): Map<EncodeHintType, Any> = buildMap {
        put(EncodeHintType.MARGIN, 0)
        if (text.any { it.code > 0x7F }) put(EncodeHintType.CHARACTER_SET, Charsets.UTF_8.name())
    }

    private fun toBitmap(matrix: BitMatrix): Bitmap {
        val zoneX = if (isLinearCode) QUIET_ZONE_LINEAR else QUIET_ZONE_MATRIX
        val zoneY = if (isLinearCode) 0 else QUIET_ZONE_MATRIX
        val width = matrix.width + zoneX * 2
        val height = matrix.height + zoneY * 2
        val pixels = IntArray(width * height) { Color.WHITE }
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix.get(x, y)) pixels[(y + zoneY) * width + x + zoneX] = Color.BLACK
            }
        }
        return Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val bitmap = codeBitmap ?: return
        val availableWidth = width - paddingLeft - paddingRight
        val availableHeight = height - paddingTop - paddingBottom
        if (availableWidth <= 0 || availableHeight <= 0) return

        //linear codes only scale horizontally, their bars stretch to fill the height
        val widthScale = availableWidth / bitmap.width.toFloat()
        val fit = if (isLinearCode) widthScale else min(widthScale, availableHeight / bitmap.height.toFloat())

        //whole pixels per module keep every bar and cell the same size, unless the code is too big for that
        val scale = if (fit >= 1f) floor(fit) else fit

        val drawWidth = (bitmap.width * scale).toInt()
        val drawHeight = if (isLinearCode) availableHeight else (bitmap.height * scale).toInt()
        val left = paddingLeft + (availableWidth - drawWidth) / 2
        val top = paddingTop + (availableHeight - drawHeight) / 2

        destination.set(left, top, left + drawWidth, top + drawHeight)
        canvas.drawBitmap(bitmap, null, destination, paint)
    }
}
