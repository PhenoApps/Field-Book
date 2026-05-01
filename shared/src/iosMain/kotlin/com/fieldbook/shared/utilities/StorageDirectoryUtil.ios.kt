@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.fieldbook.shared.utilities

import com.fieldbook.shared.preferences.GeneralKeys
import com.russhwolf.settings.Settings
import io.github.vinceglb.filekit.core.PlatformDirectory
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSArray
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.dataWithContentsOfFile
import platform.Foundation.firstObject
import platform.Foundation.writeToFile

private enum class StoragePathType {
    Missing,
    File,
    Directory
}

private val fileManager: NSFileManager
    get() = NSFileManager.defaultManager

private var activeStorageDirectoryUrl: NSURL? = null
private var activeStorageDirectoryPath: String? = null

private fun storagePathType(path: String): StoragePathType {
    val attributes = fileManager.attributesOfItemAtPath(path, error = null) ?: return StoragePathType.Missing
    return when (attributes[NSFileType] as? String) {
        NSFileTypeDirectory -> StoragePathType.Directory
        else -> StoragePathType.File
    }
}

private fun joinStoragePath(parent: String, child: String): String =
    parent.trimEnd('/') + "/" + child.trimStart('/')

private fun bookmarkFilePath(): String {
    val urls = fileManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask) as NSArray
    val appSupportDir = (urls.firstObject as? NSURL)
        ?: error("NSFileManager.URLsForDirectory returned empty result for Application Support")
    val fieldBookDir = appSupportDir.URLByAppendingPathComponent("FieldBook", isDirectory = true)?.path
        ?: error("Unable to resolve FieldBook application support directory")

    ensureStorageDirectoryExists(fieldBookDir)
    return joinStoragePath(fieldBookDir, "storage-directory.bookmark")
}

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

private fun saveStorageDirectoryBookmark(url: NSURL) {
    try {
        val bookmarkData = url.bookmarkDataWithOptions(
            options = 0u,
            includingResourceValuesForKeys = null,
            relativeToURL = null,
            error = null
        )
        bookmarkData?.writeToFile(bookmarkFilePath(), atomically = true)
    } catch (error: Throwable) {
        println("StorageDirectoryUtil: unable to save storage bookmark: ${error.message}")
    }
}

private fun resolveStorageDirectoryBookmark(): NSURL? {
    return try {
        val bookmarkData = NSData.dataWithContentsOfFile(bookmarkFilePath()) ?: return null
        NSURL.URLByResolvingBookmarkData(
            bookmarkData = bookmarkData,
            options = 0u,
            relativeToURL = null,
            bookmarkDataIsStale = null,
            error = null
        )
    } catch (error: Throwable) {
        println("StorageDirectoryUtil: unable to resolve storage bookmark: ${error.message}")
        null
    }
}

internal fun accessStorageDirectoryPath(path: String): Boolean {
    val normalizedPath = normalizeStorageDirectoryPath(path)
    if (normalizedPath.isBlank()) return false

    if (activeStorageDirectoryPath == normalizedPath && activeStorageDirectoryUrl != null) {
        return true
    }

    activeStorageDirectoryUrl?.stopAccessingSecurityScopedResource()
    val bookmarkUrl = resolveStorageDirectoryBookmark()
    val url = bookmarkUrl?.takeIf {
        normalizeStorageDirectoryPath(it.path ?: "") == normalizedPath
    } ?: NSURL.fileURLWithPath(normalizedPath, isDirectory = true)
    val canAccess = url.startAccessingSecurityScopedResource()
    activeStorageDirectoryUrl = url
    activeStorageDirectoryPath = normalizedPath
    return canAccess || storagePathType(normalizedPath) == StoragePathType.Directory
}

internal fun accessConfiguredStorageForPath(path: String): Boolean {
    val configuredPath = normalizeStorageDirectoryPath(
        Settings().getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY.key, "")
    )
    val normalizedPath = normalizeStorageDirectoryPath(path)

    if (configuredPath.isBlank() || normalizedPath.isBlank()) return false
    if (normalizedPath != configuredPath && !normalizedPath.startsWith("$configuredPath/")) return false

    return accessStorageDirectoryPath(configuredPath)
}

actual fun configurePickedStorageDirectory(directory: PlatformDirectory): String? {
    val directoryUrl = directory.nsUrl
    val path = directoryUrl.path?.let(::normalizeStorageDirectoryPath)
        ?: normalizeStorageDirectoryPath(directory.path ?: "")
    if (path.isBlank()) return null

    activeStorageDirectoryUrl?.takeIf { activeStorageDirectoryPath != path }
        ?.stopAccessingSecurityScopedResource()
    directoryUrl.startAccessingSecurityScopedResource()
    activeStorageDirectoryUrl = directoryUrl
    activeStorageDirectoryPath = path

    saveStorageDirectoryBookmark(directoryUrl)

    return path.takeIf(::ensureStorageDirectoryStructure)
}

actual fun isStorageDirectoryConfigured(): Boolean {
    val configuredPath = normalizeStorageDirectoryPath(
        Settings().getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY.key, "")
    )

    return configuredPath.isNotBlank()
        && accessStorageDirectoryPath(configuredPath)
        && ensureStorageDirectoryStructure(configuredPath)
}
