package org.phenoapps.labelprint.model

/**
 * The type of element on the visual label design canvas.
 */
enum class LabelDesignElementType {
    TEXT,
    DATE,
    QR_CODE,
    BARCODE_128;

    /**
     * Text and date elements share the same ZPL (^A0 + ^FD) and font/prefix/suffix settings,
     * a date element's placeholder is always [DATE_PLACEHOLDER].
     */
    val isTextLike: Boolean get() = this == TEXT || this == DATE

    companion object {
        const val DATE_PLACEHOLDER = "{date}"
    }
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
    val fontSize: Int = 28,
    val magnification: Int = 5,
    val blockWidth: Int = 0,
    val barcodeHeight: Int = 80,
    val moduleWidth: Int = 2,
    val prefix: String = "",
    val suffix: String = ""
)
