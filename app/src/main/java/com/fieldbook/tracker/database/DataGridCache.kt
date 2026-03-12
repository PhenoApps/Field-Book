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
    data class HeaderData(val name: String, val code: String)

    @Immutable
    data class CellData(val value: String?, val code: String, val color: Int = android.graphics.Color.GREEN)

    data class GridSnapshot(
        val studyId: Int,
        /** Sorted list of visible trait DB IDs — part of the cache key. */
        val traitIds: List<String>,
        val rowHeader: String,
        /** COUNT(*) of observations at the time the snapshot was built, used for staleness checks. */
        val observationCount: Int,
        val traits: List<TraitObject>,
        val rowHeaders: List<HeaderData>,
        val plotIds: List<String>,
        val gridData: List<List<CellData>>
    )

    @Volatile
    private var snapshot: GridSnapshot? = null

    /**
     * Returns the cached snapshot if the cache key matches, or null on a miss.
     */
    fun get(studyId: Int, traitIds: List<String>, rowHeader: String): GridSnapshot? {
        val s = snapshot ?: return null
        return if (s.studyId == studyId && s.traitIds == traitIds && s.rowHeader == rowHeader) s
        else null
    }

    fun put(snapshot: GridSnapshot) {
        this.snapshot = snapshot
    }

    fun invalidate() {
        snapshot = null
    }
}
