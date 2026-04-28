@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.fieldbook.shared.utilities

import com.fieldbook.shared.preferences.GeneralKeys
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.core.PlatformDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory

private enum class StoragePathType {
    Missing,
    File,
    Directory
}

private val fileManager: NSFileManager
    get() = NSFileManager.defaultManager

private fun storagePathType(path: String): StoragePathType {
    val attributes = fileManager.attributesOfItemAtPath(path, error = null) ?: return StoragePathType.Missing
    return when (attributes[NSFileType] as? String) {
        NSFileTypeDirectory -> StoragePathType.Directory
        else -> StoragePathType.File
    }
}

private fun joinStoragePath(parent: String, child: String): String =
    parent.trimEnd('/') + "/" + child.trimStart('/')

private fun ensureStorageDirectoryExists(path: String): Boolean {
    when (storagePathType(path)) {
        StoragePathType.Directory -> return true
        StoragePathType.File -> return false
        StoragePathType.Missing -> Unit
    }

    fileManager.createDirectoryAtPath(
        path = path,
        withIntermediateDirectories = true,
        attributes = null,
        error = null
    )

    return storagePathType(path) == StoragePathType.Directory
}

private fun ensureStorageDirectoryStructure(rootPath: String): Boolean {
    if (!ensureStorageDirectoryExists(rootPath)) return false

    return defaultStorageDirectoryNames().all { directoryName ->
        ensureStorageDirectoryExists(joinStoragePath(rootPath, directoryName))
    }
}

actual fun configurePickedStorageDirectory(directory: PlatformDirectory): String? {
    val path = normalizeStorageDirectoryPath(directory.path ?: "")
    if (path.isBlank()) return null

    return path.takeIf(::ensureStorageDirectoryStructure)
}

actual fun isStorageDirectoryConfigured(): Boolean {
    val configuredPath = normalizeStorageDirectoryPath(
        Settings().getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY.key, "")
    )

    return configuredPath.isNotBlank() && ensureStorageDirectoryStructure(configuredPath)
}
