package com.fieldbook.tracker.brapi

import com.fieldbook.tracker.brapi.model.Observation
import com.fieldbook.tracker.traits.formats.Formats
import org.brapi.v2.model.pheno.BrAPIObservation
import org.threeten.bp.OffsetDateTime

/**
 * BrAPI export-bucket routing for tree architecture traits.
 *
 * Local / user-created tree traits are uploadable (child OUs + node obs), so they
 * must not sit in `userCreatedTraitObservations`.
 */
object TreeBrapiExportRouting {

    /** Minimal sync-status inputs — avoids pulling BrAPI Observation into pure JVM callers. */
    data class SyncStatus(
        val dbId: String?,
        val timestamp: OffsetDateTime?,
        val lastSyncedTime: OffsetDateTime?,
    )

    @JvmStatic
    fun isTreeArchitecture(format: String?): Boolean =
        format.equals(Formats.TREE_ARCHITECTURE.getDatabaseName(), ignoreCase = true)

    /**
     * Returns the [ObservationDao.getBrAPIExportData] category for one row.
     */
    @JvmStatic
    @JvmOverloads
    fun exportCategory(
        format: String?,
        source: String?,
        hostUrl: String,
        sync: SyncStatus,
        isPhoto: Boolean = false,
    ): String {
        val localOrNull = source == "local" || source == null
        val tree = isTreeArchitecture(format)

        // Local tree architecture traits are uploaded (child OU POST path).
        if (localOrNull && tree && !isPhoto) {
            return statusCategory(sync, isPhoto = false)
        }

        return when {
            localOrNull -> if (isPhoto) "userCreatedImageObservations" else "userCreatedTraitObservations"
            source != hostUrl -> if (isPhoto) "wrongSourceImageObservations" else "wrongSourceObservations"
            else -> statusCategory(sync, isPhoto)
        }
    }

    /**
     * Local tree traits often have an empty `external_db_id` / variableDbId, so
     * [mapObservations] drops them. After a successful upload, synthesize sync
     * results so parents leave the `newObservations` bucket.
     *
     * Only [submitted] observations that are tree-architecture traits should be
     * passed in — callers must filter; blank variableDbId alone is not enough.
     */
    @JvmStatic
    fun ensureLocalTreeParentsSynced(
        submitted: List<Observation>,
        mapped: MutableList<Observation>,
        serverData: List<BrAPIObservation>?,
        fieldBookReferenceSource: String,
    ): List<Observation> {
        val already = mapped.mapNotNull { it.fieldbookDbId }.toHashSet()
        val byFieldBookId = serverFieldBookIds(serverData, fieldBookReferenceSource)
        for (parent in submitted) {
            val fbId = parent.fieldbookDbId?.takeIf { it.isNotBlank() } ?: continue
            if (parent.variableDbId?.isNotBlank() == true) continue
            if (fbId in already) continue
            val serverDbId = byFieldBookId[fbId]
            val sync = Observation().apply {
                setFieldBookDbId(fbId)
                unitDbId = parent.unitDbId
                variableDbId = parent.variableDbId
                value = parent.value
                dbId = serverDbId?.takeIf { it.isNotBlank() } ?: "tree-local-$fbId"
                lastSyncedTime = OffsetDateTime.now()
            }
            mapped += sync
            already += fbId
        }
        return mapped
    }

    private fun serverFieldBookIds(
        serverData: List<BrAPIObservation>?,
        fieldBookReferenceSource: String,
    ): Map<String, String> {
        if (serverData.isNullOrEmpty()) return emptyMap()
        val out = LinkedHashMap<String, String>()
        for (obs in serverData) {
            val refs = obs.externalReferences ?: continue
            val fbId = refs.firstOrNull { ref ->
                ref.referenceSource == fieldBookReferenceSource
            }?.let { ref ->
                listOfNotNull(ref.referenceId, ref.referenceID)
                    .firstOrNull { it.isNotBlank() }
            } ?: continue
            val obsDbId = obs.observationDbId?.takeIf { it.isNotBlank() } ?: continue
            out[fbId] = obsDbId
        }
        return out
    }

    private fun statusCategory(sync: SyncStatus, isPhoto: Boolean): String {
        if (sync.dbId == null) {
            return if (isPhoto) "newImageObservations" else "newObservations"
        }
        if (sync.lastSyncedTime == null) {
            return if (isPhoto) "incompleteImageObservations" else "editedObservations"
        }
        val obsTimestamp = sync.timestamp
        val syncTimestamp = sync.lastSyncedTime
        return if (obsTimestamp == null || obsTimestamp <= syncTimestamp) {
            if (isPhoto) "syncedImageObservations" else "syncedObservations"
        } else {
            if (isPhoto) "editedImageObservations" else "editedObservations"
        }
    }
}
