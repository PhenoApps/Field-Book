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
 * activity checks whether the snapshot is still valid (same study/traits/row-header and no
 * observation writes since it was built) before skipping the expensive multi-JOIN query.
 */
@Singleton
class DataGridCache @Inject constructor() {

    @Immutable
    data class HeaderData(val name: String)

    @Immutable
    data class CellData(val value: String?, val code: String)

    data class GridSnapshot(
        val studyId: Int,
        /**
         * Fingerprint of the visible traits in display order — part of the cache key. Covers the
         * trait attributes that change how a cell renders (not just which traits are shown), so
         * toggling e.g. a trait's repeated-measures or category-display parameter is a miss.
         */
        val traitsFingerprint: String,
        val rowHeader: String,
        val extraHeaders: List<String> = emptyList(),
        /** [ObservationChangeTracker] revision when the snapshot was built, for staleness checks. */
        val observationsRevision: Long,
        val traits: List<TraitObject>,
        val rowHeaders: List<HeaderData>,
        val plotIds: List<String>,
        val gridData: List<List<CellData>>,
        val extraHeaderData: List<List<String>> = emptyList()
    )

    private val lock = Any()
    private var snapshot: GridSnapshot? = null

    /**
     * Returns the cached snapshot if the cache key matches and no observations have been written
     * since it was built, or null on a miss.
     */
    fun get(
        studyId: Int,
        traitsFingerprint: String,
        rowHeader: String,
        extraHeaders: List<String> = emptyList()
    ): GridSnapshot? =
        synchronized(lock) {
            val s = snapshot ?: return null
            if (s.studyId == studyId && s.traitsFingerprint == traitsFingerprint &&
                s.rowHeader == rowHeader && s.extraHeaders == extraHeaders &&
                s.observationsRevision == ObservationChangeTracker.current
            ) s else null
        }

    /**
     * Builds the trait half of the cache key from the traits about to be rendered. Includes every
     * attribute the grid reads while building a cell, so a trait edit that changes rendering
     * invalidates the snapshot even though the set of trait IDs is unchanged.
     */
    fun fingerprintTraits(traits: List<TraitObject>): String =
        traits.joinToString("|") { trait ->
            listOf(
                trait.id,
                trait.alias,
                trait.format,
                trait.categoryDisplayValue,
                trait.repeatedMeasures
            ).joinToString(":")
        }

    fun put(snapshot: GridSnapshot) {
        synchronized(lock) { this.snapshot = snapshot }
    }

    data class MapSnapshot(
        val studyId: Int,
        val rowAttr: String,
        val colAttr: String,
        val invertRow: Boolean,
        val invertCol: Boolean,
        /** Visible trait DB IDs in display order — part of the cache key (order matters). */
        val traitIds: List<String>,
        /** [ObservationChangeTracker] revision when the snapshot was built, for staleness checks. */
        val observationsRevision: Long,
        val plots: List<MapPlotData>,
        val gridRows: Int,
        val gridCols: Int,
        val grid: Array<Array<MapPlotData?>>
    )

    private val mapLock = Any()
    private var mapSnapshot: MapSnapshot? = null

    /**
     * Returns the cached map snapshot if the cache key matches and no observations have been
     * written since it was built, or null on a miss.
     */
    fun getMap(
        studyId: Int,
        rowAttr: String,
        colAttr: String,
        invertRow: Boolean,
        invertCol: Boolean,
        traitIds: List<String>
    ): MapSnapshot? =
        synchronized(mapLock) {
            val s = mapSnapshot ?: return null
            if (s.studyId == studyId && s.rowAttr == rowAttr && s.colAttr == colAttr &&
                s.invertRow == invertRow && s.invertCol == invertCol &&
                s.traitIds == traitIds &&
                s.observationsRevision == ObservationChangeTracker.current
            ) s else null
        }

    fun putMap(snapshot: MapSnapshot) {
        synchronized(mapLock) { mapSnapshot = snapshot }
    }
}
