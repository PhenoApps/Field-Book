package com.fieldbook.tracker.database

import androidx.compose.runtime.Immutable
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.ui.grid.datagrid.MapPlotData
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
        /** Visible trait DB IDs in display order — part of the cache key (order matters). */
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

    data class MapSnapshot(
        val studyId: Int,
        val rowAttr: String,
        val colAttr: String,
        val invertRow: Boolean,
        val invertCol: Boolean,
        /** Visible trait DB IDs in display order — part of the cache key (order matters). */
        val traitIds: List<String>,
        /** COUNT(*) of observations at the time the snapshot was built, used for staleness checks. */
        val observationCount: Int,
        val plots: List<MapPlotData>,
        val gridRows: Int,
        val gridCols: Int,
        val grid: Array<Array<MapPlotData?>>
    )

    private val mapLock = Any()
    private var mapSnapshot: MapSnapshot? = null

    /**
     * Returns the cached map snapshot if the cache key matches and the observation count hasn't
     * changed, or null on a miss.
     */
    fun getMap(
        studyId: Int,
        rowAttr: String,
        colAttr: String,
        invertRow: Boolean,
        invertCol: Boolean,
        traitIds: List<String>,
        observationCount: Int
    ): MapSnapshot? =
        synchronized(mapLock) {
            val s = mapSnapshot ?: return null
            if (s.studyId == studyId && s.rowAttr == rowAttr && s.colAttr == colAttr &&
                s.invertRow == invertRow && s.invertCol == invertCol &&
                s.traitIds == traitIds && s.observationCount == observationCount
            ) s else null
        }

    fun putMap(snapshot: MapSnapshot) {
        synchronized(mapLock) { mapSnapshot = snapshot }
    }

    fun invalidateMap() {
        synchronized(mapLock) { mapSnapshot = null }
    }
}
