package com.fieldbook.shared

enum class KmpHostScreenType(val value: String) {
    CONFIG("config"),
    SCANNER("scanner"),
    FIELD_EDITOR("field_editor"),
    TRAIT_EDITOR("trait_editor"),
    COLLECT("collect"),
    PREFERENCES("preferences"),
    STORAGE_PREFERENCES("storage_preferences"),
    STORAGE_DEFINER("storage_definer");

    companion object {
        fun fromValue(value: String): KmpHostScreenType {
            return KmpHostScreenType.entries.find { it.value.equals(value, ignoreCase = true) }
                ?: CONFIG
        }
    }
}
