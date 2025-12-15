package com.fieldbook.tracker.database.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.fieldbook.tracker.R
import com.fieldbook.tracker.application.IoDispatcher
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.database.models.TraitAttributes
import com.fieldbook.tracker.enums.FileFormat
import com.fieldbook.tracker.objects.FieldFileObject
import com.fieldbook.tracker.objects.TraitImportFile
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.objects.toTraitJson
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.CSVReader
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.FileUtils.copyToDirectory
import com.fieldbook.tracker.utilities.TraitImportFileUtil.detectTraitFileFormat
import com.fieldbook.tracker.utilities.export.ValueProcessorFormatAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import org.phenoapps.utils.BaseDocumentTreeUtil
import java.io.InputStreamReader
import java.util.ArrayList
import java.util.UUID
import javax.inject.Inject

class TraitRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: DataHelper,
    private val prefs: SharedPreferences,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) {
    companion object {
        private const val TAG = "TraitRepository"
    }

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    val valueFormatter: ValueProcessorFormatAdapter
        get() = database.valueFormatter

    suspend fun getTraits(): List<TraitObject> = withContext(ioDispatcher) {
        database.getAllTraitObjects()
    }

    suspend fun getTraitById(id: String): TraitObject? = withContext(ioDispatcher) {
        database.getTraitById(id)
    }

    suspend fun getTraitByName(name: String): TraitObject? = withContext(ioDispatcher) {
        database.getTraitByName(name)
    }

    suspend fun getTraitByAlias(alias: String): TraitObject? = withContext(ioDispatcher) {
        database.getTraitByAlias(alias)
    }

    suspend fun getTraitByExternalDbId(externalDbId: String, dataSource: String): TraitObject? =
        withContext(ioDispatcher) {
            database.getTraitByExternalDbId(externalDbId, dataSource)
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

    suspend fun deleteTrait(traitId: String) = withContext(ioDispatcher) {
        database.deleteTrait(traitId)

        // clear crop coordinates
        prefs.edit {
            remove(GeneralKeys.getCropCoordinatesKey(traitId.toInt()))
        }
    }

    suspend fun updateTrait(trait: TraitObject) = withContext(ioDispatcher) {
        database.updateTrait(trait)
    }

    suspend fun updateTraitAlias(trait: TraitObject, newAlias: String) = withContext(ioDispatcher) {
        val updatedTrait = trait.clone().apply { alias = newAlias }

        val currentSynonyms = updatedTrait.synonyms.toMutableList()
        if (!currentSynonyms.contains(newAlias)) {
            // add to synonyms
            currentSynonyms.add(newAlias)
            updatedTrait.synonyms = currentSynonyms
        }

        updateTrait(updatedTrait)
        updatedTrait
    }

    suspend fun updateVisibility(id: String, visible: Boolean) = withContext(ioDispatcher) {
        database.updateTraitVisibility(id, visible)
    }

    suspend fun updateTraitOrder(traits: List<TraitObject>) = withContext(ioDispatcher) {
        traits.forEachIndexed { index, trait ->
            database.updateTraitPosition(trait.id, index + 1)
        }
    }

    // returns -1 if insertion failed, else returns rowId if successful
    suspend fun insertTrait(trait: TraitObject): Long = withContext(ioDispatcher) {
        database.insertTraits(trait)
    }

    // returns count of traits that were actually inserted
    suspend fun insertTraitsList(traits: List<TraitObject>): Int = withContext(ioDispatcher) {
        traits.count { insertTrait(it) != -1L }
    }

    suspend fun updateResourceFile(trait: TraitObject, fileUri: String): TraitObject? =
        withContext(ioDispatcher) {
            val updatedTrait = trait.clone().apply {
                resourceFile = fileUri
                saveAttributeValues()
                updateTrait(this)
            }
            updatedTrait
        }

    suspend fun updateAttributes(trait: TraitObject) = withContext(ioDispatcher) {
        trait.saveAttributeValues()
    }

    suspend fun getTraitObservations(traitId: String): Array<ObservationModel> =
        withContext(ioDispatcher) {
            database.getAllObservationsOfVariable(traitId)
        }

    suspend fun getMissingObservationCount(traitId: String): Int = withContext(ioDispatcher) {
        database.getMissingObservationsCount(traitId)
    }

    suspend fun getMaxPosition(): Int = withContext(ioDispatcher) {
        database.getMaxPositionFromTraits()
    }

    suspend fun copyTrait(baseTrait: TraitObject, newName: String): TraitObject? =
        withContext(ioDispatcher) {
            val pos = getMaxPosition() + 1

            val newTrait = baseTrait.clone().apply {
                name = newName
                alias = newName
                visible = true
                realPosition = pos
            }

            val inserted = insertTrait(newTrait) != -1L
            if (inserted) newTrait else null
        }

    fun changeTraitFormat(trait: TraitObject): TraitObject = TraitObject().apply {
        id = trait.id
        name = trait.name
        alias = trait.alias
        synonyms = trait.synonyms
        details = trait.details
    }

    suspend fun exportTraitsAsJson(
        fileName: String,
        traits: List<TraitObject>,
        onSuccess: suspend (Uri) -> Unit,
        onError: suspend (Int) -> Unit,
    ) = withContext(ioDispatcher) {
        runCatching {
            val traitDir =
                BaseDocumentTreeUtil.Companion.getDirectory(context, R.string.dir_trait)
                    ?: return@withContext onError(R.string.error_trait_directory_not_available)

            if (!traitDir.exists()) {
                return@withContext onError(R.string.error_trait_directory_missing)
            }

            val exportFile =
                traitDir.createFile("*/*", fileName)
                    ?: return@withContext onError(R.string.error_failed_to_create_file)

            val output =
                BaseDocumentTreeUtil.Companion.getFileOutputStream(
                    context,
                    R.string.dir_trait,
                    fileName
                )
                    ?: return@withContext onError(R.string.error_output_stream_failed)

            output.use {
                val wrapper = TraitImportFile(traits.map { it.toTraitJson() })
                val jsonString = json.encodeToString(TraitImportFile.serializer(), wrapper)

                it.write(jsonString.toByteArray())
            }

            onSuccess(exportFile.uri)
        }
            .onFailure { e ->
                Log.e(TAG, "Error exporting file", e)
                onError(R.string.error_export_failed)
            }
    }

    suspend fun parseTraits(
        sourceUri: Uri,
        onError: suspend (Int) -> Unit,
    ): List<TraitObject> = withContext(ioDispatcher) {
        // copy the file to dir_trait, and then import traits

        // generate a file name
        val fileName = FileUtil()
            .getFileName(context, sourceUri)
            .replace(".trt", "_${UUID.randomUUID()}.trt")

        val copiedUri =
            sourceUri.copyToDirectory(context, R.string.dir_trait, fileName)
                ?: run {
                    onError(R.string.error_unable_to_copy_trait_file)
                    return@withContext emptyList()
                }

        val format = detectTraitFileFormat(context, copiedUri)

        val maxPos = database.getAllTraitObjects().maxOfOrNull { it.realPosition } ?: 0

        return@withContext when (format) {
            FileFormat.JSON -> parseJsonTraits(copiedUri, maxPos, onError)
            FileFormat.CSV -> parseCsvTraits(copiedUri, maxPos, onError)
            else -> emptyList()
        }
    }

    private suspend fun parseJsonTraits(
        uri: Uri,
        maxPosition: Int,
        onError: suspend (Int) -> Unit,
    ): List<TraitObject> =
        withContext(ioDispatcher) {
            val stream = BaseDocumentTreeUtil.Companion.getUriInputStream(context, uri)
                ?: run {
                    onError(R.string.error_cannot_open_file)
                    return@withContext emptyList()
                }

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
                                TraitAttributes.byKey(key)
                            if (def != null) {
                                setAttributeValue(def, stringValue)
                            }
                        }
                    }
                }.getOrNull()
            }
        }

    private suspend fun parseCsvTraits(
        uri: Uri,
        maxPosition: Int,
        onError: suspend (Int) -> Unit,
    ): List<TraitObject> =
        withContext(ioDispatcher) {

            val list = mutableListOf<TraitObject>()

            val stream = BaseDocumentTreeUtil.Companion.getUriInputStream(context, uri)
                ?: run {
                    onError(R.string.error_cannot_open_file)
                    return@withContext emptyList()
                }

            stream.use { stream ->
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

    // BRAPI IMPORTS
    suspend fun saveTraitsFromHashmap(
        varUpdates: HashMap<String, TraitObject>,
        dbIds: ArrayList<String>?
    ) = withContext(ioDispatcher) {
        var nextPosition = getMaxPosition() + 1
        varUpdates.forEach { (t, u) ->
            if (t in dbIds!!) {
                insertTrait(u.apply {
                    realPosition = nextPosition++
                })
            }
        }
    }

    // to simplify usage in java
    fun saveTraitsFromBrapiBlocking(traits: List<TraitObject>): TraitSaveResult {
        return runBlocking {
            saveTraitsFromBrapi(traits)
        }
    }

    suspend fun saveTraitsFromBrapi(traits: List<TraitObject>): TraitSaveResult = withContext(ioDispatcher) {

        if (traits.isEmpty()) return@withContext TraitSaveResult()

        val maxPosition = getMaxPosition()
        var successfulSaves = 0
        val failedTraits = mutableListOf<TraitObject>()

        traits.forEachIndexed { index, trait ->
            runCatching { saveBrapiTraits(trait, maxPosition + index + 1) }
                .onSuccess { result ->
                    when (result) {
                        is TraitProcessResult.Success -> successfulSaves++
                        is TraitProcessResult.NameOrAliasConflict,
                             is TraitProcessResult.Error -> failedTraits.add(trait)
                    }
                }.onFailure { exception ->
                    Log.e(TAG, "Error saving trait: ${trait.name}", exception)
                    failedTraits.add(trait)
                }
        }

        TraitSaveResult(
            totalTraits = traits.size,
            successfulInserts = successfulSaves,
            failedInserts = failedTraits,
        )
    }

    private suspend fun saveBrapiTraits(trait: TraitObject, position: Int): TraitProcessResult {
        val existingTraitByName = getTraitByName(trait.name)
        val existingTraitByAlias = getTraitByAlias(trait.name)
        val existingTraitByExId = trait.externalDbId?.let {
            getTraitByExternalDbId(it, trait.traitDataSource)
        }

        return when {
            existingTraitByName != null || existingTraitByAlias != null -> {
                TraitProcessResult.NameOrAliasConflict
            }

            existingTraitByExId != null -> { // update existing trait
                trait.apply {
                    id = existingTraitByExId.id
                }
                val res = updateTrait(trait)
                if (res != -1L) TraitProcessResult.Success else TraitProcessResult.Error
            }

            else -> { // no conflicts, insert the new trait
                trait.apply {
                    realPosition = position
                    alias = name
                    synonyms = synonyms.ifEmpty { listOf(name) }
                }
                val res = insertTrait(trait)
                if (res != -1L) TraitProcessResult.Success else TraitProcessResult.Error
            }
        }
    }

}

private sealed class TraitProcessResult {
    object Success : TraitProcessResult()
    object NameOrAliasConflict : TraitProcessResult()
    object Error: TraitProcessResult()
}

/**
 * Used for saving trait in BrapiTraitActivity
 */
data class TraitSaveResult(
    val totalTraits: Int = 0,
    val successfulInserts: Int = 0,
    val failedInserts: List<TraitObject> = emptyList()
) {
    val allSuccess: Boolean get() = failedInserts.isEmpty()
    val allFailed: Boolean get() = successfulInserts == 0 && totalTraits > 0
    val oneFailed: Boolean get() = failedInserts.size == 1
}