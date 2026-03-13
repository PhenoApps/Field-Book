package com.fieldbook.tracker.database

import androidx.compose.runtime.Immutable
import com.fieldbook.tracker.objects.TraitObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Application-scoped cache for the DataGrid feature.
 *
 * Holds the last fully-computed grid snapshot. On subsequent opens of DataGridActivity, the
 * activity checks whether the snapshot is still valid (same study/traits/row-header and the same
 * observation count) before skipping the expensive multi-JOIN query.
 */
@Singleton
class DataGridCache @Inject constructor() {

    @Immutable
    data class HeaderData(val name: String)

    @Immutable
    data class CellData(val value: String?, val code: String)

    data class GridSnapshot(
        val studyId: Int,
        /** Sorted list of visible trait DB IDs — part of the cache key. */
        val traitIds: List<String>,
        val rowHeader: String,
        val extraHeaders: List<String> = emptyList(),
        /** COUNT(*) of observations at the time the snapshot was built, used for staleness checks. */
        val observationCount: Int,
        val traits: List<TraitObject>,
        val rowHeaders: List<HeaderData>,
        val plotIds: List<String>,
        val gridData: List<List<CellData>>,
        val extraHeaderData: List<List<String>> = emptyList()
    )

    private val lock = Any()
    private var snapshot: GridSnapshot? = null

    /**
     * Returns the cached snapshot if the cache key matches, or null on a miss.
     */
    fun get(studyId: Int, traitIds: List<String>, rowHeader: String, extraHeaders: List<String> = emptyList()): GridSnapshot? =
        synchronized(lock) {
            val s = snapshot ?: return null
            if (s.studyId == studyId && s.traitIds == traitIds && s.rowHeader == rowHeader && s.extraHeaders == extraHeaders) s
            else null
        }

    fun put(snapshot: GridSnapshot) {
        synchronized(lock) { this.snapshot = snapshot }
    }

    fun invalidate() {
        synchronized(lock) { snapshot = null }
    }
}
