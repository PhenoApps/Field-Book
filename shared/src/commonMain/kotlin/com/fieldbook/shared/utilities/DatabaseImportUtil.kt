package com.fieldbook.shared.utilities

import com.fieldbook.shared.database.utils.DATABASE_NAME
import com.fieldbook.shared.database.utils.PlatformEnv
import com.fieldbook.shared.database.utils.writeAllBytes
import com.fieldbook.shared.generated.resources.Res
import com.fieldbook.shared.generated.resources.dir_database
import com.fieldbook.shared.preferences.GeneralKeys
import com.russhwolf.settings.Settings
import no.synth.kmpzip.zip.ZipInputStream

sealed class DatabaseImportResult {
    data object Success : DatabaseImportResult()
    data object NoDatabaseFile : DatabaseImportResult()
    data object UnsupportedFile : DatabaseImportResult()
}


fun availableDatabaseImportFiles(): List<DocumentFile> {
    val databaseDir = getDirectory(Res.string.dir_database) ?: return emptyList()
    return listFiles(databaseDir)
        .filter { file ->
            file.name()?.let { name ->
                name.endsWith(".db", ignoreCase = true) ||
                    name.endsWith(".zip", ignoreCase = true)
            } ?: false
        }
        .sortedBy { it.name().orEmpty().lowercase() }
}

fun importDatabaseFile(file: DocumentFile): DatabaseImportResult {
    val name = file.name().orEmpty()
    return when {
        name.endsWith(".db", ignoreCase = true) -> {
            importDatabaseBytes(file.readBytes())
            clearImportedDatabaseState()
            selectFirstField()
            DatabaseImportResult.Success
        }

        name.endsWith(".zip", ignoreCase = true) -> importDatabaseZip(file.readBytes())
        else -> DatabaseImportResult.UnsupportedFile
    }
}

private fun importDatabaseZip(bytes: ByteArray): DatabaseImportResult {
    var databaseBytes: ByteArray? = null

    ZipInputStream(bytes).use { zipInput ->
        while (true) {
            val entry = zipInput.nextEntry ?: break
            val entryName = entry.name
            val entryBytes = zipInput.readBytes()
            zipInput.closeEntry()

            when {
                entryName == DATABASE_NAME || entryName == "Output/$DATABASE_NAME" -> {
                    databaseBytes = entryBytes
                }
            }
        }
    }

    val database = databaseBytes ?: return DatabaseImportResult.NoDatabaseFile
    importDatabaseBytes(database)
    selectFirstField()
    return DatabaseImportResult.Success
}

private fun importDatabaseBytes(bytes: ByteArray) {
    writeAllBytes(PlatformEnv.databaseFilePath(DATABASE_NAME), bytes)
}

private fun clearImportedDatabaseState(settings: Settings = Settings()) {
    settings.putInt(GeneralKeys.SELECTED_FIELD_ID.key, -1)
    settings.putString(GeneralKeys.UNIQUE_NAME.key, "")
    settings.putString(GeneralKeys.PRIMARY_NAME.key, "")
    settings.putString(GeneralKeys.SECONDARY_NAME.key, "")
    settings.putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED.key, false)
}

