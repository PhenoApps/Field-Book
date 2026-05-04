package com.fieldbook.shared.utilities

import platform.Foundation.NSURL

private const val FILE_PROVIDER_MARKER = "/File Provider Storage/"
private const val MOBILE_DOCUMENTS_MARKER = "/Mobile Documents/"
private const val CLOUD_STORAGE_MARKER = "/Library/CloudStorage/"
private const val VOLUMES_MARKER = "/Volumes/"
private const val ICLOUD_DOCUMENTS_PREFIX = "com~apple~CloudDocs/"

private fun relativeStoragePath(normalizedPath: String): String {
    val fileProviderIndex = normalizedPath.indexOf(FILE_PROVIDER_MARKER)
    if (fileProviderIndex >= 0) {
        return normalizedPath
            .substring(fileProviderIndex + FILE_PROVIDER_MARKER.length)
            .trim('/')
    }

    val mobileDocumentsIndex = normalizedPath.indexOf(MOBILE_DOCUMENTS_MARKER)
    if (mobileDocumentsIndex >= 0) {
        return normalizedPath
            .substring(mobileDocumentsIndex + MOBILE_DOCUMENTS_MARKER.length)
            .removePrefix(ICLOUD_DOCUMENTS_PREFIX)
            .trim('/')
    }

    val cloudStorageIndex = normalizedPath.indexOf(CLOUD_STORAGE_MARKER)
    if (cloudStorageIndex >= 0) {
        return normalizedPath
            .substring(cloudStorageIndex + CLOUD_STORAGE_MARKER.length)
            .substringAfter('/', "")
            .trim('/')
    }

    val volumesIndex = normalizedPath.indexOf(VOLUMES_MARKER)
    if (volumesIndex >= 0) {
        return normalizedPath
            .substring(volumesIndex + VOLUMES_MARKER.length)
            .substringAfter('/', "")
            .trim('/')
    }

    return normalizedPath
        .trimEnd('/')
        .substringAfterLast("/")
}

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

actual fun displayStorageDirectoryPath(rawPath: String): String {
    val normalized = normalizeStorageDirectoryPath(rawPath)
    if (normalized.isBlank()) return ""

    return relativeStoragePath(normalized)
}

actual fun detectStorageProviderType(rawPath: String): StorageProviderType {
    val normalized = normalizeStorageDirectoryPath(rawPath)
    if (normalized.isBlank()) return StorageProviderType.UNKNOWN

    return when {
        normalized.contains(FILE_PROVIDER_MARKER) -> StorageProviderType.PRIMARY_LOCAL
        normalized.contains(MOBILE_DOCUMENTS_MARKER) -> StorageProviderType.SYNCED_PROVIDER
        normalized.contains(CLOUD_STORAGE_MARKER) -> StorageProviderType.SYNCED_PROVIDER
        normalized.contains(VOLUMES_MARKER) -> StorageProviderType.EXTERNAL_LOCAL
        normalized.startsWith("/private/var/mobile/Containers/Shared/AppGroup/") ->
            StorageProviderType.PRIMARY_LOCAL
        else -> StorageProviderType.UNKNOWN
    }
}

actual fun detectStorageProviderLabel(rawPath: String): String {
    val normalized = normalizeStorageDirectoryPath(rawPath)
    if (normalized.isBlank()) return ""

    return when {
        normalized.contains(FILE_PROVIDER_MARKER) -> "primary"
        normalized.contains(MOBILE_DOCUMENTS_MARKER) -> "cloud"
        normalized.contains(CLOUD_STORAGE_MARKER) -> "cloud"
        normalized.contains(VOLUMES_MARKER) -> normalized
            .substringAfter(VOLUMES_MARKER)
            .substringBefore('/')
            .trim()
        normalized.startsWith("/private/var/mobile/Containers/Shared/AppGroup/") -> "primary"
        else -> ""
    }
}
