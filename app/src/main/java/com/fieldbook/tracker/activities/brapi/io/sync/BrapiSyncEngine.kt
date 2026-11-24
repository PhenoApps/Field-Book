package com.fieldbook.tracker.activities.brapi.io.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.fieldbook.tracker.R
import com.fieldbook.tracker.brapi.model.FieldBookImage
import com.fieldbook.tracker.brapi.model.Observation
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
import com.fieldbook.tracker.brapi.service.BrapiDeviceIdProvider
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.database.ObservationChangeTracker
import com.fieldbook.tracker.database.repository.TraitRepository
import com.fieldbook.tracker.objects.FieldObject
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.traits.CategoricalTraitLayout
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.coders.DateJsonCoder
import com.fieldbook.tracker.utilities.BrapiImplementationHelper
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.brapi.v2.model.core.BrAPIService as BrAPIServerCallInfo
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The BrAPI observation sync work, separated from any UI so that both the sync screen and the
 * automatic sync when a field is opened run the same code.
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
    private val valueProcessorFormatAdapter: ValueProcessorFormatAdapter,
    private val reviewDecisions: BrapiReviewDecisions
) {

    companion object {
        private const val TAG = "BrapiSyncEngine"
        private const val UNAUTHORIZED = 401

        // how long an automatic sync's review items stand in for the sync screen's own check
        private val REVIEW_REUSE_MS = TimeUnit.MINUTES.toMillis(5)

        // values of PreferenceKeys.BRAPI_SYNC_UPLOAD_MODE
        const val UPLOAD_MODE_ALL = "all"
        const val UPLOAD_MODE_NEW = "new"
    }

    // Manual and automatic syncs of the same study must not interleave, or both could upload the
    // same new observations before either has written back the returned BrAPI ids.
    private val studyLocks = ConcurrentHashMap<Int, Mutex>()

    fun lockFor(studyId: Int): Mutex = studyLocks.getOrPut(studyId) { Mutex() }

    fun newService(): BrAPIService = BrAPIServiceFactory.getBrAPIService(context)

    /**
     * Which kinds of upload the server accepts, from the calls it lists in serverinfo.
     * Everything defaults to supported: a server whose serverinfo fails or lists nothing is not
     * blocked, and neither is a call it leaves out, many servers leave it incomplete.
     */
    data class UploadSupport(
        val newObservations: Boolean = true, // POST observations
        val editedObservations: Boolean = true, // PUT observations
        val images: Boolean = true // POST images, PUT images/{imageDbId} and its imagecontent
    )

    // serverinfo rarely changes, so it is asked once per server for the life of the process
    private val uploadSupportByHost = ConcurrentHashMap<String, UploadSupport>()

    /**
     * Review items an automatic sync found, kept so the sync screen does not download the same
     * server observations again just to list them.
     *
     * @param revision [ObservationChangeTracker] after the sync, any observation write since
     * makes the result stale
     */
    private data class CachedReview(
        val review: ReviewCheck,
        val host: String,
        val revision: Long,
        val createdAt: Long
    )

    private val reviewByStudy = ConcurrentHashMap<Int, CachedReview>()

    /**
     * The last automatic sync's review items for the study, if nothing could have changed them
     * since: same server, no observation written, and recent enough that the server is unlikely
     * to have moved on. Uploads check the server for conflicts again regardless.
     */
    private fun cachedReview(studyId: Int): ReviewCheck? {

        val cached = reviewByStudy[studyId]

        val staleReason = when {
            cached == null -> "no automatic sync result"
            cached.host != BrAPIService.getHostUrl(context) -> "server changed"
            cached.revision != ObservationChangeTracker.current -> "observations written since"
            System.currentTimeMillis() - cached.createdAt >= REVIEW_REUSE_MS -> "older than the reuse window"
            else -> null
        }

        if (staleReason != null) {
            Log.d(TAG, "Checking the server for study $studyId review items: $staleReason")
            reviewByStudy.remove(studyId)
            return null
        }

        Log.d(TAG, "Using the review items from the last automatic sync of study $studyId")

        return cached?.review
    }

    /**
     * @param brAPIService the service to ask, only used the first time a server is asked. A new
     * one is created when none is given.
     */
    suspend fun uploadSupport(brAPIService: BrAPIService? = null): UploadSupport {

        val host = BrAPIService.getHostUrl(context) ?: return UploadSupport()

        uploadSupportByHost[host]?.let { return it }

        // BrAPI V1 has no serverinfo calls list to check
        val v2 = (brAPIService ?: newService()) as? BrAPIServiceV2 ?: return UploadSupport()

        val serverCalls = fetchServerCalls(v2)
        if (serverCalls.isNullOrEmpty()) {
            Log.d(TAG, "serverinfo for $host listed no calls, uploads are not restricted")
            return UploadSupport()
        }

        val keys = BrapiImplementationHelper.serverCallKeys(serverCalls)
        val listedServices = keys.map { (service, _) -> service }.toSet()

        // Only a call listed without the method rules an upload out. A call missing entirely is
        // more likely left out of serverinfo than unsupported, so it is tried anyway.
        fun accepts(service: String, method: String) =
            BrapiImplementationHelper.normalizeService(service) !in listedServices
                    || BrapiImplementationHelper.supports(keys, service, method)

        val support = UploadSupport(
            newObservations = accepts("observations", "POST"),
            editedObservations = accepts("observations", "PUT"),
            images = accepts("images", "POST")
                    && accepts("images/{imageDbId}", "PUT")
                    && accepts("images/{imageDbId}/imagecontent", "PUT")
        )

        Log.d(TAG, "Upload support for $host from serverinfo: $support")

        uploadSupportByHost[host] = support

        return support
    }

    // null when serverinfo could not be read
    private suspend fun fetchServerCalls(brAPIService: BrAPIServiceV2): List<BrAPIServerCallInfo>? =
        withTimeoutOrNull(TimeUnit.SECONDS.toMillis(BrAPIService.getTimeoutValue(context).toLong())) {
            suspendCancellableCoroutine { continuation ->
                brAPIService.serverInfoService.fetchServerInfo(
                    onSuccess = { response ->
                        if (continuation.isActive) continuation.resume(response?.result?.calls)
                        null
                    },
                    onFail = { code ->
                        Log.w(TAG, "serverinfo failed with $code, uploads are not checked")
                        if (continuation.isActive) continuation.resume(null)
                        null
                    }
                )
            }
        }

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
        val unsyncableObservationCount: Int = 0,
        // Taken before the read. Uploads of these observations record it as their sync time, so
        // anything edited after the read, in Collect while the sync runs, still counts as edited.
        val loadedAt: String = ""
    ) {
        /**
         * What is not on the server yet, split by whether the server accepts that kind of upload.
         */
        fun unsyncedCounts(support: UploadSupport): UnsyncedCounts {

            val images = newImageObservations.size + editedImageObservations.size +
                    incompleteImageObservations.size

            val kinds = listOf(
                newObservations.size to support.newObservations,
                editedObservations.size to support.editedObservations,
                images to support.images
            )

            return UnsyncedCounts(
                sendable = kinds.filter { (_, accepted) -> accepted }.sumOf { (count, _) -> count },
                unsupported = kinds.filterNot { (_, accepted) -> accepted }.sumOf { (count, _) -> count }
            )
        }
    }

    /**
     * @param sendable observations and images the sync screen can upload, including any that
     * need review first
     * @param unsupported observations and images the server does not accept, no sync can send them
     */
    data class UnsyncedCounts(val sendable: Int = 0, val unsupported: Int = 0)

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

        val loadedAt = syncTimestamp()

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
            unsyncableObservationCount = unsyncableObservationCount,
            loadedAt = loadedAt
        )
    }

    /**
     * Observations, including images, not on the server yet. Uses the same categories as the
     * sync screen so the two counts always agree.
     *
     * @param support what the server accepts, asked for when not given, see [uploadSupport]
     */
    suspend fun countUnsynced(studyId: Int, support: UploadSupport? = null): UnsyncedCounts {

        val local = loadLocalStatus(studyId)

        return local.unsyncedCounts(support ?: uploadSupport())
    }

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
     * applies the updates. The server observations keep their BrAPI values.
     *
     * @return the updates skipped because Collect edited the observation after the sync read it,
     * conflicts now that both sides changed, see [conflictsFor]
     */
    fun saveDownloadedObservations(
        study: FieldObject,
        resolution: DownloadResolution,
        traitsById: Map<String, TraitObject>
    ): List<Observation> {

        Log.d(TAG, "Saving ${resolution.inserts.size} new observations")

        resolution.inserts.forEach { obs ->

            Log.d(TAG, "Saving observation: ${obs.dbId}")

            obs.internalVariableDbId = obs.variableDbId

            // Store categorical BrAPI values using Field Book's internal JSON format.
            val value = normalizeDownloadedObservationValue(obs, traitsById)

            val rep = dataHelper.getNextRep(
                study.studyId.toString(),
                obs.unitDbId,
                obs.internalVariableDbId
            ).toInt()

            dataHelper.insertObservation(
                obs.unitDbId,
                obs.internalVariableDbId,
                value,
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

        // These were unedited when the sync read them, but Collect may have changed one since.
        return applyServerValues(resolution.updates, traitsById, onlyIfUnedited = true)
    }

    /**
     * Overwrites local observations with the server's copy, re-encoding raw BrAPI server values
     * to Field Book's internal format before persisting.
     *
     * @param onlyIfUnedited skip observations edited locally since their last sync, for updates
     * found by a download. A user choosing the server's value in a conflict passes false.
     * @return the observations skipped by [onlyIfUnedited]
     */
    fun applyServerValues(
        serverObservations: List<Observation>,
        traitsById: Map<String, TraitObject>,
        onlyIfUnedited: Boolean = false
    ): List<Observation> {

        // The values are converted in place for the write and put back after it, callers still
        // compare these server copies with local observations.
        val brapiValues = serverObservations.map { it.value }

        serverObservations.forEach { obs ->
            if (obs.internalVariableDbId.isNullOrBlank()) {
                obs.internalVariableDbId = obs.variableDbId
            }
            obs.value = normalizeDownloadedObservationValue(obs, traitsById)
        }

        try {

            if (!onlyIfUnedited) {
                dataHelper.updateObservationsByBrapiId(serverObservations)
                return emptyList()
            }

            val skipped = dataHelper.updateUneditedObservationsByBrapiId(serverObservations)

            return serverObservations.filter { it.dbId in skipped }

        } finally {

            serverObservations.forEachIndexed { index, obs -> obs.value = brapiValues[index] }
        }
    }

    /**
     * Pairs server observations with their local copies edited since the last sync, for the
     * updates a download skipped because Collect edited the observation while the sync ran.
     *
     * @param localStatus read after the download was saved
     */
    fun conflictsFor(serverObservations: List<Observation>, localStatus: LocalStatus): List<Conflict> {

        val editedByDbId = localStatus.editedObservations
            .filter { it.dbId != null }
            .associateBy { it.dbId }

        return serverObservations.mapNotNull { server ->
            editedByDbId[server.dbId]?.let { local ->
                Conflict(serverObservation = server, localObservation = local)
            }
        }
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
     * Re-sync check for issue #550. Before upload, finds observations a prior request already
     * stored on the server, for example when the app crashed after the server write but before
     * the local sync update. Matches against the study's observations as just downloaded, so the
     * check needs no request of its own.
     *
     * Matches that already carry a BrAPI id are linked here, matches without one are returned for
     * the user to choose. For observations without a BrAPI id, new ones, nothing is written.
     *
     * @param allCandidateObservations the study's observations on the server
     * @param adoptServerKeys choice keys the user chose to link to the existing server observation
     * @param uploadAsNewKeys choice keys the user chose to upload again as new observations
     * @return the observations still to upload, and the matches that need a user choice
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
                dataHelper.markObservationsSynced(listOf(localObservation))
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
     * @param onServerCheck called only when the server has to be asked, the last automatic
     * sync's result is reused otherwise
     * @return conflicts: observations changed on this device and on the server since the last sync
     *         resyncChoices: new observations whose values already appear on the server
     */
    suspend fun checkForReview(
        brAPIService: BrAPIService,
        study: FieldObject,
        onServerCheck: () -> Unit = {}
    ): ReviewCheck {

        cachedReview(study.studyId)?.let { return withoutDecided(study.studyId, it) }

        onServerCheck()

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

        return withoutDecided(study.studyId, ReviewCheck(conflicts, resync.pendingChoices))
    }

    /**
     * Drops the review items the user has already decided on, see [BrapiReviewDecisions]. Also
     * applied to a reused result, which may predate the choice.
     */
    private fun withoutDecided(studyId: Int, review: ReviewCheck): ReviewCheck {

        val uploadAsNew = reviewDecisions.get(studyId).uploadAsNew

        return ReviewCheck(
            conflicts = reviewDecisions.unsettled(studyId, review.conflicts),
            resyncChoices = review.resyncChoices.filter { it.key !in uploadAsNew }
        )
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
        localValuesByFieldBookId: Map<String, String>,
        syncedAt: String
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
                        syncedAt = syncedAt,
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

    /**
     * @param syncedAt when the uploaded observations were read, see [LocalStatus.loadedAt]
     */
    private fun processChunk(
        chunk: List<Observation>,
        referenceObservations: List<Observation>,
        isNew: Boolean = true,
        syncedAt: String,
        localValuesByFieldBookId: Map<String, String> = emptyMap()
    ): Triple<Int, Int, Int> {

        var incompatible = 0
        var new = 0
        var edited = 0

        //chunk returned from brapi, each observation is updated individually
        chunk.forEach { observation ->

            var retVal = UploadError.NONE

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
                update.setLastSyncedTime(syncedAt)
                // only the id and sync time, the value may have been edited in Collect meanwhile
                dataHelper.markObservationsSynced(listOf(update))

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
        localValuesByFieldBookId: Map<String, String>,
        syncedAt: String
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
                        syncedAt = syncedAt,
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

    /**
     * @param syncedAt when the images' observations were read, see [LocalStatus.loadedAt]
     */
    suspend fun processImages(
        brAPIService: BrAPIService,
        images: List<FieldBookImage>,
        isNew: Boolean = true,
        syncedAt: String,
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
                        processNewImage(brAPIService, image, syncedAt, onEvent)
                    } else {
                        updateImages(brAPIService, image, syncedAt, onEvent)
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
        syncedAt: String,
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

            // Recorded before the content is sent. If that fails the image is left incomplete, and
            // the next sync finishes this server image rather than posting the details again.
            dataHelper.markImageCreated(image.fieldBookDbId, imageWithDbId.dbId)

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

            val retVal = processImageResponse(finalImage, finalImage.fieldBookDbId, syncedAt)

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
        syncedAt: String,
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

            // Without this the image stayed edited or incomplete and was sent again every sync.
            image.setLastSyncedTime(syncedAt)
            dataHelper.updateImage(image)

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
        syncedAt: String,
    ): UploadError {

        var retVal = UploadError.NONE

        converted.setLastSyncedTime(syncedAt)
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
     * decision is left untouched for the sync screen, unless the user already made it there, see
     * [BrapiReviewDecisions]:
     *
     * - An observation changed both here and on the server since the last sync is a conflict, see
     *   [resolveObservationStatus], including one edited in Collect while this sync runs. Neither
     *   side is changed and the local edit is held back.
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
            val skippedUpdates = saveDownloadedObservations(study, resolution, traitsById)

            markStudySynced(studyId)

            // Upload

            val local = loadLocalStatus(studyId)

            reviewDecisions.prune(studyId, local)

            // An update skipped because Collect edited the observation after the read above is a
            // conflict too, both sides changed. Otherwise the edit would be uploaded over the
            // server's change. Conflicts the user settled by keeping the local value upload.
            val conflicts = reviewDecisions.unsettled(
                studyId,
                resolution.conflicts + conflictsFor(skippedUpdates, local)
            )
            val heldBack = conflicts.mapNotNull { it.localObservation.fieldBookDbId }.toSet()

            // Matches the user chose to upload again, the rest wait for a choice.
            val uploadAsNew = reviewDecisions.get(studyId).uploadAsNew

            // Only what the server accepts, otherwise every sync would fail on the same calls.
            val support = uploadSupport(brAPIService)

            // Automatic syncs can leave edits to the sync screen, where they are checked first.
            val uploadEdited = support.editedObservations && preferences.getString(
                PreferenceKeys.BRAPI_SYNC_UPLOAD_MODE,
                UPLOAD_MODE_ALL
            ) != UPLOAD_MODE_NEW

            var uploaded = 0
            var failedCode: Int? = null

            listOf(
                (if (support.newObservations) local.newObservations else emptyList()) to true,
                (if (uploadEdited) local.editedObservations.filter { it.fieldBookDbId !in heldBack } else emptyList()) to false
            ).forEach { (observations, isNew) ->

                if (observations.isEmpty() || failedCode == UNAUTHORIZED) return@forEach

                val localValues = prepareForUpload(observations)

                // Against this sync's own download, it already holds the study's observations.
                val resync = matchResyncCandidates(
                    observations,
                    localValues,
                    downloaded,
                    mutableSetOf(),
                    uploadAsNew.toMutableSet()
                )
                if (resync.observationsToUpload.isEmpty()) return@forEach

                val flow = if (isNew) {
                    processNewObservations(brAPIService, resync.observationsToUpload, localValues, local.loadedAt)
                } else {
                    processEditedObservations(brAPIService, resync.observationsToUpload, localValues, local.loadedAt)
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

            // Images can be large, so automatic syncs can leave them to the sync screen.
            val uploadImages = preferences.getBoolean(PreferenceKeys.BRAPI_SYNC_UPLOAD_IMAGES, true)

            if (failedCode != UNAUTHORIZED && support.images && uploadImages) {

                processImages(
                    brAPIService,
                    dataHelper.getImageDetails(context, local.newImageObservations),
                    syncedAt = local.loadedAt
                ) { event -> if (event == ImageUploadEvent.Inserted) uploadedImages.incrementAndGet() }

                // An incomplete image is a new one whose content never finished uploading, so it
                // goes with new data even though it is sent the way edits are.
                val imageUpdates = (if (uploadEdited) local.editedImageObservations else emptyList()) +
                        local.incompleteImageObservations

                processImages(
                    brAPIService,
                    dataHelper.getImageDetails(context, imageUpdates),
                    isNew = false,
                    syncedAt = local.loadedAt
                ) { event -> if (event == ImageUploadEvent.Edited) uploadedImages.incrementAndGet() }
            }

            uploaded += uploadedImages.get()

            if (uploaded > 0) markStudySynced(studyId)

            // What is left for the user, found from this sync's download rather than asking the
            // server again: the conflicts held back, and new observations still not uploaded
            // whose values are already on the server. Read after this sync's own writes, a fresh
            // copy, the upload converted the values of the copies it sent.
            val remaining = loadLocalStatus(studyId)
            val resyncChoices = matchResyncCandidates(
                remaining.newObservations,
                prepareForUpload(remaining.newObservations),
                downloaded,
                mutableSetOf(),
                mutableSetOf()
            ).pendingChoices

            val review = withoutDecided(studyId, ReviewCheck(conflicts, resyncChoices))

            reviewByStudy[studyId] = CachedReview(
                review = review,
                host = BrAPIService.getHostUrl(context),
                revision = ObservationChangeTracker.current,
                createdAt = System.currentTimeMillis()
            )

            val needsReview = review.conflicts.size + review.resyncChoices.size

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

            reviewByStudy.remove(studyId)

            Log.e(TAG, "Automatic sync of study $studyId failed", e)

            AutoSyncResult(
                error = if (e.code == UNAUTHORIZED) AutoSyncError.UNAUTHORIZED else AutoSyncError.FAILED
            )

        } catch (e: Exception) {

            reviewByStudy.remove(studyId)

            Log.e(TAG, "Automatic sync of study $studyId failed", e)

            AutoSyncResult(error = AutoSyncError.FAILED)

        } finally {

            lock.unlock()

        }
    }
}
