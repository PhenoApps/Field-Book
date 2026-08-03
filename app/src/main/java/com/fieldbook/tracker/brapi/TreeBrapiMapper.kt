package com.fieldbook.tracker.brapi

import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.tree.TreeNode
import com.fieldbook.tracker.traits.formats.tree.TreeObservation
import com.fieldbook.tracker.traits.formats.tree.TreeSchema
import com.fieldbook.tracker.traits.formats.tree.allNodes
import com.fieldbook.tracker.traits.formats.tree.mtgPath
import com.fieldbook.tracker.traits.formats.tree.parentOf
import com.fieldbook.tracker.traits.formats.tree.pathTo
import com.google.gson.JsonObject
import org.brapi.v2.model.TimeAdapter
import org.brapi.v2.model.pheno.BrAPIObservation
import org.brapi.v2.model.pheno.BrAPIObservationUnit
import org.brapi.v2.model.pheno.BrAPIObservationUnitLevelRelationship
import org.brapi.v2.model.pheno.BrAPIObservationUnitPosition
import org.threeten.bp.OffsetDateTime

data class TreeBrapiNodeUnit(
    val observationUnit: BrAPIObservationUnit,
    val nodeId: String,
    val mtgEdge: String,
)

data class TreeBrapiPreparedUpload(
    val childUnits: List<TreeBrapiNodeUnit>,
    val nodeObservations: List<BrAPIObservation>,
)

object TreeBrapiMapper {

    /**
     * Builds one BrAPI ObservationUnit shape per tree node (including root).
     *
     * [observationLevelRelationships] is the ancestor chain ending at the plot —
     * never includes self and never uses a fake `"parent"` levelName.
     *
     * Callers POST these via [TreeBrapiUploadSequence] / [ObservationUnitService].
     */
    fun buildChildObservationUnits(
        observation: TreeObservation,
        schema: TreeSchema,
        parentUnitDbId: String,
        studyDbId: String? = null,
        traitResolver: (String) -> TraitObject?,
    ): List<TreeBrapiNodeUnit> {
        val plotDbId = parentUnitDbId.takeIf { it.isNotBlank() } ?: observation.unit
        val units = mutableListOf<TreeBrapiNodeUnit>()
        allNodes(observation.root).forEach { node ->
            val isRoot = node.id == observation.root.id
            val ancestors = pathTo(observation.root, node.id).dropLast(1)
            val unit = BrAPIObservationUnit()
            unit.observationUnitDbId = node.id
            studyDbId?.takeIf { it.isNotBlank() }?.let { unit.studyDbId = it }
            unit.observationUnitName = if (isRoot) {
                "${node.cls}${node.idx}"
            } else {
                mtgPath(node, observation.root)
            }

            val level = levelOf(node, schema, order = ancestors.size + 1)

            val relationships = mutableListOf<BrAPIObservationUnitLevelRelationship>()
            relationships += plotLevel(plotDbId)
            ancestors.forEachIndexed { index, ancestor ->
                relationships += levelOf(ancestor, schema, order = index + 1)
            }

            val position = BrAPIObservationUnitPosition()
            position.observationLevelRelationships = relationships
            position.observationLevel = level
            unit.observationUnitPosition = position

            val immediateParentDbId = parentOf(observation.root, node.id)?.id ?: plotDbId
            val info = JsonObject()
            info.addProperty("mtgEdge", node.edge.symbol)
            info.addProperty("mtgClass", node.cls)
            info.addProperty("mtgIndex", node.idx.toString())
            info.addProperty("parentObservationUnitDbId", immediateParentDbId)
            node.traits.forEach { (traitName, value) ->
                val ontologyId = traitResolver(traitName)?.externalDbId?.takeIf { it.isNotBlank() }
                if (ontologyId != null) {
                    info.addProperty("observationVariableDbId_$traitName", ontologyId)
                }
                // Always attach collected values — even when the study trait has no ontology id.
                info.addProperty("value_$traitName", value)
            }
            unit.additionalInfo = info
            units += TreeBrapiNodeUnit(unit, node.id, node.edge.symbol)
        }
        return units
    }

    /**
     * Builds BrAPI Observations for per-node trait values that have a resolved
     * [TraitObject.externalDbId]. Values without ontology ids stay on the unit
     * [additionalInfo] only (`value_*` from [buildChildObservationUnits]).
     */
    fun buildNodeObservations(
        observation: TreeObservation,
        studyDbId: String?,
        collector: String?,
        timestamp: OffsetDateTime?,
        traitResolver: (String) -> TraitObject?,
    ): List<BrAPIObservation> {
        val out = mutableListOf<BrAPIObservation>()
        allNodes(observation.root).forEach { node ->
            node.traits.forEach { (traitName, value) ->
                if (value.isBlank()) return@forEach
                val ontologyId = traitResolver(traitName)?.externalDbId?.takeIf { it.isNotBlank() }
                    ?: return@forEach
                val obs = BrAPIObservation()
                obs.observationUnitDbId = node.id
                studyDbId?.takeIf { it.isNotBlank() }?.let { obs.studyDbId = it }
                obs.observationVariableDbId = ontologyId
                obs.observationVariableName = traitName
                obs.value = value
                collector?.let { obs.collector = it.trim() }
                timestamp?.let { obs.observationTimeStamp = TimeAdapter.convertFrom(it) }
                out += obs
            }
        }
        return out
    }

    fun prepareUpload(
        observation: TreeObservation,
        schema: TreeSchema,
        parentUnitDbId: String,
        studyDbId: String? = null,
        collector: String? = null,
        timestamp: OffsetDateTime? = null,
        traitResolver: (String) -> TraitObject?,
    ): TreeBrapiPreparedUpload {
        val childUnits = buildChildObservationUnits(
            observation,
            schema,
            parentUnitDbId,
            studyDbId,
            traitResolver,
        )
        val nodeObservations = buildNodeObservations(
            observation,
            studyDbId,
            collector,
            timestamp,
            traitResolver,
        )
        return TreeBrapiPreparedUpload(childUnits, nodeObservations)
    }

    /**
     * After child OU POST, rewrite node observation unit DbIds from local node ids
     * to server-assigned ids (matched by request order when sizes align).
     */
    @JvmStatic
    fun remapChildUnitResponseIds(
        requested: List<BrAPIObservationUnit>,
        response: List<BrAPIObservationUnit>?,
        nodeObservations: List<BrAPIObservation>,
    ): Map<String, String> {
        val localToServer = LinkedHashMap<String, String>()
        if (response.isNullOrEmpty()) return localToServer
        val count = minOf(requested.size, response.size)
        for (i in 0 until count) {
            val local = requested[i].observationUnitDbId ?: continue
            val server = response[i].observationUnitDbId ?: continue
            if (local.isNotBlank() && server.isNotBlank()) {
                localToServer[local] = server
            }
        }
        // Fallback: match by observationUnitName when order differs.
        if (localToServer.size < requested.size) {
            val byName = response.mapNotNull { unit ->
                val name = unit.observationUnitName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val id = unit.observationUnitDbId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                name to id
            }.toMap()
            requested.forEach { req ->
                val local = req.observationUnitDbId ?: return@forEach
                if (local in localToServer) return@forEach
                val name = req.observationUnitName ?: return@forEach
                byName[name]?.let { localToServer[local] = it }
            }
        }
        nodeObservations.forEach { obs ->
            val local = obs.observationUnitDbId ?: return@forEach
            localToServer[local]?.let { obs.observationUnitDbId = it }
        }
        return localToServer
    }

    private fun plotLevel(plotDbId: String): BrAPIObservationUnitLevelRelationship {
        val plot = BrAPIObservationUnitLevelRelationship()
        plot.observationUnitDbId = plotDbId
        plot.levelName = "plot"
        plot.levelCode = plotDbId
        plot.levelOrder = 0
        return plot
    }

    private fun levelOf(
        node: TreeNode,
        schema: TreeSchema,
        order: Int,
    ): BrAPIObservationUnitLevelRelationship {
        val level = BrAPIObservationUnitLevelRelationship()
        level.observationUnitDbId = node.id
        level.levelName = schema.typeOf(node.nodeType)?.displayName ?: node.nodeType
        level.levelCode = "${node.cls}${node.idx}"
        level.levelOrder = order
        return level
    }
}

internal fun brapiInfoOf(vararg pairs: Pair<String, String>): JsonObject {
    val info = JsonObject()
    pairs.forEach { (key, value) -> info.addProperty(key, value) }
    return info
}
