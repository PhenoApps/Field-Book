package com.fieldbook.shared.database.utils

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.sqldelight.DriverFactory
import com.fieldbook.shared.sqldelight.closeDatabase
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import org.jetbrains.compose.resources.ExperimentalResourceApi

const val DATABASE_VERSION: Long = 12
const val DATABASE_NAME = "fieldbook.db"

interface PlatformPaths {
    fun databaseFilePath(dbName: String): String
}

expect object PlatformEnv : PlatformPaths

fun fileExists(path: String) = FileSystem.SYSTEM.exists(path.toPath())

fun writeAllBytes(driverFactory: DriverFactory, path: String, bytes: ByteArray) {
    closeDatabase(driverFactory)

    val p = path.toPath()
    FileSystem.SYSTEM.createDirectories(p.parent!!)
    FileSystem.SYSTEM.write(p) { write(bytes) }

}

@OptIn(ExperimentalResourceApi::class)
suspend fun importDatabaseFromBundled(
    driverFactory: DriverFactory,
    dbName: String, bundledName: String = "files/$DATABASE_NAME"
) {
    val bytes = Res.readBytes(bundledName)
    val dst = PlatformEnv.databaseFilePath(dbName)
    writeAllBytes(driverFactory, dst, bytes)
}
