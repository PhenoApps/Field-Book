package com.fieldbook.tracker.repositories

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.fieldbook.tracker.application.IoDispatcher
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.GeneralKeys
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.core.content.edit
import com.fieldbook.tracker.R
import com.fieldbook.tracker.enums.FileFormat
import com.fieldbook.tracker.objects.FieldFileObject
import com.fieldbook.tracker.objects.TraitImportFile
import com.fieldbook.tracker.objects.toTraitJson
import com.fieldbook.tracker.utilities.CSVReader
import com.fieldbook.tracker.utilities.FileUtil
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.io.InputStreamReader
import java.util.UUID

class TraitRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: DataHelper,
    private val prefs: SharedPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    suspend fun getTraits(): List<TraitObject> = withContext(ioDispatcher) {
        database.getAllTraitObjects()
    }

    suspend fun deleteAllTraits(traits: List<TraitObject>) = withContext(ioDispatcher) {
        database.deleteTraitsTable()

        // clear crop coordinates
        prefs.edit {
            traits.forEach { trait ->
                remove(GeneralKeys.getCropCoordinatesKey(trait.id.toInt()))
            }
        }
    }

    suspend fun updateVisibility(id: String, visible: Boolean) = withContext(ioDispatcher) {
        database.updateTraitVisibility(id, visible)
    }

    suspend fun updateTraitOrder(traits: List<TraitObject>) = withContext(ioDispatcher) {
        traits.forEachIndexed { index, trait ->
            database.updateTraitPosition(trait.id, index + 1)
        }
    }

    // returns count of traits that were actually inserted
    suspend fun insertTraits(traits: List<TraitObject>): Int = withContext(ioDispatcher) {
        traits.count { database.insertTraits(it) != -1L }
    }

    suspend fun exportTraitsAsJson(
        fileName: String,
        traits: List<TraitObject>,
        onSuccess: suspend (Uri) -> Unit,
        onError: suspend (String) -> Unit,
    ) = withContext(ioDispatcher) {
        runCatching {
            val traitDir = BaseDocumentTreeUtil.getDirectory(context, R.string.dir_trait)
                ?: return@withContext onError("Trait directory not available")

            if (!traitDir.exists()) {
                return@withContext onError("Trait directory missing")
            }

            val exportFile = traitDir.createFile("*/*", fileName)
                ?: return@withContext onError("Failed to create file")

            val output = BaseDocumentTreeUtil.getFileOutputStream(context, R.string.dir_trait, fileName)
                ?: return@withContext onError("Output stream failed")

            output.use {
                val wrapper = TraitImportFile(traits.map { it.toTraitJson() })
                val jsonString = json.encodeToString(TraitImportFile.serializer(), wrapper)

                it.write(jsonString.toByteArray())
            }

            onSuccess(exportFile.uri)
        }
            .onFailure { e ->
                onError("Export failed: ${e.message}")
            }
    }

    suspend fun importTraits(sourceUri: Uri): List<TraitObject> = withContext(ioDispatcher) {
        val copiedUri = copyToTraitDirectory(sourceUri)
            ?: throw IllegalStateException("Unable to copy trait file")

        val format = detectTraitFileFormat(copiedUri)

        val maxPos = database.getAllTraitObjects().maxOfOrNull { it.realPosition } ?: 0

        return@withContext when (format) {
            FileFormat.JSON -> parseJsonTraits(copiedUri, maxPos)
            FileFormat.CSV -> parseCsvTraits(copiedUri, maxPos)
            else -> emptyList()
        }
    }

    /**
     * Returns copied file uri if successful, returns null otherwise
     */
    private fun copyToTraitDirectory(sourceUri: Uri): Uri? {
        val fileName = FileUtil()
            .getFileName(context, sourceUri)
            .replace(".trt", "_${UUID.randomUUID()}.trt")

        return sourceUri.copyToDirectory(context, R.string.dir_trait, fileName)
    }

    private fun detectTraitFileFormat(uri: Uri): FileFormat {
        return runCatching {
            context.contentResolver.openInputStream(uri)?.bufferedReader()?.use {
                val firstLine = it.readLine()?.trim().orEmpty()
                when {
                    firstLine.startsWith("{") || firstLine.startsWith("[") -> FileFormat.JSON
                    firstLine.contains("\"trait\"") -> FileFormat.CSV
                    else -> FileFormat.UNKNOWN
                }
            } ?: FileFormat.UNKNOWN
        }.getOrDefault(FileFormat.UNKNOWN)
    }

    private suspend fun parseJsonTraits(uri: Uri, maxPosition: Int): List<TraitObject> =
        withContext(ioDispatcher) {
            val stream = BaseDocumentTreeUtil.getUriInputStream(context, uri)
                ?: error("Cannot open JSON file")

            val jsonText = stream.bufferedReader().use { it.readText() }

            val wrapper = json.decodeFromString(TraitImportFile.serializer(), jsonText)

            val source = FieldFileObject.create(context, uri, null, null).fileStem

            wrapper.traits.mapNotNull { json ->
                runCatching {
                    TraitObject().apply {
                        name = json.name
                        alias = json.alias ?: json.name
                        synonyms = if (json.synonyms.isEmpty()) listOf(name) else json.synonyms
                        format = json.format
                        defaultValue = json.defaultValue
                        details = json.details
                        visible = json.visible
                        realPosition = maxPosition + json.position
                        traitDataSource = source

                        json.attributes?.forEach { (key, value) ->
                            val primitive = value as? JsonPrimitive
                            val stringValue = primitive?.content ?: value.toString()
                            val def =
                                com.fieldbook.tracker.database.models.TraitAttributes.byKey(key)
                            if (def != null) {
                                setAttributeValue(def, stringValue)
                            }
                        }
                    }
                }.getOrNull()
            }
        }

    private suspend fun parseCsvTraits(uri: Uri, maxPosition: Int): List<TraitObject> =
        withContext(ioDispatcher) {

            val list = mutableListOf<TraitObject>()

            BaseDocumentTreeUtil.getUriInputStream(context, uri)?.use { stream ->
                CSVReader(InputStreamReader(stream)).use { reader ->

                    reader.readNext() // skip header

                    val source = FieldFileObject.create(context, uri, null, null).fileStem

                    var row = reader.readNext()

                    while (row != null) {
                        val name = row.getOrNull(0)
                        val fmt = row.getOrNull(1)

                        if (name != null && fmt != null) {
                            val t = TraitObject().apply {
                                this.name = name
                                alias = name
                                synonyms = listOf(name)
                                format = fmt
                                defaultValue = row.getOrNull(2) ?: ""
                                minimum = row.getOrNull(3) ?: ""
                                maximum = row.getOrNull(4) ?: ""
                                details = row.getOrNull(5) ?: ""
                                categories = row.getOrNull(6) ?: ""
                                visible =
                                    row.getOrNull(7)?.equals("true", ignoreCase = true) != false
                                realPosition = maxPosition + (row.getOrNull(8)?.toIntOrNull() ?: 0)
                                traitDataSource = source

                                if (fmt == "multicat") {
                                    this.format = "categorical"
                                    allowMulticat = true
                                }
                            }
                            list.add(t)
                        }

                        row = reader.readNext()
                    }
                }
            }

            list
        }

    private fun Uri.copyToDirectory(context: Context, dirRes: Int, fileName: String): Uri? =
        runCatching {
            val traitDir = BaseDocumentTreeUtil.getDirectory(context, dirRes) ?: return null
            val destination = traitDir.createFile("*/*", fileName) ?: return null

            context.contentResolver.openInputStream(this)?.use { input ->
                BaseDocumentTreeUtil.getFileOutputStream(context, dirRes, fileName)?.use { output ->
                    input.copyTo(output)
                }
            } ?: return null

            destination.uri
        }.getOrNull()
}
