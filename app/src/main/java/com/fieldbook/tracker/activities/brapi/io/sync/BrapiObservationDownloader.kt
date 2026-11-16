package com.fieldbook.tracker.activities.brapi.io.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.fieldbook.tracker.brapi.model.Observation
import com.fieldbook.tracker.brapi.service.BrAPIService
import com.fieldbook.tracker.brapi.service.BrAPIServiceV2
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
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import org.brapi.v2.model.pheno.BrAPIObservation
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

/**
 * Downloads a study's observations from the BrAPI server and saves them into Field Book fields.
 *
 * Used by [BrapiSyncViewModel], which adds conflict handling against local edits,
 * and by the study importer, which saves into fields that were just created.
 *
 * The server is queried by study, so a study imported at several levels is fetched once
 * and its observations are split between its fields by observation unit.
 */
class BrapiObservationDownloader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataHelper: DataHelper,
    private val preferences: SharedPreferences,
    private val traitRepo: TraitRepository,
) {

    companion object {
        private const val TAG = "BrapiObsDownloader"
    }

    /**
     * Download progress across all studies, page counts are for the current study only.
     */
    data class Progress(
        val studyIndex: Int,
        val studyCount: Int,
        val page: Int,
        val totalPages: Int,
    )

    /**
     * Variables from the field's server, the observations of any other variable are dropped while mapping.
     */
    suspend fun getVariableDbIds(field: FieldObject): List<String> =
        traitRepo.getTraits()
            .filter { it.traitDataSource.isNotEmpty() && it.traitDataSource == field.dataSource }
            .mapNotNull { it.externalDbId }

    /**
     * Pages through the study's observations, sending progress for each page and then all observations.
     */
    fun fetch(
        brAPIService: BrAPIService,
        studyDbId: String,
        variableDbIds: List<String>
    ): Flow<DownloadProgressUpdate> = channelFlow {

        val pageCount = AtomicInteger(1)

        val pageSize = BrAPIService.getPageSize(context)
        val paginationManager = BrapiPaginationManager(0, pageSize)

        Log.d(
            TAG,
            "Starting to pull observations from brapi server with pagesize: $pageSize and ${variableDbIds.size} variables for $studyDbId"
        )

        //gets the first page of data and updates the pagination manager with total page size
        val firstPageResult = brAPIService.awaitGetSingleObservationPage(
            studyDbId,
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
                brapiStudyId = studyDbId,
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

    /**
     * Keeps the observations of the field's own units. The server returns the whole study,
     * but a study imported at several levels is split into one field per level.
     */
    fun filterToField(fieldId: Int, observations: List<Observation>): List<Observation> {

        val unitDbIds = dataHelper.getAllObservationUnits(fieldId)
            .mapTo(hashSetOf()) { it.observation_unit_db_id }

        return observations.filter { it.unitDbId in unitDbIds }.also {
            Log.d(TAG, "Kept ${it.size} of ${observations.size} observations for field $fieldId")
        }
    }

    /**
     * Saves server observations into the field as new local observations, each in the next rep
     * of its unit and variable. Callers decide which observations are new.
     *
     * The field's highest reps are read once and counted up in memory, and the inserts are written
     * in one transaction, rather than a rep query and a commit for each observation.
     */
    fun insert(
        field: FieldObject,
        observations: List<Observation>,
        traitsById: Map<String, TraitObject>
    ) {

        if (observations.isEmpty()) return

        val studyId = field.studyId.toString()

        Log.d(TAG, "Saving ${observations.size} observations into field $studyId")

        val maxReps = HashMap(dataHelper.getMaxReps(studyId))

        dataHelper.beginTransaction()

        try {

            observations.forEach { obs ->

                obs.internalVariableDbId = obs.variableDbId

                // Store categorical BrAPI values using Field Book's internal JSON format.
                obs.value = normalizeValue(obs, traitsById)

                val key = obs.unitDbId.orEmpty() to obs.internalVariableDbId.orEmpty()
                val rep = (maxReps[key] ?: 0) + 1
                maxReps[key] = rep

                dataHelper.insertObservation(
                    obs.unitDbId,
                    obs.internalVariableDbId,
                    obs.value ?: "",
                    obs.collector ?: "",
                    "",
                    "",
                    studyId,
                    obs.dbId,
                    //without a server timestamp the insert would use the current time, which is later
                    //than the sync time, and the next sync would treat the observation as a local edit
                    obs.timestamp ?: obs.lastSyncedTime,
                    obs.lastSyncedTime,
                    rep.toString()
                )
            }

            dataHelper.setTransactionSuccessfull()

        } finally {

            dataHelper.endTransaction()
        }
    }

    /**
     * Downloads the observations of each field's study and saves the ones the field doesn't have yet.
     * Meant for fields without local edits, such as fields that were just imported,
     * conflicts are not detected, use the sync screen for fields that have been collected on.
     *
     * Fields of the same study share one download. A study that fails to download throws,
     * fields of studies before it keep their saved observations.
     *
     * @param included observations the server already sent for a study, such as with its units,
     * keyed by study, these are saved instead of downloading the study's observations
     * @return the number of observations saved into each field, keyed by field id
     */
    suspend fun downloadInto(
        brAPIService: BrAPIService,
        fields: List<FieldObject>,
        included: Map<String, List<BrAPIObservation>> = emptyMap(),
        onProgress: (Progress) -> Unit = {}
    ): Map<Int, Int> = withContext(Dispatchers.IO) {

        val saved = hashMapOf<Int, Int>()

        val hostUrl = BrAPIService.getHostUrl(context)
        val traitsById = traitRepo.getTraits().associateBy { it.id }

        val studies = fields.filter { !it.studyDbId.isNullOrEmpty() }.groupBy { it.studyDbId }

        studies.entries.forEachIndexed { index, (studyDbId, studyFields) ->

            val variableDbIds = getVariableDbIds(studyFields.first())

            onProgress(Progress(index, studies.size, 0, 0))

            //mapped like the observations endpoint's, after the import so the study's traits are known
            val includedObservations = included[studyDbId]
                ?.let { (brAPIService as? BrAPIServiceV2)?.mapObservations(it, variableDbIds) }

            var observations = includedObservations.orEmpty()

            if (includedObservations == null) {

                fetch(brAPIService, studyDbId, variableDbIds).collect { update ->
                    when (update) {
                        is DownloadProgressUpdate.InDownloadProgress ->
                            onProgress(Progress(index, studies.size, update.pageCount, update.totalPages))

                        is DownloadProgressUpdate.Completed -> observations = update.data
                    }
                }
            }

            studyFields.forEach { field ->

                //skip observations the field already has, so a repeated download doesn't add reps
                val existingDbIds = dataHelper.getBrAPIExportData(field.studyId, hostUrl)
                    .values.flatten().mapNotNullTo(hashSetOf()) { it.dbId }

                val newObservations = filterToField(field.studyId, observations)
                    .filter { it.dbId !in existingDbIds }

                insert(field, newObservations, traitsById)

                if (newObservations.isNotEmpty()) dataHelper.updateSyncDate(field.studyId)

                saved[field.studyId] = newObservations.size
            }
        }

        saved
    }

    /**
     * Converts a server value to the format Field Book stores for the observation's trait.
     */
    fun normalizeValue(
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
}
