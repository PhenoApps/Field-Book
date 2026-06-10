package com.fieldbook.tracker.database.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import androidx.core.content.edit
import com.fieldbook.tracker.R
import com.fieldbook.tracker.application.IoDispatcher
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.dao.FieldTraitConfigDao
import com.fieldbook.tracker.database.models.ObservationModel
import com.fieldbook.tracker.enums.FileFormat
import com.fieldbook.tracker.objects.TraitImportFile
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.objects.toTraitJson
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.TraitScopePreferences
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

    private val fieldTraitConfigDao = FieldTraitConfigDao()

    val valueFormatter: ValueProcessorFormatAdapter
        get() = database.valueFormatter

    suspend fun getTraits(
        studyId: Int? = null,
        usePerFieldList: Boolean = false,
        sortOrder: String = "position",
    ): List<TraitObject> =
        withContext(ioDispatcher) {
            val allTraits = database.getAllTraitObjects()

            if (!usePerFieldList || studyId == null || studyId < 0) {
                return@withContext allTraits
            }

            // Ensure field-specific configuration is initialized on first access
            ensureStudyVisibilityInitialized(studyId)

            val scopedIds = TraitScopePreferences.getOrInitializeStudyTraitIds(
                preferences = prefs,
                studyId = studyId,
                fallbackTraitIds = allTraits.map { it.id }.toSet(),
            )

            // Since we ensured initialization above, hasFieldConfig should be true
            // if the study is valid.
            val hasFieldConfig = fieldTraitConfigDao.hasFieldTraitConfig(studyId)
            
            // Per-field visibility is stored in preferences
            val customVisibleIds = TraitScopePreferences.getStudyVisibleTraitIds(prefs, studyId)
            
            val scopedVisibleIds: Set<String> = if (customVisibleIds != null) {
                customVisibleIds.intersect(scopedIds)
            } else {
                allTraits.filter { it.visible && it.id in scopedIds }.map { it.id }.toSet()
            }

            Log.d(TAG, "getTraits: studyId=$studyId hasFieldConfig=$hasFieldConfig scopedVisibleCount=${scopedVisibleIds.size}")

            var filtered = allTraits
                .filter { it.id in scopedIds }
                .map { trait ->
                    trait.clone().apply { visible = id in scopedVisibleIds }
                }

            // For field-specific lists, prefer the field-specific trait order when one exists.
            // Fall back to observation_variables position (global order) when no field order is set.
            if (sortOrder == "position") {
                val fieldOrder = if (hasFieldConfig) fieldTraitConfigDao.getTraitIdsInOrder(studyId) else emptyList()
                filtered = if (fieldOrder.isNotEmpty()) {
                    val orderMap = fieldOrder.mapIndexed { index, id -> id to index }.toMap()
                    filtered.sortedWith(compareBy { orderMap[it.id] ?: Int.MAX_VALUE })
                } else {
                    filtered.sortedWith(
                        compareBy<TraitObject> { it.realPosition }
                            .thenBy { it.id.toIntOrNull() ?: Int.MAX_VALUE }
                    )
                }
            } else if (sortOrder == "visible") {
                // Keep visible traits at the top when sorting by visibility.
                filtered = filtered.sortedWith(
                    compareByDescending<TraitObject> { it.visible }
                        .thenBy { it.alias.ifBlank { it.name }.lowercase() }
                )
            } else if (sortOrder == "observation_variable_name") {
                filtered = filtered.sortedBy { it.alias.ifBlank { it.name }.lowercase() }
            } else if (sortOrder == "observation_variable_field_book_format") {
                filtered = filtered.sortedWith(
                    compareBy<TraitObject> { it.format.lowercase() }
                        .thenBy { it.alias.ifBlank { it.name }.lowercase() }
                )
            } else if (sortOrder == "internal_id_observation_variable") {
                filtered = filtered.sortedWith(
                    compareBy<TraitObject> { it.id.toIntOrNull() ?: Int.MAX_VALUE }
                        .thenBy { it.alias.ifBlank { it.name }.lowercase() }
                )
            }

            filtered
        }

    suspend fun getUnscopedTraits(): List<TraitObject> = withContext(ioDispatcher) {
        database.getAllTraitObjects()
    }

    suspend fun addTraitsToStudy(studyId: Int, traitIds: Set<String>) = withContext(ioDispatcher) {
        TraitScopePreferences.addStudyTraitIds(prefs, studyId, traitIds)
        // Ensure field is initialized before adding new traits as visible
        ensureStudyVisibilityInitialized(studyId)

        val allTraitsInGlobalOrder = database.getAllTraitObjects()
        val orderedNewIds = allTraitsInGlobalOrder
            .map { it.id }
            .filter { it in traitIds }

        // Update ordering
        val currentOrder = fieldTraitConfigDao.getTraitIdsInOrder(studyId).toMutableList()
        orderedNewIds.forEach { newId ->
            if (newId !in currentOrder) currentOrder.add(newId)
        }
        fieldTraitConfigDao.setTraitConfiguration(studyId, currentOrder)

        // Update visibility (default to visible for new additions)
        val currentVisible = TraitScopePreferences.getStudyVisibleTraitIds(prefs, studyId)?.toMutableSet()
            ?: allTraitsInGlobalOrder.filter { it.visible }.map { it.id }.toSet().toMutableSet()
        currentVisible.addAll(traitIds)
        TraitScopePreferences.setStudyVisibleTraitIds(prefs, studyId, currentVisible)
    }

    suspend fun removeTraitsFromStudy(studyId: Int, traitIds: Set<String>) = withContext(ioDispatcher) {
        if (studyId < 0 || traitIds.isEmpty()) return@withContext

        val allTraitIds = database.getAllTraitObjects().map { it.id }.toSet()
        val currentScoped = TraitScopePreferences.getOrInitializeStudyTraitIds(
            preferences = prefs,
            studyId = studyId,
            fallbackTraitIds = allTraitIds,
        )

        val updatedScoped = currentScoped - traitIds
        TraitScopePreferences.setStudyTraitIds(prefs, studyId, updatedScoped)

        ensureStudyVisibilityInitialized(studyId)
        
        // Update ordering
        val updatedOrder = fieldTraitConfigDao.getTraitIdsInOrder(studyId).filterNot { it in traitIds }
        fieldTraitConfigDao.setTraitConfiguration(studyId, updatedOrder)

        // Update visibility
        TraitScopePreferences.getStudyVisibleTraitIds(prefs, studyId)?.let { currentVisible ->
            val updatedVisible = currentVisible - traitIds
            TraitScopePreferences.setStudyVisibleTraitIds(prefs, studyId, updatedVisible)
        }
    }

    suspend fun syncStudyWithMainList(studyId: Int) = withContext(ioDispatcher) {
        if (studyId < 0) return@withContext

        val allTraitsInGlobalOrder = database.getAllTraitObjects()
        val allIds = allTraitsInGlobalOrder.map { it.id }
        val globalVisible = allTraitsInGlobalOrder.filter { it.visible }.map { it.id }.toSet()

        TraitScopePreferences.setStudyTraitIds(prefs, studyId, allIds.toSet())
        TraitScopePreferences.setStudyVisibleTraitIds(prefs, studyId, globalVisible)

        fieldTraitConfigDao.setTraitConfiguration(
            studyId = studyId,
            traitIdsInOrder = allIds,
        )
    }

    /**
     * Ensures that the field-specific trait configuration is initialized.
     * Creates config based on the current global visibility state if not already done.
     * This is a no-op if the field already has a custom configuration.
     */
    private fun ensureStudyVisibilityInitialized(studyId: Int) {
        if (studyId < 0) return
        if (fieldTraitConfigDao.hasFieldTraitConfig(studyId)) return

        val allTraitsInGlobalOrder = database.getAllTraitObjects()
        val fallbackIds = allTraitsInGlobalOrder.map { it.id }.toSet()
        val scopedIds = TraitScopePreferences.getOrInitializeStudyTraitIds(
            preferences = prefs,
            studyId = studyId,
            fallbackTraitIds = fallbackIds,
        )
        
        // Initialize order with ALL scoped traits
        val scopedOrder = allTraitsInGlobalOrder
            .filter { it.id in scopedIds }
            .map { it.id }

        Log.d(TAG, "ensureStudyVisibilityInitialized: initializing studyId=$studyId with ${scopedOrder.size} scoped traits in order")
        fieldTraitConfigDao.setTraitConfiguration(
            studyId = studyId,
            traitIdsInOrder = scopedOrder,
        )

        // Initialize visibility if not already present
        if (TraitScopePreferences.getStudyVisibleTraitIds(prefs, studyId) == null) {
            val scopedVisibleIds = allTraitsInGlobalOrder
                .filter { it.visible && it.id in scopedIds }
                .map { it.id }
                .toSet()
            TraitScopePreferences.setStudyVisibleTraitIds(prefs, studyId, scopedVisibleIds)
        }
    }

    suspend fun updateStudyTraitVisibility(studyId: Int, traitId: String, isVisible: Boolean) =
        withContext(ioDispatcher) {
            // Initialize field-specific visibility on first edit (lazy initialization)
            ensureStudyVisibilityInitialized(studyId)
            
            val currentVisible = TraitScopePreferences.getStudyVisibleTraitIds(prefs, studyId)?.toMutableSet()
                ?: mutableSetOf()

            if (isVisible) {
                currentVisible.add(traitId)
            } else {
                currentVisible.remove(traitId)
            }
            
            TraitScopePreferences.setStudyVisibleTraitIds(prefs, studyId, currentVisible)
        }

    suspend fun updateAllStudyTraitVisibility(studyId: Int, traitIds: Set<String>, isVisible: Boolean) =
        withContext(ioDispatcher) {
            // Initialize field-specific visibility on first edit (lazy initialization)
            ensureStudyVisibilityInitialized(studyId)

            val currentVisible = TraitScopePreferences.getStudyVisibleTraitIds(prefs, studyId)?.toMutableSet()
                ?: mutableSetOf()

            if (isVisible) {
                currentVisible.addAll(traitIds)
            } else {
                currentVisible.removeAll(traitIds)
            }

            TraitScopePreferences.setStudyVisibleTraitIds(prefs, studyId, currentVisible)
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
                remove(GeneralKeys.getCropCoordinatesKey(trait.id))
            }
        }
    }

    suspend fun deleteTrait(traitId: String) = withContext(ioDispatcher) {
        database.deleteTrait(traitId)

        // clear crop coordinates
        prefs.edit {
            remove(GeneralKeys.getCropCoordinatesKey(traitId))
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

    /**
     * Update field-specific trait order. This creates a field-specific trait list
     * in the field_trait_config table to remember the custom ordering for this field.
     */
    suspend fun updateStudyTraitOrder(studyId: Int, orderedTraitIds: List<String>) =
        withContext(ioDispatcher) {
            ensureStudyVisibilityInitialized(studyId)
            fieldTraitConfigDao.setTraitConfiguration(
                studyId = studyId,
                traitIdsInOrder = orderedTraitIds,
            )
        }

    /**
     * Check if a field has a custom trait list (field-specific ordering)
     */
    suspend fun hasFieldSpecificTraitList(studyId: Int): Boolean =
        withContext(ioDispatcher) {
            fieldTraitConfigDao.getTraitIdsInOrder(studyId).isNotEmpty()
        }

    // returns -1 if insertion failed, else returns rowId if successful
    suspend fun insertTrait(trait: TraitObject): Long = withContext(ioDispatcher) {
        database.insertTraits(trait)
    }

    // returns count of traits that were actually inserted
    suspend fun insertTraitsList(traits: List<TraitObject>): Int = withContext(ioDispatcher) {
        traits.count { insertTrait(it) != -1L }
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
                    TraitObject.fromJson(json, maxPosition, originalFileName)
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
                                visible =
                                    row.getOrNull(7)?.equals("true", ignoreCase = true) != false
                                realPosition = maxPosition + (row.getOrNull(8)?.toIntOrNull() ?: 0)
                                traitDataSource = originalFileName

                                if (fmtLower == "multicat") {
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