package com.fieldbook.shared.utilities

import com.fieldbook.shared.database.utils.DATABASE_NAME
import com.fieldbook.shared.database.utils.PlatformEnv
import com.fieldbook.shared.sqldelight.closeDatabase
import com.russhwolf.settings.Settings
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM

fun resetLocalDatabaseAndPreferences(): Boolean {
    closeDatabase()
    Settings().clear()

    val databasePath = PlatformEnv.databaseFilePath(DATABASE_NAME).toPath()
    FileSystem.SYSTEM.delete(databasePath, mustExist = false)

    return !FileSystem.SYSTEM.exists(databasePath)
}
