package com.fieldbook.shared.brapi

import com.fieldbook.shared.AndroidAppContextHolder
import java.io.File

private fun brapiFilterCacheFile(fileName: String): File {
    val context = AndroidAppContextHolder.context
    val cacheDir = context.externalCacheDir ?: context.cacheDir
    return File(cacheDir, fileName)
}

internal actual fun readBrapiFilterCacheFile(fileName: String): String? {
    val file = brapiFilterCacheFile(fileName)
    return runCatching {
        if (file.exists()) file.readText() else null
    }.getOrNull()
}

internal actual fun writeBrapiFilterCacheFile(fileName: String, contents: String): Boolean {
    return runCatching {
        val file = brapiFilterCacheFile(fileName)
        file.parentFile?.mkdirs()
        file.writeText(contents)
        true
    }.getOrDefault(false)
}

internal actual fun deleteBrapiFilterCacheFile(fileName: String): Boolean {
    val file = brapiFilterCacheFile(fileName)
    return !file.exists() || file.delete()
}
