package com.fieldbook.shared.utilities

import platform.Foundation.NSURL

actual fun normalizeStorageDirectoryPath(rawPath: String): String {
    val trimmed = rawPath.trim()
    if (trimmed.isEmpty()) return ""

    if (trimmed.startsWith("file://")) {
        val url = NSURL.URLWithString(trimmed)
        val normalized = url?.path
        if (!normalized.isNullOrBlank()) {
            return normalized
        }
    }

    return trimmed
}
