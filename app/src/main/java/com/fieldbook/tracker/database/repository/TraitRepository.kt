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
import com.fieldbook.tracker.objects.TraitImportFile
import com.fieldbook.tracker.objects.TraitJson
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.objects.toTraitJson
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.utilities.CSVReader
import com.fieldbook.tracker.utilities.FileUtil
import com.fieldbook.tracker.utilities.FileUtils.copyToDirectory
import com.fieldbook.tracker.utilities.TraitRefRenameRepairResult
import com.fieldbook.tracker.utilities.TreeDerivedTraitHelper
import com.fieldbook.tracker.utilities.TreeSchemaLoader
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
        val trait = database.getTraitById(traitId)
        if (trait != null) {
            if (trait.format.equals(Formats.TREE_ARCHITECTURE.getDatabaseName(), ignoreCase = true)) {
                val summary = TreeDerivedTraitHelper.resolveDerivedTrait(database, traitId)
                if (summary != null) {
                    database.deleteTrait(summary.id)
                    prefs.edit {
                        remove(GeneralKeys.getCropCoordinatesKey(summary.id.toInt()))
                    }
                }
            } else if (trait.format.equals(Formats.TREE_SUMMARY.getDatabaseName(), ignoreCase = true)) {
                val source = TreeDerivedTraitHelper.resolveSourceTrait(database, traitId)
                if (source != null) {
                    source.additionalInfo = TreeDerivedTraitHelper.clearTreeLinkKeys(source.additionalInfo)
                    database.updateTrait(source)
                }
            }
        }

        database.deleteTrait(traitId)

        // clear crop coordinates
        prefs.edit {
            remove(GeneralKeys.getCropCoordinatesKey(traitId.toInt()))
        }
    }

    suspend fun updateTrait(trait: TraitObject): TraitUpdateResult = withContext(ioDispatcher) {
        val oldTrait = database.getTraitById(trait.id)
        val res = database.updateTrait(trait)
        if (oldTrait != null && oldTrait.name != trait.name) {
            if (trait.format.equals(Formats.TREE_ARCHITECTURE.getDatabaseName(), ignoreCase = true)) {
                val summary = TreeDerivedTraitHelper.resolveDerivedTrait(database, trait.id)
                if (summary != null) {
                    summary.name = "${trait.name} (summary)"
                    summary.alias = summary.name
                    summary.synonyms = listOf(summary.name)
                    summary.details = "Derived from ${trait.name}"
                    database.updateTrait(summary)
                }
            }
            // Best-effort: rename plot_data/<field>/<oldTrait>/ → sanitized new name.
            maybeRenameTraitMediaFolder(oldTrait.name, trait.name)
        }
        val repair = if (oldTrait != null && oldTrait.name != trait.name) {
            val allTraits = database.getAllTraitObjects()
            val hasTree = allTraits.any {
                it.format.equals(Formats.TREE_ARCHITECTURE.getDatabaseName(), ignoreCase = true)
            }
            if (!hasTree) {
                null
            } else {
                TreeSchemaLoader.repairTraitRefsAfterRename(
                    context = context,
                    treeTraits = allTraits,
                    oldName = oldTrait.name,
                    newName = trait.name,
                ).also { result ->
                    if (result.touchedAny) {
                        Log.w(
                            TAG,
                            "Trait rename '${oldTrait.name}' → '${trait.name}': " +
                                "updated ${result.schemasUpdated} schema(s), " +
                                "${result.schemasUnwritable} unwritable",
                        )
                    }
                }
            }
        } else {
            null
        }
        TraitUpdateResult(rowId = res, traitRefRepair = repair)
    }

    /** Best-effort SAF rename of the trait media folder after a study trait rename. */
    private fun maybeRenameTraitMediaFolder(oldName: String, newName: String) {
        val oldFolder = FileUtil.sanitizeFileName(oldName)
        val newFolder = FileUtil.sanitizeFileName(newName)
        if (oldFolder.isBlank() || oldFolder == newFolder) return
        runCatching {
            val prefs = androidx.preference.PreferenceManager.getDefaultSharedPreferences(context)
            val fieldNames = linkedSetOf<String>()
            database.allFieldObjects.mapNotNullTo(fieldNames) { it.name?.takeIf { n -> n.isNotBlank() } }
            prefs.getString(GeneralKeys.FIELD_FILE, null)
                ?.takeIf { it.isNotBlank() }
                ?.let { fieldNames += it }
            for (field in fieldNames) {
                renameTraitMediaFolderInField(field, oldFolder, newFolder)
            }
        }.onFailure {
            Log.w(TAG, "Trait media folder rename skipped: ${it.message}")
        }
    }

    private fun renameTraitMediaFolderInField(field: String, oldFolder: String, newFolder: String) {
        val mediaRoot = BaseDocumentTreeUtil.getFile(context, R.string.dir_plot_data, field) ?: return
        val oldDir = mediaRoot.findFile(oldFolder)?.takeIf { it.isDirectory } ?: return
        if (mediaRoot.findFile(newFolder) != null) return
        val dest = mediaRoot.createDirectory(newFolder) ?: return
        val children = oldDir.listFiles().filter { it.isFile && !it.name.isNullOrBlank() }
        var copied = 0
        for (child in children) {
            val name = child.name ?: continue
            val target = dest.createFile(child.type ?: "*/*", name) ?: run {
                Log.w(TAG, "Trait media rename: failed to create $name in $newFolder; aborting delete of $oldFolder")
                return
            }
            val ok = runCatching {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    context.contentResolver.openOutputStream(target.uri)?.use { output ->
                        input.copyTo(output)
                    } ?: error("no output stream")
                } ?: error("no input stream")
            }.isSuccess
            if (!ok) {
                Log.w(TAG, "Trait media rename: copy failed for $name; leaving $oldFolder intact")
                return
            }
            copied++
        }
        if (copied != children.size) {
            Log.w(TAG, "Trait media rename: copied $copied/${children.size}; leaving $oldFolder intact")
            return
        }
        if (!oldDir.delete()) {
            Log.w(TAG, "Trait media rename: copied OK but could not delete $oldFolder")
        }
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
        val inserted = mutableListOf<TraitObject>()
        traits.forEach { trait ->
            if (insertTrait(trait) != -1L) {
                inserted += trait
            } else {
                // Drop stale exporter link ids so they cannot remount onto wrong DB rows.
                trait.additionalInfo = TreeDerivedTraitHelper.clearTreeLinkKeys(trait.additionalInfo)
                applyTreeResourceFileOnNameConflict(trait)
            }
        }
        val updatedTraits = TreeDerivedTraitHelper.remapLinksAfterImport(inserted)
        updatedTraits.forEach { updateTrait(it) }
        inserted.size
    }

    /**
     * Name-conflict import skips insert, but embedded-schema restore may still have
     * written a local schema and set [TraitObject.resourceFile]. Point the existing
     * tree trait at that restored file when the incoming ref is usable.
     */
    private suspend fun applyTreeResourceFileOnNameConflict(incoming: TraitObject) {
        if (!incoming.format.equals(Formats.TREE_ARCHITECTURE.getDatabaseName(), ignoreCase = true)) {
            return
        }
        if (incoming.resourceFile.isBlank()) return

        val existing = database.getTraitByName(incoming.name)
            ?: incoming.alias.takeIf { it.isNotBlank() }?.let { database.getTraitByAlias(it) }
            ?: return
        if (!existing.format.equals(Formats.TREE_ARCHITECTURE.getDatabaseName(), ignoreCase = true)) {
            return
        }
        if (existing.resourceFile == incoming.resourceFile) return

        val incomingReadable = TreeSchemaLoader.readText(context, incoming.resourceFile) != null
        val existingReadable = existing.resourceFile.isNotBlank() &&
            TreeSchemaLoader.readText(context, existing.resourceFile) != null
        // Update when import restored a readable schema, or when existing is blank/unreadable.
        if (incomingReadable || !existingReadable) {
            updateResourceFile(existing, incoming.resourceFile)
        }
    }

    suspend fun updateResourceFile(trait: TraitObject, fileUri: String): TraitObject =
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
                // Copied summaries stay export-only; never force into Collect carousel.
                visible = if (TreeDerivedTraitHelper.isExportOnlySummary(baseTrait)) false else true
                realPosition = pos
                // Drop peer tree↔summary IDs so we do not flush into the original's summary.
                additionalInfo = TreeDerivedTraitHelper.clearTreeLinkKeys(additionalInfo)
            }

            if (insertTrait(newTrait) == -1L) return@withContext null

            // TREE_SUMMARY is created lazily when the copied tree first produces content.

            newTrait
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
        onSuccess: suspend (Uri, Int) -> Unit,
        onError: suspend (Int) -> Unit,
    ) = withContext(ioDispatcher) {
        runCatching {
            val traitDir =
                BaseDocumentTreeUtil.getDirectory(context, R.string.dir_trait)
                    ?: return@withContext onError(R.string.error_trait_directory_not_available)

            if (!traitDir.exists()) {
                return@withContext onError(R.string.error_trait_directory_missing)
            }

            val exportFile =
                traitDir.createFile("*/*", fileName)
                    ?: return@withContext onError(R.string.error_failed_to_create_file)

            val output =
                BaseDocumentTreeUtil.getFileOutputStream(
                    context,
                    R.string.dir_trait,
                    fileName
                )
                    ?: return@withContext onError(R.string.error_output_stream_failed)

            var missingEmbeddedSchemaCount = 0
            output.use {
                val wrapper = TraitImportFile(
                    traits.map { trait ->
                        val (json, omittedSchema) = toExportTraitJson(trait)
                        if (omittedSchema) missingEmbeddedSchemaCount++
                        json
                    },
                )
                val jsonString = json.encodeToString(TraitImportFile.serializer(), wrapper)

                it.write(jsonString.toByteArray())
            }

            onSuccess(exportFile.uri, missingEmbeddedSchemaCount)
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

        val originalFileName = FileUtil()
            .getFileName(context, sourceUri)

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
            FileFormat.JSON -> parseJsonTraits(copiedUri, originalFileName, maxPos, onError)
            FileFormat.CSV -> parseCsvTraits(copiedUri, originalFileName, maxPos, onError)
            else -> emptyList()
        }
    }

    private suspend fun parseJsonTraits(
        uri: Uri,
        originalFileName: String,
        maxPosition: Int,
        onError: suspend (Int) -> Unit,
    ): List<TraitObject> =
        withContext(ioDispatcher) {
            val stream = BaseDocumentTreeUtil.getUriInputStream(context, uri)
                ?: run {
                    onError(R.string.error_cannot_open_file)
                    return@withContext emptyList()
                }

            val jsonText = stream.bufferedReader().use { it.readText() }

            val wrapper = json.decodeFromString(TraitImportFile.serializer(), jsonText)

            wrapper.traits.mapNotNull { json ->
                runCatching {
                    TraitObject.fromJson(json, maxPosition, originalFileName).apply {
                        TreeDerivedTraitHelper.coerceExportOnlySummaryVisibility(this)
                        if (format == Formats.TREE_ARCHITECTURE.getDatabaseName() && !json.embeddedSchema.isNullOrBlank()) {
                            TreeSchemaLoader.saveEmbeddedSchema(
                                context = context,
                                traitName = name,
                                resourceRef = resourceFile,
                                schemaText = json.embeddedSchema,
                            )?.let { savedRef ->
                                resourceFile = savedRef
                            }
                        }
                    }
                }.getOrNull()
            }
        }

    private suspend fun parseCsvTraits(
        uri: Uri,
        originalFileName: String,
        maxPosition: Int,
        onError: suspend (Int) -> Unit,
    ): List<TraitObject> =
        withContext(ioDispatcher) {

            val list = mutableListOf<TraitObject>()

            val stream = BaseDocumentTreeUtil.getUriInputStream(context, uri)
                ?: run {
                    onError(R.string.error_cannot_open_file)
                    return@withContext emptyList()
                }

            stream.use { stream ->
                CSVReader(InputStreamReader(stream)).use { reader ->

                    reader.readNext() // skip header

                    var row = reader.readNext()

                    while (row != null) {
                        val name = row.getOrNull(0)
                        val fmt = row.getOrNull(1)

                        if (name != null && fmt != null) {
                            val fmtLower = fmt.lowercase()
                            val t = TraitObject().apply {
                                this.name = name
                                alias = name
                                synonyms = listOf(name)
                                format = fmtLower
                                defaultValue = row.getOrNull(2) ?: ""
                                minimum = row.getOrNull(3) ?: ""
                                maximum = row.getOrNull(4) ?: ""
                                details = row.getOrNull(5) ?: ""
                                categories = row.getOrNull(6) ?: ""
                                visible = row.getOrNull(7)?.equals("true", ignoreCase = true) != false
                                realPosition = maxPosition + (row.getOrNull(8)?.toIntOrNull() ?: 0)
                                traitDataSource = originalFileName

                                if (fmtLower == "multicat") {
                                    this.format = "categorical"
                                    allowMulticat = true
                                }
                            }
                            TreeDerivedTraitHelper.coerceExportOnlySummaryVisibility(t)
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
            existingTraitByExId != null -> { // update existing trait
                trait.apply {
                    id = existingTraitByExId.id
                }
                val res = updateTrait(trait)
                if (res.rowId != -1L) TraitProcessResult.Success else TraitProcessResult.Error
            }

            existingTraitByName != null || existingTraitByAlias != null -> {
                TraitProcessResult.NameOrAliasConflict
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

    private fun toExportTraitJson(trait: TraitObject): Pair<TraitJson, Boolean> {
        if (trait.format != Formats.TREE_ARCHITECTURE.getDatabaseName()) {
            return trait.toTraitJson(embeddedSchema = null) to false
        }
        val embeddedSchema = TreeSchemaLoader.readText(context, trait.resourceFile)
        val omittedUnreadable = embeddedSchema.isNullOrBlank() && trait.resourceFile.isNotBlank()
        if (omittedUnreadable) {
            Log.w(
                TAG,
                "Omitting embeddedSchema for tree trait \"${trait.name}\": " +
                    "resourceFile unreadable (${trait.resourceFile})",
            )
        }
        return trait.toTraitJson(embeddedSchema = embeddedSchema) to omittedUnreadable
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

/** Result of [TraitRepository.updateTrait], including optional R-18 schema repair. */
data class TraitUpdateResult(
    val rowId: Long,
    val traitRefRepair: TraitRefRenameRepairResult? = null,
)