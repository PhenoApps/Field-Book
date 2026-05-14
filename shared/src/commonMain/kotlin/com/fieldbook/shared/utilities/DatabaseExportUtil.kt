package com.fieldbook.shared.utilities

import com.fieldbook.shared.database.utils.DATABASE_NAME
import com.fieldbook.shared.database.utils.PlatformEnv
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.dir_database
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.sqldelight.closeDatabase
import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

fun defaultDatabaseExportFileName(): String {
    val now = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault())
    val hour = now.hour.mod(12).let { if (it == 0) 12 else it }

    return buildString {
        append(now.year.toString().padStart(4, '0'))
        append("-")
        append(now.monthNumber.toString().padStart(2, '0'))
        append("-")
        append(now.dayOfMonth.toString().padStart(2, '0'))
        append("-")
        append(hour.toString().padStart(2, '0'))
        append("-")
        append(now.minute.toString().padStart(2, '0'))
        append("-")
        append(now.second.toString().padStart(2, '0'))
        append("_systemdb")
        append(FieldbookDatabase.Schema.version)
    }
}

fun exportDatabaseZip(fileName: String): DocumentFile {
    val normalizedFileName = fileName.trim().removeSuffix(".zip")
    require(normalizedFileName.isNotBlank()) { "Invalid export file name." }

    closeDatabase()

    val databaseFile = getFileByPath(PlatformEnv.databaseFilePath(DATABASE_NAME))
        ?: error("Database file was not found.")
    val databaseDir = getDirectory(Res.string.dir_database)
        ?: error("Database export directory was not found.")

    return zipFiles(
        files = listOf(databaseFile),
        destinationDir = databaseDir,
        zipFileName = normalizedFileName
    ) ?: error("Unable to create database export.")
}
