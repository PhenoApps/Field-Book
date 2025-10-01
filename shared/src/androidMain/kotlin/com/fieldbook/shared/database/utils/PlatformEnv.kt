package com.fieldbook.shared.database.utils

import com.fieldbook.shared.AndroidAppContextHolder

actual object PlatformEnv : PlatformPaths {
    override fun databaseFilePath(dbName: String) =
        AndroidAppContextHolder.context.getDatabasePath(dbName).absolutePath
}

