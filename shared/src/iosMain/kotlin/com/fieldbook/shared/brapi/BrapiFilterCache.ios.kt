package com.fieldbook.shared.brapi

import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSArray
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.Foundation.firstObject

private fun brapiFilterCachePath(fileName: String): Path? {
    val urls = NSFileManager.defaultManager.URLsForDirectory(NSCachesDirectory, NSUserDomainMask) as NSArray
    val cacheDir = (urls.firstObject as? NSURL)?.path ?: return null
    return "${cacheDir.trimEnd('/')}/${fileName.trimStart('/')}".toPath()
}

internal actual fun readBrapiFilterCacheFile(fileName: String): String? {
    val path = brapiFilterCachePath(fileName) ?: return null
    return runCatching {
        if (FileSystem.SYSTEM.exists(path)) {
            FileSystem.SYSTEM.read(path) { readUtf8() }
        } else {
            null
        }
    }.getOrNull()
}

internal actual fun writeBrapiFilterCacheFile(fileName: String, contents: String): Boolean {
    val path = brapiFilterCachePath(fileName) ?: return false
    return runCatching {
        path.parent?.let(FileSystem.SYSTEM::createDirectories)
        FileSystem.SYSTEM.write(path) { writeUtf8(contents) }
        true
    }.getOrDefault(false)
}

internal actual fun deleteBrapiFilterCacheFile(fileName: String): Boolean {
    val path = brapiFilterCachePath(fileName) ?: return false
    return runCatching {
        FileSystem.SYSTEM.delete(path, mustExist = false)
        true
    }.getOrDefault(false)
}
