package com.fieldbook.shared.database.utils

import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.sqldelight.DriverFactory
import com.fieldbook.shared.sqldelight.closeDatabase
import com.fieldbook.shared.sqldelight.createDatabase
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.SYSTEM
import org.jetbrains.compose.resources.ExperimentalResourceApi

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

//    createDatabase(driverFactory)
}

@OptIn(ExperimentalResourceApi::class)
suspend fun importDatabaseFromBundled(
    driverFactory: DriverFactory,
    dbName: String, bundledName: String = "files/fieldbook.db"
) {
    println("test importDatabaseFromBundled to $dbName from $bundledName")
    val bytes = Res.readBytes(bundledName)
    println("test imported bundled db, size=${bytes.size}")
    val dst = PlatformEnv.databaseFilePath(dbName)
    println("test importing bundled db to $dst")
    writeAllBytes(driverFactory, dst, bytes)
}
