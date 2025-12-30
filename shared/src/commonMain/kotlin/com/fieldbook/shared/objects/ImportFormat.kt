package com.fieldbook.shared.objects

enum class ImportFormat(val format: String) {
    CSV("csv"),
    XLS("xls"),
    XLSX("xlsx"),
    BRAPI("brapi"),
    INTERNAL("internal");

    override fun toString(): String = format

    companion object {
        fun fromString(text: String?): ImportFormat? {
            return values().find { it.format.equals(text, ignoreCase = true) }
        }
    }
}
