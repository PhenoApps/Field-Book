package com.fieldbook.tracker.activities.brapi.io.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.model.FieldBookImage
import com.fieldbook.tracker.brapi.model.Observation
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrapiDeviceIdProvider
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.traits.CategoricalTraitLayout
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.coders.DateJsonCoder
import com.fieldbook.tracker.utilities.CategoryJsonUtil
import com.fieldbook.tracker.utilities.DateJsonUtil
import com.fieldbook.tracker.utilities.JsonUtil
import com.fieldbook.tracker.utilities.export.ValueProcessorFormatAdapter
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The BrAPI observation sync work, separated from any UI so that both the sync screen and the
 * automatic syncs (field open, interval, background) run the same code.
 *
 * [BrapiSyncViewModel] drives the interactive flow and turns these results into UI state, while
 * [autoSync] runs a whole download and upload without asking the user anything.
 */
@Singleton
class BrapiSyncEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataHelper: DataHelper,
    private val preferences: SharedPreferences,
    private val traitRepo: TraitRepository,
    private val valueProcessorFormatAdapter: ValueProcessorFormatAdapter
) {

    companion object {
        private const val TAG = "BrapiSyncEngine"
        private const val UNAUTHORIZED = 401

        // values of PreferenceKeys.BRAPI_SYNC_UPLOAD_MODE
        const val UPLOAD_MODE_ALL = "all"
        const val UPLOAD_MODE_NEW = "new"
    }

    // Manual and automatic syncs of the same study must not interleave, or both could upload the
    // same new observations before either has written back the returned BrAPI ids.
    private val studyLocks = ConcurrentHashMap<Int, Mutex>()

    fun lockFor(studyId: Int): Mutex = studyLocks.getOrPut(studyId) { Mutex() }

    fun newService(): BrAPIService = BrAPIServiceFactory.getBrAPIService(context)

    // A new formatter and time on every call, the engine is shared between concurrent syncs.
    fun syncTimestamp(): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZZZZZ", Locale.getDefault()).format(Date())

    data class Conflict(val serverObservation: Observation, val localObservation: Observation)

    data class PendingResyncChoice(
        val key: String,
        val localObservation: Observation,
        val serverObservation: Observation,
        val preservedLocalValue: String
    )

    data class PreUploadResyncResult(
        val observationsToUpload: List<Observation>,
        val pendingChoices: List<PendingResyncChoice>
    )

    data class ReviewCheck(
        val conflicts: List<Conflict>,
        val resyncChoices: List<PendingResyncChoice>
    )

    data class DownloadResolution(
        val inserts: List<Observation>,
        val updates: List<Observation>,
        val conflicts: List<Conflict>
    )

    /**
     * The study's observations split by sync state, as the sync screen shows them.
     */
    data class LocalStatus(
        val newObservations: List<Observation> = emptyList(),
        val syncedObservations: List<Observation> = emptyList(),
        val syncedImageObservations: List<Observation> = emptyList(),
        val editedObservations: List<Observation> = emptyList(),
        val newImageObservations: List<Observation> = emptyList(),
        val editedImageObservations: List<Observation> = emptyList(),
        val incompleteImageObservations: List<Observation> = emptyList(),
        val unsyncableObservationCount: Int = 0
    ) {
        val unsyncedCount: Int
            get() = newObservations.size + editedObservations.size + newImageObservations.size +
                    editedImageObservations.size + incompleteImageObservations.size
    }

    sealed class ImageUploadEvent {
        data class Message(val text: String) : ImageUploadEvent()
        object Inserted : ImageUploadEvent()
        object Edited : ImageUploadEvent()
        object Failed : ImageUploadEvent()
        data class Finished(val message: String) : ImageUploadEvent()
    }

    enum class AutoSyncError {
        UNAUTHORIZED,
        MISSING_STUDY,
        FAILED
    }

    data class AutoSyncResult(
        val downloaded: Int = 0,
        val uploaded: Int = 0,
        val needsReview: Int = 0,
        val error: AutoSyncError? = null,
        val skipped: Boolean = false
    )

    enum class UploadError {
        NONE,
        MISSING_OBSERVATION_IN_RESPONSE,
        MULTIPLE_OBSERVATIONS_PER_VARIABLE,
        WRONG_NUM_OBSERVATIONS_RETURNED,
        API_CALLBACK_ERROR,
        API_UNAUTHORIZED_ERROR,
        API_NOT_SUPPORTED_ERROR,
        API_PERMISSION_ERROR
    }

    fun loadLocalStatus(studyId: Int): LocalStatus {

        val hostURL = BrAPIService.getHostUrl(context)
        val exportData = dataHelper.getBrAPIExportData(studyId, hostURL)

        // Anything collected on a trait that did not come from this server - created in the app,
        // imported from a trait file, or pulled from a different brapi server - can never be
        // uploaded. Roll those observations up into a single count for the summary line.
        val unsyncableObservationCount = listOf(
            "userCreatedTraitObservations",
            "userCreatedImageObservations",
            "wrongSourceObservations",
            "wrongSourceImageObservations"
        ).sumOf { (exportData[it] ?: emptyList()).size }

        return LocalStatus(
            newObservations = exportData["newObservations"] ?: emptyList(),
            syncedObservations = exportData["syncedObservations"] ?: emptyList(),
            syncedImageObservations = exportData["syncedImageObservations"] ?: emptyList(),
            editedObservations = exportData["editedObservations"] ?: emptyList(),
            newImageObservations = exportData["newImageObservations"] ?: emptyList(),
            editedImageObservations = exportData["editedImageObservations"] ?: emptyList(),
            incompleteImageObservations = exportData["incompleteImageObservations"] ?: emptyList(),
            unsyncableObservationCount = unsyncableObservationCount
        )
    }

    /**
     * Observations, including images, that would be uploaded by a sync of this study. Uses the
     * same categories as the sync screen so the two counts always agree.
     */
    fun countUnsynced(studyId: Int): Int = loadLocalStatus(studyId).unsyncedCount

    /**
     * Records that the study exchanged data with the server, this is what the field detail
     * screen reads for its last sync date.
     */
    fun markStudySynced(studyId: Int) {
        dataHelper.updateSyncDate(studyId)
    }

    /**
     * Reduces a stored or BrAPI observation value to a plain, comparable string so that
     * local (Field Book JSON) and server (BrAPI token) representations of the same value
     * are treated as equal.
     *
     * - Categorical: both local JSON and BrAPI tokens are reduced to "val1:val2" format.
     * - Date: local DateJson is reduced to its formattedDate (ISO "YYYY-MM-DD"); BrAPI ISO strings pass through unchanged.
     * - Other types: returned unchanged.
     */
    private fun normalizeValueForComparison(value: String, trait: TraitObject?): String {
        if (value.isBlank() || value == "NA") return value
        if (trait == null) return value
        return when {
            trait.format in CategoricalTraitLayout.POSSIBLE_VALUES ->
                CategoryJsonUtil.encodeBrapiCategoricalValue(value, trait.categories)

            trait.format == Formats.DATE.getDatabaseName() -> {
                val decoded = DateJsonUtil.decode(value)
                if (decoded is DateJsonCoder.DateJson) decoded.formattedDate else value
            }

            else -> value
        }
    }

    /**
     * Compares downloaded server observations with the local ones.
     *
     * A value that differs is only a conflict when both sides changed since the last sync. An edit
     * on this device naturally differs from the server until it is uploaded, so that alone is not
     * a conflict. The server copy counts as changed when its observation timestamp is later than
     * the local last synced time, an edit made elsewhere carries its own edit time.
     *
     * - Edited here, unchanged on the server: nothing to download, the upload sends the edit.
     * - Unchanged here, changed on the server: an update, the server value replaces the local one.
     * - Changed on both: a conflict for the user to resolve.
     * - Photos are skipped, their stored values are local file references that never match.
     *
     * @return inserts: server observations not on this device
     *         updates: server values to write over unedited local observations
     *         conflicts: observations changed on both sides since the last sync
     */
    fun resolveObservationStatus(
        brapiObservations: List<Observation>,
        localStatus: LocalStatus,
        traitsById: Map<String, TraitObject>
    ): DownloadResolution {

        val insertObservations = mutableListOf<Observation>()
        val updateObservations = mutableListOf<Observation>()
        val conflicts = mutableListOf<Conflict>()

        val editedByDbId = localStatus.editedObservations
            .filter { it.dbId != null }
            .associateBy { it.dbId }
        val syncedByDbId = localStatus.syncedObservations
            .filter { it.dbId != null }
            .associateBy { it.dbId }

        // Images already on this device in any state, only ever matched so they are not inserted
        // a second time.
        val imageDbIds = (localStatus.syncedImageObservations + localStatus.editedImageObservations +
                localStatus.incompleteImageObservations).mapNotNull { it.dbId }.toSet()

        brapiObservations.forEach { brapiObs ->

            val localEdited = editedByDbId[brapiObs.dbId]
            val localSynced = syncedByDbId[brapiObs.dbId]

            when {
                localEdited != null -> {
                    if (valuesDiffer(localEdited, brapiObs, traitsById)
                        && serverChangedSinceSync(brapiObs, localEdited)) {
                        conflicts.add(Conflict(serverObservation = brapiObs, localObservation = localEdited))
                    }
                }

                localSynced != null -> {
                    if (valuesDiffer(localSynced, brapiObs, traitsById)) {
                        updateObservations.add(brapiObs)
                    }
                }

                brapiObs.dbId in imageDbIds -> Unit

                //purely new download
                else -> insertObservations.add(brapiObs)
            }
        }

        return DownloadResolution(insertObservations, updateObservations, conflicts)
    }

    private fun valuesDiffer(
        local: Observation,
        server: Observation,
        traitsById: Map<String, TraitObject>
    ): Boolean {
        val trait = traitsById[local.internalVariableDbId]
        return normalizeValueForComparison(local.value ?: "", trait) !=
                normalizeValueForComparison(server.value ?: "", trait)
    }

    /**
     * Whether the server copy changed after this device last synced the observation. Without a
     * timestamp on either side there is no way to tell, so it is treated as changed and the user
     * decides, rather than risk overwriting someone else's edit.
     */
    private fun serverChangedSinceSync(server: Observation, local: Observation): Boolean {
        val serverTime = server.timestamp ?: return true
        val syncedTime = local.lastSyncedTime ?: return true
        return serverTime > syncedTime
    }

    /**
     * Writes a resolved download to the local database: inserts the new server observations and
     * applies the updates.
     */
    fun saveDownloadedObservations(
        study: FieldObject,
        resolution: DownloadResolution,
        traitsById: Map<String, TraitObject>
    ) {

        Log.d(TAG, "Saving ${resolution.inserts.size} new observations")

        resolution.inserts.forEach { obs ->

            Log.d(TAG, "Saving observation: ${obs.dbId}")

            obs.internalVariableDbId = obs.variableDbId

            // Store categorical BrAPI values using Field Book's internal JSON format.
            obs.value = normalizeDownloadedObservationValue(obs, traitsById)

            val rep = dataHelper.getNextRep(
                study.studyId.toString(),
                obs.unitDbId,
                obs.internalVariableDbId
            ).toInt()

            dataHelper.insertObservation(
                obs.unitDbId,
                obs.internalVariableDbId,
                obs.value ?: "",
                obs.collector ?: "",
                "",
                "",
                study.studyId.toString(),
                obs.dbId,
                obs.timestamp,
                obs.lastSyncedTime,
                rep.toString()
            )
        }

        Log.d(TAG, "Updating local database with ${resolution.updates.size} updates")

        applyServerValues(resolution.updates, traitsById)
    }

    /**
     * Overwrites local observations with the server's copy, re-encoding raw BrAPI server values
     * to Field Book's internal format before persisting.
     */
    fun applyServerValues(
        serverObservations: List<Observation>,
        traitsById: Map<String, TraitObject>
    ) {
        serverObservations.forEach { obs ->
            if (obs.internalVariableDbId.isNullOrBlank()) {
                obs.internalVariableDbId = obs.variableDbId
            }
            obs.value = normalizeDownloadedObservationValue(obs, traitsById)
        }

        dataHelper.updateObservationsByBrapiId(serverObservations)
    }

    fun getObservations(
        brAPIService: BrAPIService,
        study: FieldObject
    ): Flow<DownloadProgressUpdate> = channelFlow {

        val pageCount = AtomicInteger(1)

        val brapiStudyId =
            study.studyDbId
                ?: throw IllegalStateException(context.getString(R.string.brapi_study_db_id_is_missing))

        val variables = traitRepo.getTraits().filter {
            it.traitDataSource.isNotEmpty() && it.traitDataSource == study.dataSource
        }

        val variableDbIds = variables.mapNotNull { it.externalDbId }

        val pageSize = preferences.getString(PreferenceKeys.BRAPI_PAGE_SIZE, "50")?.toInt() ?: 50
        val paginationManager = BrapiPaginationManager(0, pageSize)

        Log.d(
            TAG,
            "Starting to pull observations from brapi server with pagesize: $pageSize and ${variableDbIds.size} variables for $brapiStudyId"
        )

        //gets the first page of data and updates the pagination manager with total page size
        val firstPageResult = brAPIService.awaitGetSingleObservationPage(
            brapiStudyId,
            variableDbIds,
            paginationManager
        )

        Log.d(
            TAG,
            "First page returned with ${firstPageResult.size} observations, total pages: ${paginationManager.totalPages}"
        )

        val totalPages = paginationManager.totalPages ?: 0

        //if there's only one page, we're done.
        if (totalPages <= 1) {

            send(DownloadProgressUpdate.Completed(firstPageResult))

        } else {

            trySend(
                DownloadProgressUpdate.InDownloadProgress(
                    pageCount.get(),
                    totalPages
                )
            )

            val concurrencyLimit = (preferences.getString(
                PreferenceKeys.BRAPI_MAX_CONCURRENT_OBSERVATION_TRANSFER, "5"
            ))?.toInt() ?: 5

            val allObservations = brAPIService.awaitGetObservations(
                brapiStudyId = brapiStudyId,
                variableDbIds = variableDbIds,
                paginationManager = paginationManager,
                initialPages = firstPageResult,
                concurrencyLimit = concurrencyLimit
            ) { _, observations ->

                trySend(
                    DownloadProgressUpdate.InDownloadProgress(
                        pageCount.incrementAndGet(),
                        totalPages
                    )
                )

                Log.d(TAG, "Downloaded: ${observations.size} observations")
            }

            Log.d(TAG, "All observations returned with ${allObservations.size} observations")

            send(DownloadProgressUpdate.Completed(allObservations))
        }

    }.flowOn(Dispatchers.IO)

    fun normalizeDownloadedObservationValue(
        observation: Observation,
        traitsById: Map<String, TraitObject>
    ): String {
        val rawValue = observation.value ?: return ""

        if (rawValue.isBlank() || rawValue == "NA") return rawValue

        val traitId = observation.internalVariableDbId
            ?.takeIf { it.isNotBlank() }
            ?: observation.variableDbId?.takeIf { it.isNotBlank() }
            ?: return rawValue
        val trait = traitsById[traitId] ?: return rawValue

        return when {
            trait.format in CategoricalTraitLayout.POSSIBLE_VALUES ->
                CategoryJsonUtil.encodeDownloadedBrapiCategoricalValue(rawValue, trait.categories)

            trait.format == Formats.DATE.getDatabaseName() -> {
                // If the value is already internal DateJson, keep it as-is.
                if (JsonUtil.isJsonValid(rawValue)) return rawValue
                // Otherwise it's a plain BrAPI ISO-8601 date "YYYY-MM-DD". Convert to DateJson.
                try {
                    val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
                    val parsed = sdf.parse(rawValue) ?: return rawValue
                    val cal = Calendar.getInstance()
                    cal.time = parsed
                    DateJsonUtil.encode(
                        DateJsonCoder.DateJson(
                            formattedDate = rawValue,
                            dayOfYear = cal.get(Calendar.DAY_OF_YEAR).toString()
                        )
                    )
                } catch (_: Exception) {
                    rawValue
                }
            }

            else -> rawValue
        }
    }

    /**
     * Converts observation values to their BrAPI representation in place, ready for upload.
     *
     * @return the original Field Book values keyed by fieldBookDbId, so the local database keeps
     * its internal representation when upload results are written back.
     */
    suspend fun prepareForUpload(observations: List<Observation>): Map<String, String> {

        val localValuesByFieldBookId = observations.mapNotNull { obs ->
            obs.fieldBookDbId?.let { id -> id to (obs.value ?: "") }
        }.toMap()

        val traits = traitRepo.getTraits().associateBy { it.id }
        observations.forEach { obs ->
            traits[obs.internalVariableDbId]?.let { trait ->
                obs.value =
                    valueProcessorFormatAdapter.processValue(obs.value, trait, forBrapi = true)
            }
        }

        return localValuesByFieldBookId
    }

    /**
     * Re-sync check for issue #550. Before upload, verify if observations were already stored
     * by a prior request (for example, app crash after server write but before local sync update).
     *
     * @param adoptServerKeys choice keys the user chose to link to the existing server observation
     * @param uploadAsNewKeys choice keys the user chose to upload again as new observations
     * @return the observations still to upload, and the matches that need a user choice
     */
    suspend fun performResyncCheckBeforeUpload(
        brAPIService: BrAPIService,
        studyDbId: String?,
        observations: List<Observation>,
        localValuesByFieldBookId: Map<String, String>,
        adoptServerKeys: MutableSet<String> = mutableSetOf(),
        uploadAsNewKeys: MutableSet<String> = mutableSetOf()
    ): PreUploadResyncResult {

        if (observations.isEmpty()) return PreUploadResyncResult(observations, emptyList())

        if (studyDbId.isNullOrEmpty()) return PreUploadResyncResult(observations, emptyList())

        val variableDbIds = observations.mapNotNull { it.variableDbId }.distinct()
        if (variableDbIds.isEmpty()) return PreUploadResyncResult(observations, emptyList())

        return try {
            val pageSize =
                preferences.getString(PreferenceKeys.BRAPI_PAGE_SIZE, "50")?.toInt() ?: 50
            val paginationManager = BrapiPaginationManager(0, pageSize)

            val firstPage = brAPIService.awaitGetSingleObservationPage(
                studyDbId,
                variableDbIds,
                paginationManager
            )

            val allCandidateObservations = if ((paginationManager.totalPages ?: 1) <= 1) {
                firstPage
            } else {
                brAPIService.awaitGetObservations(
                    brapiStudyId = studyDbId,
                    variableDbIds = variableDbIds,
                    paginationManager = paginationManager,
                    initialPages = firstPage,
                    concurrencyLimit = preferences.getString(
                        PreferenceKeys.BRAPI_MAX_CONCURRENT_OBSERVATION_TRANSFER,
                        "5"
                    )?.toInt() ?: 5
                ) { _, _ ->
                    // Re-sync check runs silently without changing upload progress UI.
                }
            }

            matchResyncCandidates(
                observations,
                localValuesByFieldBookId,
                allCandidateObservations,
                adoptServerKeys,
                uploadAsNewKeys
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Pre-upload re-sync check failed; continuing with normal upload", e)
            PreUploadResyncResult(observations, emptyList())
        }
    }

    /**
     * Matches local observations against server observations already fetched, see
     * [performResyncCheckBeforeUpload]. Matches that already carry a BrAPI id are linked here,
     * matches without one are returned for the user to choose. For observations without a BrAPI
     * id, new ones, nothing is written.
     */
    fun matchResyncCandidates(
        observations: List<Observation>,
        localValuesByFieldBookId: Map<String, String>,
        allCandidateObservations: List<Observation>,
        adoptServerKeys: MutableSet<String>,
        uploadAsNewKeys: MutableSet<String>
    ): PreUploadResyncResult {

        val pendingUpload = mutableListOf<Observation>()
        val pendingUserChoices = mutableListOf<PendingResyncChoice>()
        val remainingCandidates = allCandidateObservations.toMutableList()
        var recoveredCount = 0
        val localDeviceId = BrapiDeviceIdProvider.getOrCreate(context)

        observations.forEach { localObservation ->
            val matchIndex = remainingCandidates.indexOfFirst { serverObservation ->
                matchesResyncCheck(localObservation, serverObservation, localDeviceId)
            }

            if (matchIndex >= 0) {
                val matchedServerObservation = remainingCandidates.removeAt(matchIndex)
                val choiceKey = buildResyncChoiceKey(localObservation, matchedServerObservation)

                if (localObservation.dbId.isNullOrEmpty() && !adoptServerKeys.contains(
                        choiceKey
                    ) && !uploadAsNewKeys.contains(choiceKey)
                ) {
                    val preservedValue = localObservation.fieldBookDbId
                        ?.let { localValuesByFieldBookId[it] }
                        ?: localObservation.value.orEmpty()
                    pendingUserChoices.add(
                        PendingResyncChoice(
                            key = choiceKey,
                            localObservation = localObservation,
                            serverObservation = matchedServerObservation,
                            preservedLocalValue = preservedValue
                        )
                    )
                    return@forEach
                }

                if (localObservation.dbId.isNullOrEmpty() && uploadAsNewKeys.contains(
                        choiceKey
                    )
                ) {
                    pendingUpload.add(localObservation)
                    return@forEach
                }

                localObservation.fieldBookDbId?.let { fieldBookId ->
                    localValuesByFieldBookId[fieldBookId]?.let { preservedValue ->
                        localObservation.value = preservedValue
                    }
                }

                localObservation.dbId = matchedServerObservation.dbId
                val serverSyncTime = matchedServerObservation.timestamp
                if (serverSyncTime != null) {
                    localObservation.lastSyncedTime = serverSyncTime
                } else {
                    localObservation.setLastSyncedTime(syncTimestamp())
                }
                dataHelper.updateObservationsByFieldBookId(listOf(localObservation))
                adoptServerKeys.remove(choiceKey)
                uploadAsNewKeys.remove(choiceKey)
                recoveredCount++
            } else {
                pendingUpload.add(localObservation)
            }
        }

        if (recoveredCount > 0) {
            Log.i(
                TAG,
                "Pre-upload re-sync check recovered $recoveredCount/${observations.size} observations"
            )
        }

        return PreUploadResyncResult(pendingUpload, pendingUserChoices)
    }

    /**
     * Everything on the server for the study's BrAPI variables, see [getObservations].
     */
    suspend fun downloadServerObservations(
        brAPIService: BrAPIService,
        study: FieldObject
    ): List<Observation> {

        var downloaded: List<Observation> = emptyList()

        getObservations(brAPIService, study).collect { update ->
            if (update is DownloadProgressUpdate.Completed) downloaded = update.data
        }

        return downloaded
    }

    /**
     * What a sync of the study would need the user to decide, found without changing anything.
     *
     * @return conflicts: observations changed on this device and on the server since the last sync
     *         resyncChoices: new observations whose values already appear on the server
     */
    suspend fun checkForReview(brAPIService: BrAPIService, study: FieldObject): ReviewCheck {

        val server = downloadServerObservations(brAPIService, study)
        val local = loadLocalStatus(study.studyId)
        val traitsById = traitRepo.getTraits().associateBy { it.id }

        val conflicts = resolveObservationStatus(server, local, traitsById).conflicts

        // New observations have no BrAPI id, so matching them only reports, it never links.
        val localValues = prepareForUpload(local.newObservations)
        val resync = matchResyncCandidates(
            local.newObservations,
            localValues,
            server,
            mutableSetOf(),
            mutableSetOf()
        )

        return ReviewCheck(conflicts, resync.pendingChoices)
    }

    private fun matchesResyncCheck(
        localObservation: Observation,
        serverObservation: Observation,
        localDeviceId: String
    ): Boolean {
        val localValue = localObservation.value?.trim().orEmpty()
        val serverValue = serverObservation.value?.trim().orEmpty()

        if (localValue != serverValue) return false

        val localDbId = localObservation.dbId
        if (!localDbId.isNullOrEmpty()) {
            return localDbId == serverObservation.dbId
        }

        val localFieldBookDbId = localObservation.fieldBookDbId
        val serverFieldBookDbId = serverObservation.fieldBookDbId
        val serverDeviceId = serverObservation.originDeviceId

        return !localFieldBookDbId.isNullOrEmpty()
                && localFieldBookDbId == serverFieldBookDbId
                && !serverDeviceId.isNullOrEmpty()
                && serverDeviceId == localDeviceId
    }

    private fun buildResyncChoiceKey(
        localObservation: Observation,
        serverObservation: Observation
    ): String {
        val localKey = localObservation.fieldBookDbId ?: ""
        val serverKey = serverObservation.dbId ?: ""
        return "$localKey|$serverKey|${localObservation.variableDbId ?: ""}|${localObservation.unitDbId ?: ""}"
    }

    /**
     * New Observations are saved in chunks, when returning from BrAPI they are updated in the local FB
     * database to save the observationDbId and synced time.
     */
    fun processNewObservations(
        brAPIService: BrAPIService,
        newObs: List<Observation>,
        localValuesByFieldBookId: Map<String, String>
    ): Flow<UploadProgressUpdate> =
        channelFlow {

            val incompatible = AtomicInteger(0)
            val new = AtomicInteger(0)
            val edited = AtomicInteger(0)

            brAPIService.awaitCreateObservations(
                context,
                newObs,
                { chunk ->

                    val (incompats, news, edits) = processChunk(
                        chunk,
                        newObs,
                        localValuesByFieldBookId = localValuesByFieldBookId
                    )

                    incompatible.addAndGet(incompats)
                    new.addAndGet(news)
                    edited.addAndGet(edits)

                    trySend(UploadProgressUpdate.InUploadProgress(chunk.size, newObs.size))

                }) { errorCode, failedChunk ->

                //errors coming from brapi api
                trySend(UploadProgressUpdate.InUploadFailedChunk(errorCode, failedChunk))
            }

            send(UploadProgressUpdate.Completed(incompatible.get(), new.get(), edited.get()))

        }.flowOn(Dispatchers.IO)

    private fun processChunk(
        chunk: List<Observation>,
        referenceObservations: List<Observation>,
        isNew: Boolean = true,
        localValuesByFieldBookId: Map<String, String> = emptyMap()
    ): Triple<Int, Int, Int> {

        var incompatible = 0
        var new = 0
        var edited = 0

        //chunk returned from brapi, each observation is updated individually
        chunk.forEach { observation ->

            var retVal = UploadError.NONE

            val syncTime = syncTimestamp()

            // find observation with matching keys and update observationDbId
            val firstIndex =
                referenceObservations.indexOfFirst { it.fieldBookDbId == observation.fieldBookDbId }
            val lastIndex =
                referenceObservations.indexOfLast { it.fieldBookDbId == observation.fieldBookDbId }

            if (firstIndex == -1) {

                retVal = UploadError.MISSING_OBSERVATION_IN_RESPONSE

            } else if (firstIndex != lastIndex) {

                retVal = UploadError.MULTIPLE_OBSERVATIONS_PER_VARIABLE

            } else if (firstIndex in referenceObservations.indices) {
                val update = referenceObservations[firstIndex]
                update.fieldBookDbId?.let { fieldBookId ->
                    localValuesByFieldBookId[fieldBookId]?.let { preservedValue ->
                        // Keep local DB observation value in Field Book's internal representation.
                        update.value = preservedValue
                    }
                }
                update.dbId = observation.dbId
                update.setLastSyncedTime(syncTime)
                dataHelper.updateObservationsByFieldBookId(listOf(update))

            }

            //process any potential upload errors here and display
            if (retVal != UploadError.NONE) {

                Log.e(TAG, "Error processing observation: $retVal")

                incompatible++

            } else if (isNew) {

                new++

            } else {

                edited++

            }
        }

        return Triple(incompatible, new, edited)
    }

    /**
     * When observation timestamp is greater that synced time, they must be pushed and updated
     * on the brapi endpoint.
     */
    fun processEditedObservations(
        brAPIService: BrAPIService,
        editedObs: List<Observation>,
        localValuesByFieldBookId: Map<String, String>
    ): Flow<UploadProgressUpdate> =
        channelFlow {

            val incompatible = AtomicInteger(0)
            val new = AtomicInteger(0)
            val edited = AtomicInteger(0)

            brAPIService.awaitUpdateObservations(
                context,
                editedObs, { chunk ->

                    val (incompats, news, edits) = processChunk(
                        chunk,
                        editedObs,
                        isNew = false,
                        localValuesByFieldBookId = localValuesByFieldBookId
                    )

                    incompatible.addAndGet(incompats)
                    new.addAndGet(news)
                    edited.addAndGet(edits)

                    trySend(UploadProgressUpdate.InUploadProgress(chunk.size, editedObs.size))

                }) { errorCode, failedChunk ->

                trySend(UploadProgressUpdate.InUploadFailedChunk(errorCode, failedChunk))
            }

            send(UploadProgressUpdate.Completed(incompatible.get(), new.get(), edited.get()))

        }.flowOn(Dispatchers.IO)

    suspend fun processImages(
        brAPIService: BrAPIService,
        images: List<FieldBookImage>,
        isNew: Boolean = true,
        onEvent: (ImageUploadEvent) -> Unit = {}
    ) = coroutineScope {

        if (images.isEmpty()) {
            Log.d(TAG, "No images to process.")
            return@coroutineScope
        }

        val concurrencyLimit = (preferences.getString(
            PreferenceKeys.BRAPI_MAX_CONCURRENT_IMAGE_CONTENT, "3"
        ) ?: "3").toInt()

        Log.d(TAG, "Processing ${images.size} images with a concurrency of $concurrencyLimit.")

        val semaphore = Semaphore(concurrencyLimit)

        images.map { image ->
            async(Dispatchers.IO) {
                semaphore.withPermit {

                    if (!isActive) return@withPermit
                    val message = if (isNew) {
                        processNewImage(brAPIService, image, onEvent)
                    } else {
                        updateImages(brAPIService, image, onEvent)
                    }

                    onEvent(ImageUploadEvent.Finished(message))
                }
            }
        }.awaitAll()

        Log.d(TAG, "All image processing jobs have completed.")
    }

    /**
     * For each image:
     * 1. upload metadata
     * 2. load image into memory
     * 3. upload image content
     * 4. clear memory
     */
    private suspend fun processNewImage(
        brAPIService: BrAPIService,
        image: FieldBookImage,
        onEvent: (ImageUploadEvent) -> Unit
    ): String {

        var completedMessage = context.getString(R.string.completed_brapi_upload, image.fileName)

        try {

            onEvent(
                ImageUploadEvent.Message(
                    context.getString(R.string.uploading_metadata, image.fileName)
                )
            )

            val imageWithDbId = brAPIService.awaitPostImageMetaData(image)

            onEvent(
                ImageUploadEvent.Message(
                    context.getString(R.string.loading_brapi_images, image.fileName)
                )
            )

            imageWithDbId.loadImage(context)

            onEvent(
                ImageUploadEvent.Message(
                    context.getString(R.string.uploading_content, image.fileName)
                )
            )

            val finalImage = brAPIService.awaitPutImageContent(imageWithDbId)

            val retVal = processImageResponse(finalImage, finalImage.fieldBookDbId)

            if (retVal != UploadError.NONE) {

                Log.e(TAG, "Error processing image: ${image.fileName}")

                throw Exception()
            }

            onEvent(ImageUploadEvent.Inserted)

        } catch (e: CancellationException) {

            throw e

        } catch (e: Exception) {

            Log.e(TAG, "Failed to process image pair for ${image.fileName}", e)

            onEvent(ImageUploadEvent.Failed)

            completedMessage = context.getString(R.string.failed_brapi_image_upload, image.fileName)

        } finally {

            image.setBytes(null)

        }

        return completedMessage
    }

    /**
     * For editing or finishing completed images
     * 1. load image into memory
     * 2. put image (update metadata)
     * 3. put image content
     * 4. clear memory
     */
    private suspend fun updateImages(
        brAPIService: BrAPIService,
        image: FieldBookImage,
        onEvent: (ImageUploadEvent) -> Unit
    ): String {

        var completedMessage = context.getString(R.string.completed_brapi_upload, image.fileName)

        try {

            onEvent(
                ImageUploadEvent.Message(
                    context.getString(R.string.loading_brapi_images, image.fileName)
                )
            )

            image.loadImage(context)

            onEvent(
                ImageUploadEvent.Message(
                    context.getString(R.string.updating_metadata_brapi_image, image.fileName)
                )
            )

            val responseImage = brAPIService.awaitPutImage(image)

            onEvent(
                ImageUploadEvent.Message(
                    context.getString(R.string.updating_content_brapi_image, image.fileName)
                )
            )

            brAPIService.awaitPutImageContent(responseImage)

            onEvent(ImageUploadEvent.Edited)

        } catch (e: CancellationException) {

            throw e

        } catch (e: Exception) {

            Log.e(TAG, "Failed to update image ${image.fileName}", e)

            completedMessage = context.getString(R.string.failed_brapi_image_upload, image.fileName)

            onEvent(ImageUploadEvent.Failed)

        } finally {

            image.setBytes(null)

        }

        return completedMessage
    }

    private fun processImageResponse(
        converted: FieldBookImage,
        fieldBookId: String?,
    ): UploadError {

        var retVal = UploadError.NONE

        converted.setLastSyncedTime(syncTimestamp())
        converted.fieldbookDbId = fieldBookId

        if (converted.dbId != null && converted.fieldBookDbId != null) {

            dataHelper.updateImage(converted)

        } else {

            retVal = UploadError.MISSING_OBSERVATION_IN_RESPONSE

        }

        return retVal
    }

    /**
     * Downloads then uploads a study without asking the user anything. Anything that would need a
     * decision is left untouched for the sync screen:
     *
     * - An observation changed both here and on the server since the last sync is a conflict, see
     *   [resolveObservationStatus]. Neither side is changed and the local edit is held back.
     * - A new observation that the re-sync check finds already on the server needs the user to
     *   choose between linking and re-uploading it, so it is skipped.
     *
     * Everything else follows whichever side changed: server changes are written locally, local
     * edits are uploaded.
     *
     * Returns immediately with [AutoSyncResult.skipped] if this study is already syncing.
     */
    suspend fun autoSync(studyId: Int): AutoSyncResult = withContext(Dispatchers.IO) {

        val lock = lockFor(studyId)
        if (!lock.tryLock()) return@withContext AutoSyncResult(skipped = true)

        try {

            val study = dataHelper.getFieldObject(studyId)
            if (study == null || study.studyDbId.isNullOrEmpty()) {
                return@withContext AutoSyncResult(error = AutoSyncError.MISSING_STUDY)
            }

            val brAPIService = newService()
            val traitsById = traitRepo.getTraits().associateBy { it.id }

            // Download

            val downloaded = downloadServerObservations(brAPIService, study)

            val beforeDownload = loadLocalStatus(studyId)
            val resolution = resolveObservationStatus(downloaded, beforeDownload, traitsById)
            saveDownloadedObservations(study, resolution, traitsById)

            markStudySynced(studyId)

            // Upload

            val heldBack = resolution.conflicts.mapNotNull { it.localObservation.fieldBookDbId }.toSet()
            val local = loadLocalStatus(studyId)

            // Automatic syncs can leave edits to the sync screen, where they are checked first.
            val uploadEdited = preferences.getString(
                PreferenceKeys.BRAPI_SYNC_UPLOAD_MODE,
                UPLOAD_MODE_ALL
            ) != UPLOAD_MODE_NEW

            var uploaded = 0
            var needsReview = resolution.conflicts.size
            var failedCode: Int? = null

            listOf(
                local.newObservations to true,
                (if (uploadEdited) local.editedObservations.filter { it.fieldBookDbId !in heldBack } else emptyList()) to false
            ).forEach { (observations, isNew) ->

                if (observations.isEmpty() || failedCode == UNAUTHORIZED) return@forEach

                val localValues = prepareForUpload(observations)
                val resync = performResyncCheckBeforeUpload(
                    brAPIService,
                    study.studyDbId,
                    observations,
                    localValues
                )
                needsReview += resync.pendingChoices.size

                if (resync.observationsToUpload.isEmpty()) return@forEach

                val flow = if (isNew) {
                    processNewObservations(brAPIService, resync.observationsToUpload, localValues)
                } else {
                    processEditedObservations(brAPIService, resync.observationsToUpload, localValues)
                }

                flow.collect { update ->
                    when (update) {
                        is UploadProgressUpdate.InUploadFailedChunk -> failedCode = update.errorCode
                        is UploadProgressUpdate.Completed -> uploaded += update.new + update.edited
                        else -> Unit
                    }
                }
            }

            // Images upload concurrently, so their events can arrive from several threads at once.
            val uploadedImages = AtomicInteger(0)

            if (failedCode != UNAUTHORIZED) {

                processImages(
                    brAPIService,
                    dataHelper.getImageDetails(context, local.newImageObservations)
                ) { event -> if (event == ImageUploadEvent.Inserted) uploadedImages.incrementAndGet() }

                // An incomplete image is a new one whose content never finished uploading, so it
                // goes with new data even though it is sent the way edits are.
                val imageUpdates = (if (uploadEdited) local.editedImageObservations else emptyList()) +
                        local.incompleteImageObservations

                processImages(
                    brAPIService,
                    dataHelper.getImageDetails(context, imageUpdates),
                    isNew = false
                ) { event -> if (event == ImageUploadEvent.Edited) uploadedImages.incrementAndGet() }
            }

            uploaded += uploadedImages.get()

            if (uploaded > 0) markStudySynced(studyId)

            AutoSyncResult(
                downloaded = resolution.inserts.size + resolution.updates.size,
                uploaded = uploaded,
                needsReview = needsReview,
                error = when (failedCode) {
                    null -> null
                    UNAUTHORIZED -> AutoSyncError.UNAUTHORIZED
                    else -> AutoSyncError.FAILED
                }
            )

        } catch (e: CancellationException) {

            throw e

        } catch (e: BrapiException) {

            Log.e(TAG, "Automatic sync of study $studyId failed", e)

            AutoSyncResult(
                error = if (e.code == UNAUTHORIZED) AutoSyncError.UNAUTHORIZED else AutoSyncError.FAILED
            )

        } catch (e: Exception) {

            Log.e(TAG, "Automatic sync of study $studyId failed", e)

            AutoSyncResult(error = AutoSyncError.FAILED)

        } finally {

            lock.unlock()

        }
    }
}
