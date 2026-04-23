@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.fieldbook.shared.utilities

import com.fieldbook.shared.preferences.GeneralKeys
import com.russhwolf.settings.Settings
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import platform.Foundation.*

private enum class PathType {
    Missing,
    File,
    Directory
}

private class IosDocumentFile(internal val path: String) : DocumentFile {

    override fun createDirectory(name: String): DocumentFile? {
        val directoryPath = joinPath(path, name)
        return when (pathType(directoryPath)) {
            PathType.Directory -> IosDocumentFile(directoryPath)
            PathType.File -> null
            PathType.Missing -> directoryPath.takeIf(::ensureDirectoryExists)?.let(::IosDocumentFile)
        }
    }

    override fun createFile(mimeType: String, name: String): DocumentFile? {
        val filePath = joinPath(path, name)
        val parentPath = filePath.toPath().parent?.toString()
        if (parentPath != null && !ensureDirectoryExists(parentPath)) {
            return null
        }

        when (pathType(filePath)) {
            PathType.Directory -> return null
            PathType.File -> return IosDocumentFile(filePath)
            PathType.Missing -> {
                fileManager.createFileAtPath(filePath, contents = null, attributes = null)
            }
        }

        return filePath.takeIf { pathType(it) == PathType.File }?.let(::IosDocumentFile)
    }

    override fun findFile(name: String): DocumentFile? {
        val filePath = joinPath(path, name)
        return filePath.takeIf(fileManager::fileExistsAtPath)?.let(::IosDocumentFile)
    }

    override fun exists(): Boolean = pathType(path) != PathType.Missing

    override fun isDirectory(): Boolean = pathType(path) == PathType.Directory

    override fun uri(): String = NSURL.fileURLWithPath(path).absoluteString ?: "file://$path"

    override fun writeBytes(byteArray: ByteArray) {
        val outputPath = path.toPath()
        outputPath.parent?.let { FileSystem.SYSTEM.createDirectories(it) }
        FileSystem.SYSTEM.write(outputPath) {
            write(byteArray)
        }
    }

    override fun name(): String? = path.toPath().name

}

private val fileManager: NSFileManager
    get() = NSFileManager.defaultManager

private fun joinPath(parent: String, child: String): String =
    parent.trimEnd('/') + "/" + child.trimStart('/')

private fun pathType(path: String): PathType {
    val attributes = fileManager.attributesOfItemAtPath(path, error = null) ?: return PathType.Missing
    return when (attributes[NSFileType] as? String) {
        NSFileTypeDirectory -> PathType.Directory
        else -> PathType.File
    }
}

private fun ensureDirectoryExists(path: String): Boolean {
    when (pathType(path)) {
        PathType.Directory -> return true
        PathType.File -> return false
        PathType.Missing -> Unit
    }

    fileManager.createDirectoryAtPath(
        path = path,
        withIntermediateDirectories = true,
        attributes = null,
        error = null
    )

    return pathType(path) == PathType.Directory
}

private fun defaultStorageBasePath(): String {
    val urls = fileManager.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask) as NSArray
    val appSupportDir = (urls.firstObject as? NSURL)
        ?: error("NSFileManager.URLsForDirectory returned empty result for Application Support")

    // Default iOS storage stays inside the app sandbox when no user-selected folder is available.
    val fieldBookDir = appSupportDir.URLByAppendingPathComponent("FieldBook", isDirectory = true)?.path
        ?: error("Unable to resolve FieldBook application support directory")

    ensureDirectoryExists(fieldBookDir)
    return fieldBookDir
}

private fun storageBasePath(): String {
    val configuredPath = Settings()
        .getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY.key, "")
        .trim()

    if (configuredPath.isNotEmpty() && pathType(configuredPath) == PathType.Directory) {
        return configuredPath
    }

    return defaultStorageBasePath()
}

actual fun createDir(
    parent: String,
    child: String
): DocumentFile? {
    val directoryPath = joinPath(joinPath(storageBasePath(), parent), child)
    return directoryPath.takeIf { ensureDirectoryExists(it) }?.let(::IosDocumentFile)
}

actual fun getExportDirectory(): DocumentFile? = TODO("Not yet implemented")
actual fun getArchiveDirectory(): DocumentFile? = TODO("Not yet implemented")
actual fun listFiles(dir: DocumentFile): List<DocumentFile> = TODO("Not yet implemented")
actual fun copyFileToDirectory(source: DocumentFile, destinationDir: DocumentFile, newFileName: String): DocumentFile? =
    TODO("Not yet implemented")
actual fun zipFiles(files: List<DocumentFile>, zipFileName: String): DocumentFile? = TODO("Not yet implemented")
actual fun shareFile(file: DocumentFile): Unit = TODO("Not yet implemented")
actual fun deleteFile(file: DocumentFile) {
    val iosFile = file as? IosDocumentFile ?: return
    if (!fileManager.fileExistsAtPath(iosFile.path)) {
        return
    }

    fileManager.removeItemAtPath(iosFile.path, error = null)
}
actual fun exportDeviceName(): String = "iOS"
