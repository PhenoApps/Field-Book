@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.fieldbook.shared.utilities

import com.fieldbook.shared.preferences.GeneralKeys
import com.russhwolf.settings.Settings
import kotlinx.cinterop.BetaInteropApi
import okio.FileSystem
import okio.Path.Companion.toPath
import platform.Foundation.*
import platform.UIKit.UIActivityViewController
import platform.UIKit.UIApplication
import platform.UIKit.UIViewController
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

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

    override fun readBytes(): ByteArray = FileSystem.SYSTEM.read(path.toPath()) {
        readByteArray()
    }

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

private fun resolveDirectoryName(name: String): String = when (name) {
    "dir_field_export" -> "field_export"
    "dir_archive" -> "archive"
    "dir_trait" -> "trait"
    else -> name
}

private fun directoryPath(name: String): String {
    val resolvedName = resolveDirectoryName(name)
    val path = joinPath(storageBasePath(), resolvedName)
    ensureDirectoryExists(path)
    return path
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
    val configuredPath = normalizeStorageDirectoryPath(
        Settings()
        .getString(GeneralKeys.DEFAULT_STORAGE_LOCATION_DIRECTORY.key, "")
        .trim()
    )

    if (configuredPath.isNotEmpty() && pathType(configuredPath) == PathType.Directory) {
        return configuredPath
    }

    return defaultStorageBasePath()
}

actual fun createDir(
    parent: String,
    child: String
): DocumentFile? {
    val directoryPath = joinPath(joinPath(storageBasePath(), resolveDirectoryName(parent)), child)
    return directoryPath.takeIf { ensureDirectoryExists(it) }?.let(::IosDocumentFile)
}

actual fun getExportDirectory(): DocumentFile? =
    IosDocumentFile(directoryPath("dir_field_export"))

actual fun getArchiveDirectory(): DocumentFile? =
    IosDocumentFile(directoryPath("dir_archive"))

actual fun getTraitDirectory(): DocumentFile? =
    IosDocumentFile(directoryPath("dir_trait"))

actual fun listFiles(dir: DocumentFile): List<DocumentFile> {
    val iosDir = dir as? IosDocumentFile ?: return emptyList()
    if (!iosDir.isDirectory()) return emptyList()

    val contents = fileManager.contentsOfDirectoryAtPath(iosDir.path, error = null) as? List<*>
        ?: return emptyList()

    return contents.mapNotNull { entry ->
        (entry as? String)?.let { joinPath(iosDir.path, it) }?.let(::IosDocumentFile)
    }
}

actual fun copyFileToDirectory(source: DocumentFile, destinationDir: DocumentFile, newFileName: String): DocumentFile? {
    val src = source as? IosDocumentFile ?: return null
    val destDir = destinationDir as? IosDocumentFile ?: return null
    val destinationPath = joinPath(destDir.path, newFileName)

    return if (src.isDirectory()) {
        if (!ensureDirectoryExists(destinationPath)) return null
        val copiedDirectory = IosDocumentFile(destinationPath)
        listFiles(src).forEach { child ->
            val childName = child.name() ?: return@forEach
            copyFileToDirectory(child, copiedDirectory, childName)
        }
        copiedDirectory
    } else {
        destDir.createFile("application/octet-stream", newFileName)?.also { created ->
            created.writeBytes(src.readBytes())
        }
    }
}

actual fun zipFiles(files: List<DocumentFile>, zipFileName: String): DocumentFile? {
    val exportDir = getExportDirectory() ?: return null
    val bundleDir = exportDir.createDirectory("$zipFileName.export") ?: return null
    files.forEach { file ->
        val name = file.name() ?: return@forEach
        copyFileToDirectory(file, bundleDir, name)
    }
    return bundleDir
}

@OptIn(BetaInteropApi::class)
actual fun shareFile(file: DocumentFile) {
    val iosFile = file as? IosDocumentFile ?: return
    val fileUrl = NSURL.fileURLWithPath(iosFile.path)
    dispatch_async(dispatch_get_main_queue()) {
        val activityViewController = UIActivityViewController(
            activityItems = listOf(fileUrl),
            applicationActivities = null
        )

        topViewController()?.presentViewController(
            viewControllerToPresent = activityViewController,
            animated = true,
            completion = null
        ) ?: println("Unable to present share sheet for ${iosFile.path}")
    }
}

actual fun deleteFile(file: DocumentFile) {
    val iosFile = file as? IosDocumentFile ?: return
    if (!fileManager.fileExistsAtPath(iosFile.path)) {
        return
    }

    fileManager.removeItemAtPath(iosFile.path, error = null)
}

private fun topViewController(base: UIViewController? = UIApplication.sharedApplication.keyWindow?.rootViewController): UIViewController? {
    val presented = base?.presentedViewController
    return if (presented != null) topViewController(presented) else base
}

actual fun exportDeviceName(): String = "iOS"
