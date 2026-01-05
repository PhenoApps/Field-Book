package com.fieldbook.shared.screens.fields

enum class FieldPattern {
    LINEAR,
    ZIGZAG;

    companion object {
        fun fromString(value: String?): FieldPattern? = when (value?.lowercase()) {
            "linear" -> LINEAR
            "zigzag" -> ZIGZAG
            else -> null
        }
    }
}
