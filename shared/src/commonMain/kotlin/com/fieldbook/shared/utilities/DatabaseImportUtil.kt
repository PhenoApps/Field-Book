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
    var importedPreferences = false

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

                entryName.endsWith(".xml", ignoreCase = true) -> {
                    updatePreferences(readPreferencesXml(entryBytes))
                    importedPreferences = true
                }
            }
        }
    }

    val database = databaseBytes ?: return DatabaseImportResult.NoDatabaseFile
    importDatabaseBytes(database)
    if (!importedPreferences) {
        selectFirstField()
    }
    return DatabaseImportResult.Success
}

private fun importDatabaseBytes(bytes: ByteArray) {
    writeAllBytes(PlatformEnv.databaseFilePath(DATABASE_NAME), bytes)
}

private sealed interface ImportedPreferenceValue {
    data class BooleanValue(val value: Boolean) : ImportedPreferenceValue
    data class IntValue(val value: Int) : ImportedPreferenceValue
    data class StringValue(val value: String) : ImportedPreferenceValue
    data object Unsupported : ImportedPreferenceValue
}

private fun updatePreferences(
    prefMap: Map<String, ImportedPreferenceValue>,
    settings: Settings = Settings(),
) {
    settings.clear()
    prefMap.forEach { (key, value) ->
        when (value) {
            is ImportedPreferenceValue.BooleanValue -> settings.putBoolean(key, value.value)
            is ImportedPreferenceValue.IntValue -> settings.putInt(key, value.value)
            is ImportedPreferenceValue.StringValue -> settings.putString(key, value.value)
            ImportedPreferenceValue.Unsupported -> Unit
        }
    }
}

private fun readPreferencesXml(bytes: ByteArray): Map<String, ImportedPreferenceValue> {
    val xml = bytes.decodeToString()
    val mapStart = Regex("""<map\b[^>]*>""").find(xml)?.range?.last?.plus(1) ?: 0
    val mapEnd = xml.lastIndexOf("</map>").takeIf { it >= mapStart } ?: xml.length
    val body = xml.substring(mapStart, mapEnd)
    val preferences = mutableMapOf<String, ImportedPreferenceValue>()

    preferenceElementRegex.findEach(body) { match ->
        val tagName = match.groupValues[1]
        val attributes = readXmlAttributes(match.groupValues[2])
        val name = attributes["name"] ?: return@findEach

        when (tagName) {
            "string" -> {
                val value = match.groups[3]?.value.orEmpty().trim().decodeXmlEntities()
                preferences[name] = ImportedPreferenceValue.StringValue(value)
            }

            "boolean" -> {
                attributes["value"]?.let { value ->
                    preferences[name] = ImportedPreferenceValue.BooleanValue(value.toBoolean())
                }
            }

            "int" -> {
                attributes["value"]?.toIntOrNull()?.let { value ->
                    preferences[name] = ImportedPreferenceValue.IntValue(value)
                }
            }

            "set" -> {
                preferences[name] = ImportedPreferenceValue.Unsupported
            }
        }
    }

    return preferences
}

private val preferenceElementRegex =
    Regex("""<([A-Za-z][A-Za-z0-9_-]*)\b([^>]*)(?:/>|>([\s\S]*?)</\1>)""")

private val xmlAttributeRegex =
    Regex("""([A-Za-z_:][A-Za-z0-9_:.-]*)\s*=\s*("[^"]*"|'[^']*')""")

private fun readXmlAttributes(attributes: String): Map<String, String> =
    buildMap {
        xmlAttributeRegex.findEach(attributes) { match ->
            val valueWithQuotes = match.groupValues[2]
            put(
                match.groupValues[1],
                valueWithQuotes.substring(1, valueWithQuotes.length - 1).decodeXmlEntities(),
            )
        }
    }

private inline fun Regex.findEach(input: CharSequence, action: (MatchResult) -> Unit) {
    var startIndex = 0
    while (startIndex <= input.length) {
        val match = find(input, startIndex) ?: break
        action(match)
        startIndex = if (match.range.isEmpty()) {
            match.range.last + 2
        } else {
            match.range.last + 1
        }
    }
}

private fun String.decodeXmlEntities(): String =
    replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&apos;", "'")
        .replace("&amp;", "&")

private fun clearImportedDatabaseState(settings: Settings = Settings()) {
    settings.putInt(GeneralKeys.SELECTED_FIELD_ID.key, -1)
    settings.putString(GeneralKeys.UNIQUE_NAME.key, "")
    settings.putString(GeneralKeys.PRIMARY_NAME.key, "")
    settings.putString(GeneralKeys.SECONDARY_NAME.key, "")
    settings.putBoolean(GeneralKeys.IMPORT_FIELD_FINISHED.key, false)
}
