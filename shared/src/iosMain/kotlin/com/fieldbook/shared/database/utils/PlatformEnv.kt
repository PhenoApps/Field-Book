package com.fieldbook.shared.database.utils

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.memScoped
import platform.Foundation.*

actual object PlatformEnv : PlatformPaths {
    @OptIn(ExperimentalForeignApi::class)
    override fun databaseFilePath(dbName: String): String {
        val fm = NSFileManager.defaultManager
        val urls = fm.URLsForDirectory(NSApplicationSupportDirectory, NSUserDomainMask) as NSArray
        val appSupportDir: NSURL = (urls.firstObject as? NSURL)
            ?: error("NSFileManager.URLsForDirectory returned empty result for Application Support")
        val databasesDir = appSupportDir.URLByAppendingPathComponent("databases", isDirectory = true)!!

        val dirPath = databasesDir.path ?: ((appSupportDir.path ?: "") + "/databases")
        if (!fm.fileExistsAtPath(dirPath)) {
            fm.createDirectoryAtPath(
                path = dirPath,
                withIntermediateDirectories = true,
                attributes = null,
                error = null
            )
        }

        val dbUrl = databasesDir.URLByAppendingPathComponent(dbName, isDirectory = false)!!
        return dbUrl.path!!
    }
}
