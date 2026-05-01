package com.fieldbook.shared.utilities

enum class StorageProviderType {
    PRIMARY_LOCAL,
    EXTERNAL_LOCAL,
    SYNCED_PROVIDER,
    UNKNOWN
}

fun storageProviderTypeFromName(rawValue: String): StorageProviderType {
    return StorageProviderType.entries.firstOrNull { it.name == rawValue } ?: StorageProviderType.UNKNOWN
}

fun displayStorageDirectoryPath(rawPath: String, providerTypeName: String): String {
    return displayStorageDirectoryPath(rawPath, providerTypeName, "")
}

fun displayStorageDirectoryPath(rawPath: String, providerTypeName: String, providerLabel: String): String {
    val displayPath = displayStorageDirectoryPath(rawPath)
    if (displayPath.isBlank()) return ""

    val providerType = storageProviderTypeFromName(providerTypeName).takeUnless {
        it == StorageProviderType.UNKNOWN
    } ?: detectStorageProviderType(rawPath)
    val resolvedLabel = providerLabel.ifBlank { detectStorageProviderLabel(rawPath) }

    return when (providerType) {
        StorageProviderType.PRIMARY_LOCAL -> "primary:$displayPath"
        StorageProviderType.EXTERNAL_LOCAL -> {
            if (resolvedLabel.isBlank() || resolvedLabel.equals("external", ignoreCase = true)) {
                "external:$displayPath"
            } else {
                "external($resolvedLabel):$displayPath"
            }
        }
        StorageProviderType.SYNCED_PROVIDER -> "cloud:$displayPath"
        StorageProviderType.UNKNOWN -> displayPath
    }
}

expect fun normalizeStorageDirectoryPath(rawPath: String): String

expect fun displayStorageDirectoryPath(rawPath: String): String

expect fun detectStorageProviderType(rawPath: String): StorageProviderType

expect fun detectStorageProviderLabel(rawPath: String): String
