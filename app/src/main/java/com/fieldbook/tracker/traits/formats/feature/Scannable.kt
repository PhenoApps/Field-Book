package com.fieldbook.tracker.traits.formats.feature

/**
 * Interface for defining traits that can be scanned in from a barcode.
 */
interface Scannable {

    fun preprocess(barcodeValue: String): String {
        return barcodeValue
    }
}

class PercentageScannable : Scannable {
    override fun preprocess(barcodeValue: String): String {
        return barcodeValue.replace("%", "")
    }
}