package com.fieldbook.shared

enum class KmpHostScreenType(val value: String) {
    CONFIG("config"),
    SCANNER("scanner"),
    FIELD_EDITOR("field_editor"),
    COLLECT("collect");

    companion object {
        fun fromValue(value: String): KmpHostScreenType {
            return values().find { it.value.equals(value, ignoreCase = true) } ?: CONFIG
        }
    }
}
