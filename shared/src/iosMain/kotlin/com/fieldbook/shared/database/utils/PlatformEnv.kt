package com.fieldbook.shared.database.utils

import platform.Foundation.*

actual object PlatformEnv : PlatformPaths {
    override fun databaseFilePath(dbName: String): String {
        val urls = NSFileManager.defaultManager
            .URLsForDirectory(NSDocumentDirectory, NSUserDomainMask) as NSArray
        val docs = urls.firstObject as? NSURL
            ?: error("Could not get documents directory URL")
        return docs.URLByAppendingPathComponent(dbName)?.path
            ?: error("Could not construct database file path")
    }
}
