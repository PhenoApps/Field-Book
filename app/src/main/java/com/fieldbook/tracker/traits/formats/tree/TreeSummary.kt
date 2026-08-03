package com.fieldbook.tracker.traits.formats.tree

import kotlin.math.max

data class TreeSummary(
    val nodeCount: Int,
    /** Soybean: seed/pod sum. Stem/branch fallback: sum of numeric `length` (NA skipped). */
    val podTotal: Int,
    val branchCount: Int,
    val maxOrder: Int,
    val podsPerNode: Double,
    /** True when primary total is length sum (no internode schema). */
    val usesLengthMetric: Boolean = false,
    var sourceUri: String? = null,
    var computedAt: String? = null,
    var schemaId: String? = null,
) {
    /** False for all-zero summaries (no pods/lengths/stems/branches worth exporting). */
    fun hasContent(): Boolean =
        podTotal != 0 || nodeCount != 0 || branchCount != 0

    companion object {
        private const val LENGTH_TRAIT = "length"

        fun compute(root: TreeNode, schema: TreeSchema): TreeSummary {
            // Soybean mode only when an internode-named type exists (not any cls == "N").
            val internodeType = schema.nodeTypes.firstOrNull {
                it.name.contains("internode", ignoreCase = true)
            }?.name

            return if (internodeType != null) {
                computeSoybean(root, schema, internodeType)
            } else {
                computeStemBranch(root, schema)
            }
        }

        /** Prefer a TraitRef literally named length; else hardcoded "length" (never first arbitrary ref). */
        internal fun lengthTraitKey(schema: TreeSchema): String {
            val stemOrBranch = schema.nodeTypes.filter { it.cls == "S" || it.cls == "B" }
            for (type in stemOrBranch) {
                type.traitRefs.firstOrNull { it.traitName.equals("length", ignoreCase = true) }
                    ?.let { return it.traitName }
            }
            return LENGTH_TRAIT
        }

        private fun computeSoybean(root: TreeNode, schema: TreeSchema, internodeType: String): TreeSummary {
            val podType = schema.summaryPodNodeType
                ?: schema.nodeTypes.firstOrNull { it.cls == "C" }?.name
            val podTrait = schema.summaryPodTraitName ?: "Seed count"

            var internodeCount = 0
            var podTotal = 0
            var branchCount = 0
            var maxDepth = 0

            allNodes(root).forEach { node ->
                val depth = pathTo(root, node.id).size
                maxDepth = max(maxDepth, depth)
                if (node.nodeType == internodeType) {
                    internodeCount++
                }
                if (podType != null && node.nodeType == podType) {
                    node.traits[podTrait]?.toIntOrNull()?.let { podTotal += it }
                }
                if (node.edge == EdgeType.BEARS && node.nodeType == internodeType) {
                    branchCount++
                }
            }

            val podsPerNode = if (internodeCount > 0) podTotal.toDouble() / internodeCount else 0.0
            return TreeSummary(
                nodeCount = internodeCount,
                podTotal = podTotal,
                branchCount = branchCount,
                maxOrder = maxDepth,
                podsPerNode = podsPerNode,
                usesLengthMetric = false,
            )
        }

        /** Root/stem/branch: count stems + BEARS branches; primary total = numeric length sum. */
        private fun computeStemBranch(root: TreeNode, schema: TreeSchema): TreeSummary {
            val stemType = schema.nodeTypes.firstOrNull { it.cls == "S" }?.name
                ?: schema.nodeTypes.firstOrNull { it.name.equals("stem", ignoreCase = true) }?.name
            val branchType = schema.nodeTypes.firstOrNull { it.cls == "B" }?.name
                ?: schema.nodeTypes.firstOrNull { it.name.equals("branch", ignoreCase = true) }?.name
            val lengthKey = lengthTraitKey(schema)

            var stemCount = 0
            var branchCount = 0
            var lengthTotal = 0
            var maxDepth = 0

            allNodes(root).forEach { node ->
                val depth = pathTo(root, node.id).size
                maxDepth = max(maxDepth, depth)
                if (stemType != null && node.nodeType == stemType) {
                    stemCount++
                }
                if (branchType != null && node.edge == EdgeType.BEARS && node.nodeType == branchType) {
                    branchCount++
                }
                val isLengthNode = (stemType != null && node.nodeType == stemType) ||
                    (branchType != null && node.nodeType == branchType)
                if (isLengthNode) {
                    parseLength(node.traits[lengthKey])?.let { lengthTotal += it }
                }
            }

            return TreeSummary(
                nodeCount = stemCount,
                podTotal = lengthTotal,
                branchCount = branchCount,
                maxOrder = maxDepth,
                podsPerNode = if (stemCount > 0) lengthTotal.toDouble() / stemCount else 0.0,
                usesLengthMetric = true,
            )
        }

        private fun parseLength(raw: String?): Int? {
            if (raw.isNullOrBlank() || raw.equals("NA", ignoreCase = true)) return null
            return raw.toDoubleOrNull()?.let { kotlin.math.round(it).toInt() } ?: raw.toIntOrNull()
        }
    }
}

object TreeFlattenExport {

    fun rows(obs: TreeObservation): List<List<String>> {
        val header = listOf(
            "unit", "node_id", "node_type", "node_path", "depth", "edge", "trait", "value", "timestamp",
        )
        val body = mutableListOf<List<String>>()
        flatten(obs.root).forEach { (node, depth) ->
            val path = if (depth == 0) "${node.cls}${node.idx}" else mtgPath(node, obs.root)
            val edge = if (depth == 0) "" else node.edge.symbol
            val ts = node.editedAt ?: node.createdAt
            if (node.traits.isEmpty()) {
                body += listOf(
                    obs.unit, node.id, node.nodeType, path, depth.toString(), edge, "", "", ts,
                )
            } else {
                node.traits.forEach { (trait, value) ->
                    body += listOf(
                        obs.unit, node.id, node.nodeType, path, depth.toString(), edge, trait,
                        exportCellValue(obs.trait, value),
                        ts,
                    )
                }
            }
        }
        return listOf(header) + body
    }

    /** Media-like values get trait-folder prefix; scalars (length, NA, dates) stay plain. */
    private fun exportCellValue(traitName: String, value: String): String {
        if (value.isBlank()) return value
        return if (com.fieldbook.tracker.utilities.TreePathPortability.looksLikeMediaRef(value)) {
            com.fieldbook.tracker.utilities.TreePathPortability.mediaRelative(traitName, value)
                .ifBlank { com.fieldbook.tracker.utilities.TreePathPortability.toRelative(value) }
        } else {
            value
        }
    }

    fun toCsv(obs: TreeObservation): String =
        rows(obs).joinToString("\n") { row -> row.joinToString(",") { escapeCsv(it) } }

    fun escapeCsv(value: String): String =
        if (value.contains(',') || value.contains('"') || value.contains('\n')) {
            "\"${value.replace("\"", "\"\"")}\""
        } else value
}
