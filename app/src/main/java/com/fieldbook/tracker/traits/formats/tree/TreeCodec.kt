package com.fieldbook.tracker.traits.formats.tree

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object TreeCodec {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = false
    }

    fun encodeSchema(schema: TreeSchema): String = json.encodeToString(schema)

    fun decodeSchema(raw: String): TreeSchema = json.decodeFromString(TreeSchema.serializer(), raw)

    fun encodeObservation(obs: TreeObservation): String = json.encodeToString(obs)

    fun decodeObservation(raw: String): TreeObservation = json.decodeFromString(TreeObservation.serializer(), raw)

    fun encodeSidecar(schemaId: String, pending: TreePending): String {
        val portableRoot = com.fieldbook.tracker.utilities.TreePathPortability.portableizeTree(
            pending.root,
            pending.traitName,
        )
        val obs = TreeObservation(
            schemaId = schemaId,
            unit = pending.unitId,
            trait = pending.traitName,
            rep = pending.rep,
            captured = pending.capturedAt,
            sourceApp = pending.sourceApp,
            mtg = encodeMtg(portableRoot),
            root = portableRoot,
        )
        return encodeObservation(obs)
    }

    fun encodeMtg(root: TreeNode): String = "/" + encodeNode(root)

    private fun encodeNode(node: TreeNode): String {
        val label = "${node.cls}${node.idx}"
        val bears = node.children.filter { it.edge == EdgeType.BEARS }
        val precedes = node.children.filter { it.edge == EdgeType.PRECEDES }
        val branchPart = bears.joinToString("") { child ->
            "[${child.edge.symbol}${encodeNode(child)}]"
        }
        val successorPart = when {
            precedes.isEmpty() -> ""
            precedes.size == 1 -> {
                val child = precedes.first()
                "${child.edge.symbol}${encodeNode(child)}"
            }
            // Multiple PRECEDES must not become N1<N2<N3 (OpenAlea linear axis).
            // Bracket each sibling group: N1[<N2][<N3]
            else -> precedes.joinToString("") { child ->
                "[${child.edge.symbol}${encodeNode(child)}]"
            }
        }
        return label + branchPart + successorPart
    }

    fun newRoot(schema: TreeSchema, createdAt: String): TreeNode =
        TreeMutations.newRoot(schema, createdAt)

    /** Build the OpenAlea poplar reference tree for encoder verification. */
    fun poplarReferenceRoot(): TreeNode {
        fun node(cls: String, idx: Int, edge: EdgeType = EdgeType.PRECEDES, children: List<TreeNode> = emptyList()) =
            TreeNode(
                id = "$cls$idx",
                nodeType = cls,
                cls = cls,
                idx = idx,
                edge = edge,
                createdAt = "1970-01-01T00:00:00Z",
                children = children.toMutableList(),
            )

        val i29 = node("I", 29)
        val i28 = node("I", 28, children = listOf(i29))
        val i27 = node("I", 27, children = listOf(i28))
        val i26 = node("I", 26, children = listOf(i27))
        val i25 = node("I", 25, children = listOf(i26))
        val i24 = node("I", 24, children = listOf(i25))
        val i23 = node("I", 23, children = listOf(i24))
        val i22 = node("I", 22, children = listOf(i23))
        val i21 = node("I", 21, children = listOf(i22))
        val i20 = node("I", 20, edge = EdgeType.BEARS, children = listOf(i21))

        val i19 = node("I", 19)
        val i18 = node("I", 18, children = listOf(i19))
        val i17 = node("I", 17, children = listOf(i18))
        val i16 = node("I", 16, children = listOf(i17))
        val i15 = node("I", 15, children = listOf(i16))
        val i14 = node("I", 14, children = listOf(i15))
        val i13 = node("I", 13, children = listOf(i14))
        val i12 = node("I", 12, children = listOf(i13))
        val i11 = node("I", 11, children = listOf(i12))
        val i10 = node("I", 10, children = listOf(i11))
        val i9 = node("I", 9, children = listOf(i10))
        val i8 = node("I", 8, children = listOf(i9))
        val i7 = node("I", 7, children = listOf(i8))

        val i6 = node("I", 6, children = listOf(i20, i7))
        val i5 = node("I", 5, children = listOf(i6))
        val i4 = node("I", 4, children = listOf(i5))
        val i3 = node("I", 3, children = listOf(i4))
        val i2 = node("I", 2, children = listOf(i3))
        return node("I", 1, children = listOf(i2))
    }
}

data class TreePending(
    val unitId: String,
    val studyId: String,
    val traitId: String,
    val traitName: String,
    val rep: String,
    var root: TreeNode,
    val capturedAt: String,
    val sourceApp: String,
    var existingUri: String? = null,
)
