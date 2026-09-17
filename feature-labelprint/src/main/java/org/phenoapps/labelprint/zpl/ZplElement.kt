package org.phenoapps.labelprint.zpl

/**
 * Text justification for field blocks.
 */
enum class Justify { LEFT, CENTER, RIGHT }

/**
 * Sealed class representing renderable elements positioned on a ZPL label.
 */
sealed class ZplElement {
    abstract val x: Int
    abstract val y: Int

    /**
     * A single-line text element rendered at the given position with font settings.
     */
    data class Text(
        override val x: Int,
        override val y: Int,
        val data: String,
        val fontHeight: Int = 28,
        val fontWidth: Int = 0,
        val fontName: Char = '0'
    ) : ZplElement()

    /**
     * A text block element that supports wrapping, justification, and multi-line rendering.
     */
    data class TextBlock(
        override val x: Int,
        override val y: Int,
        val data: String,
        val blockWidth: Int,
        val maxLines: Int = 1,
        val lineSpacing: Int = 0,
        val justify: Justify = Justify.LEFT,
        val fontHeight: Int = 28,
        val fontWidth: Int = 0,
        val fontName: Char = '0'
    ) : ZplElement()

    /**
     * A QR code element rendered using ZXing.
     */
    data class QrCode(
        override val x: Int,
        override val y: Int,
        val data: String,
        val model: Int = 2,
        val magnification: Int = 3
    ) : ZplElement()

    /**
     * A Code 128 barcode element rendered using ZXing.
     */
    data class Barcode128(
        override val x: Int,
        override val y: Int,
        val data: String,
        val height: Int = 100,
        val orientation: Char = 'N',
        val printInterpretation: Boolean = true,
        val interpretationAbove: Boolean = false,
        val moduleWidth: Int = 2,
        val ratio: Float = 3.0f
    ) : ZplElement()
}
