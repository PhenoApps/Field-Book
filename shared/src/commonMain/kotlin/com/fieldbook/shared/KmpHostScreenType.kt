package com.fieldbook.shared

enum class KmpHostScreenType(val value: String) {
    CONFIG("config"),
    SCANNER("scanner"),
    FIELD_EDITOR("field_editor"),
    TRAIT_EDITOR("trait_editor"),
    COLLECT("collect"),
    EXPORT("export"),
    ABOUT("about"),
    PREFERENCES("preferences"),
    BRAPI_STUDIES("brapi_studies"),
    BRAPI_PREFERENCES("brapi_preferences"),
    FEATURE_PREFERENCES("feature_preferences"),
    APPEARANCE_PREFERENCES("appearance_preferences"),
    LANGUAGE_PREFERENCES("language_preferences"),
    STORAGE_PREFERENCES("storage_preferences"),
    STORAGE_DEFINER("storage_definer");

    companion object {
        fun fromValue(value: String): KmpHostScreenType {
            return KmpHostScreenType.entries.find { it.value.equals(value, ignoreCase = true) }
                ?: CONFIG
        }
    }
}
