package org.phenoapps.labelprint.model

/**
 * The type of element on the visual label design canvas.
 */
enum class LabelDesignElementType {
    TEXT,
    QR_CODE,
    BARCODE_128
}

/**
 * A mutable element in the visual label editor.
 * Each element maps to one or more ZPL commands and can be dragged on the canvas.
 */
data class LabelDesignElement(
    val id: String,
    val type: LabelDesignElementType,
    val x: Int,
    val y: Int,
    val placeholder: String,
    val defaultValue: String = "",
    val fontSize: Int = 28,
    val magnification: Int = 5,
    val blockWidth: Int = 0,
    val barcodeHeight: Int = 80,
    val moduleWidth: Int = 2
)
