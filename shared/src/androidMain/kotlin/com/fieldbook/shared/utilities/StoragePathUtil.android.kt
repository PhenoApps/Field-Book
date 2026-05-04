package com.fieldbook.shared.utilities

import android.net.Uri

actual fun normalizeStorageDirectoryPath(rawPath: String): String = rawPath.trim()

actual fun displayStorageDirectoryPath(rawPath: String): String {
    val normalized = normalizeStorageDirectoryPath(rawPath)
    if (normalized.isBlank()) return ""

    val uri = runCatching { Uri.parse(normalized) }.getOrNull()
    val treePath = uri?.lastPathSegment?.let(Uri::decode)
    val relativePath = treePath?.substringAfter(':', treePath)

    return relativePath
        ?.takeIf { it.isNotBlank() }
        ?: Uri.decode(normalized)
}

actual fun detectStorageProviderType(rawPath: String): StorageProviderType {
    val normalized = normalizeStorageDirectoryPath(rawPath)
    if (normalized.isBlank()) return StorageProviderType.UNKNOWN

    val uri = runCatching { Uri.parse(normalized) }.getOrNull()
    val authority = uri?.authority.orEmpty()
    val treePath = uri?.lastPathSegment?.let(Uri::decode).orEmpty()

    return when {
        treePath.startsWith("primary:") -> StorageProviderType.PRIMARY_LOCAL
        authority == "com.android.externalstorage.documents" && treePath.contains(":") ->
            StorageProviderType.EXTERNAL_LOCAL
        authority.isNotBlank() -> StorageProviderType.SYNCED_PROVIDER
        else -> StorageProviderType.UNKNOWN
    }
}

actual fun detectStorageProviderLabel(rawPath: String): String {
    val normalized = normalizeStorageDirectoryPath(rawPath)
    if (normalized.isBlank()) return ""

    val uri = runCatching { Uri.parse(normalized) }.getOrNull()
    val authority = uri?.authority.orEmpty()
    val treePath = uri?.lastPathSegment?.let(Uri::decode).orEmpty()
    val volumeId = treePath.substringBefore(':', "")

    return when {
        treePath.startsWith("primary:") -> "primary"
        authority == "com.android.externalstorage.documents" && volumeId.isNotBlank() -> volumeId
        authority.isNotBlank() -> "cloud"
        else -> ""
    }
}
