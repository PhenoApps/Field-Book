package org.phenoapps.labelprint.zpl

/**
 * Internal mutable state used by the parser to track cursor position, font defaults,
 * field block settings, barcode defaults, and label dimensions during parsing.
 */
data class ParserState(
    val currentX: Int = 0,
    val currentY: Int = 0,
    val defaultFontName: Char = '0',
    val defaultFontHeight: Int = 28,
    val defaultFontWidth: Int = 0,
    val fieldBlockWidth: Int? = null,
    val fieldBlockMaxLines: Int = 1,
    val fieldBlockLineSpacing: Int = 0,
    val fieldBlockJustify: Justify = Justify.LEFT,
    val barcodeModuleWidth: Int = 2,
    val barcodeRatio: Float = 3.0f,
    val barcodeHeight: Int = 100,
    val labelWidth: Int = 812,
    val labelHeight: Int = 1218
)
