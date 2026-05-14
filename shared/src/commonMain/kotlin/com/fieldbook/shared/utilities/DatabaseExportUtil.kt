package com.fieldbook.shared.utilities

import com.fieldbook.shared.database.utils.DATABASE_NAME
import com.fieldbook.shared.database.utils.PlatformEnv
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.dir_database
import com.fieldbook.shared.sqldelight.FieldbookDatabase
import com.fieldbook.shared.sqldelight.closeDatabase
import com.russhwolf.settings.Settings
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
    val preferencesFile = createPreferencesBackupFile(databaseDir)
        ?: error("Unable to create preferences backup.")

    return try {
        zipFiles(
            files = listOf(databaseFile, preferencesFile),
            destinationDir = databaseDir,
            zipFileName = normalizedFileName
        ) ?: error("Unable to create database export.")
    } finally {
        runCatching { deleteFile(preferencesFile) }
    }
}

private fun createPreferencesBackupFile(databaseDir: DocumentFile): DocumentFile? {
    val fileName = "preferences_backup_${Clock.System.now().toEpochMilliseconds()}.xml"
    return databaseDir.createFile("text/xml", fileName)?.also { file ->
        file.writeBytes(createPreferencesXml().encodeToByteArray())
    }
}

private fun createPreferencesXml(settings: Settings = Settings()): String {
    val entries = settings.keys.sorted().mapNotNull { key ->
        preferenceXmlEntry(settings, key)
    }

    return buildString {
        appendLine("""<?xml version="1.0" encoding="utf-8" standalone="yes"?>""")
        appendLine("<map>")
        entries.forEach { entry ->
            appendLine("    $entry")
        }
        appendLine("</map>")
    }
}

private fun preferenceXmlEntry(settings: Settings, key: String): String? {
    runCatching { settings.getStringOrNull(key) }.getOrNull()?.let { value ->
        return """<string name="${escapeXmlAttribute(key)}">${escapeXmlText(value)}</string>"""
    }

    runCatching { settings.getBooleanOrNull(key) }.getOrNull()?.let { value ->
        return """<boolean name="${escapeXmlAttribute(key)}" value="$value" />"""
    }

    runCatching { settings.getIntOrNull(key) }.getOrNull()?.let { value ->
        return """<int name="${escapeXmlAttribute(key)}" value="$value" />"""
    }

    return null
}

private fun escapeXmlAttribute(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")

private fun escapeXmlText(value: String): String =
    value
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
