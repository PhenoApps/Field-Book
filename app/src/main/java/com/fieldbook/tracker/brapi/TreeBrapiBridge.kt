package com.fieldbook.tracker.brapi

import android.content.Context
import androidx.core.net.toUri
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.traits.formats.tree.DatabaseTraitRefResolver
import com.fieldbook.tracker.traits.formats.tree.TreeSummary
import com.fieldbook.tracker.utilities.TreeSchemaLoader
import com.fieldbook.tracker.utilities.TreeSidecarWriter
import org.brapi.v2.model.pheno.BrAPIObservation
import org.threeten.bp.OffsetDateTime

/**
 * Prepares tree-architecture BrAPI uploads: child ObservationUnits, optional
 * per-node Observations, and parent-observation summary [additionalInfo].
 *
 * Child units are POSTed by [TreeBrapiUploadSequence] (via BrAPIServiceV2), not
 * discarded into `additionalInfo`.
 */
object TreeBrapiBridge {

    /**
     * Enriches [observation] with tree summary [additionalInfo] and returns the
     * prepared child units + node observations for POST. Returns null when the
     * trait is not tree architecture or the sidecar / schema cannot be loaded.
     *
     * On success, replaces the parent observation value (often a content:// URI)
     * with a compact summary metric / MTG string suitable for BrAPI export.
     */
    @JvmStatic
    fun enrichObservation(
        context: Context,
        database: DataHelper,
        observation: BrAPIObservation,
        traitFormat: String?,
        traitResourceFile: String?,
        value: String?,
        collector: String? = null,
        timestamp: OffsetDateTime? = null,
    ): TreeBrapiPreparedUpload? {
        if (traitFormat != Formats.TREE_ARCHITECTURE.getDatabaseName() || value.isNullOrBlank()) {
            return null
        }
        val sidecar = TreeSidecarWriter.read(context, value.toUri()) ?: return null
        val schema = TreeSchemaLoader.load(context, traitResourceFile) ?: return null
        val resolver = DatabaseTraitRefResolver(database::getTraitByName, database::getTraitByAlias)
        val prepared = TreeBrapiMapper.prepareUpload(
            sidecar,
            schema,
            parentUnitDbId = observation.observationUnitDbId ?: sidecar.unit,
            studyDbId = observation.studyDbId,
            collector = collector,
            timestamp = timestamp,
        ) { name -> resolver.resolve(name) }

        val summary = TreeSummary.compute(sidecar.root, schema)
        // Same primary total Collect flushes (pod sum or length sum).
        val metric = summary.podTotal.toString()
        val mtg = sidecar.mtg.takeIf { it.isNotBlank() }.orEmpty()
        // Prefer summary metric; fall back to MTG — never ship content:// URIs.
        observation.value = metric.ifBlank { null } ?: mtg.ifBlank { null } ?: observation.value

        val pairs = mutableListOf(
            "treeSchemaId" to schema.id,
            "treeMtg" to mtg,
            "treeSummaryMetric" to metric,
            "treeUsesLengthMetric" to summary.usesLengthMetric.toString(),
            "treeChildUnitCount" to prepared.childUnits.size.toString(),
            "treeChildUnitsPosted" to "true",
        )
        prepared.childUnits.firstOrNull()?.let { pairs += "sampleMtgEdge" to it.mtgEdge }
        observation.studyDbId?.takeIf { it.isNotBlank() }?.let { pairs += "treeStudyDbId" to it }
        if (prepared.nodeObservations.isNotEmpty()) {
            pairs += "treeNodeObservationCount" to prepared.nodeObservations.size.toString()
        }
        observation.additionalInfo = brapiInfoOf(*pairs.toTypedArray())
        return prepared
    }
}
