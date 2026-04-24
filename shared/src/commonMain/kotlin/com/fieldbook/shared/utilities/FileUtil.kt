package com.fieldbook.shared.utilities

private val illegalFileNameChars = charArrayOf('|', '?', '*', '<', '"', '\\', ':', '>', '\'', '/', ';')

fun sanitizeFileName(name: String): String {
    return buildString(name.length) {
        name.forEach { char ->
            append(if (char in illegalFileNameChars) '_' else char)
        }
    }
}

fun checkForIllegalCharacters(name: String): String {
    return buildSet {
        name.forEach { char ->
            if (char in illegalFileNameChars) add(char)
        }
    }.joinToString(" ")
}
