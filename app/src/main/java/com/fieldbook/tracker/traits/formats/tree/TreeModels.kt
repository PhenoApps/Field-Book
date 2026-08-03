package com.fieldbook.tracker.traits.formats.tree

import kotlinx.serialization.Serializable

@Serializable
data class TreeObservation(
    val v: Int = 1,
    val schemaId: String,
    val unit: String,
    val trait: String,
    val rep: String,
    val captured: String,
    val sourceApp: String,
    val mtg: String,
    val root: TreeNode,
)

@Serializable
data class TreeNode(
    val id: String,
    val nodeType: String,
    val cls: String,
    var idx: Int,
    var edge: EdgeType = EdgeType.PRECEDES,
    val createdAt: String,
    var editedAt: String? = null,
    val traits: MutableMap<String, String> = mutableMapOf(),
    val children: MutableList<TreeNode> = mutableListOf(),
)

@Serializable
enum class EdgeType(val symbol: String) {
    PRECEDES("<"),
    BEARS("+"),
}

sealed class Issue(val nodeId: String, open val message: String) {
    /** Study trait name when this issue is about a TraitRef value; null for structural issues. */
    open val traitName: String? get() = null

    class MissingRequired(nodeId: String, override val traitName: String) :
        Issue(nodeId, "Missing: $traitName")

    class OutOfRange(nodeId: String, override val traitName: String, value: String) :
        Issue(nodeId, "$traitName out of range: $value")

    class BadCategory(nodeId: String, override val traitName: String, value: String) :
        Issue(nodeId, "$traitName invalid: $value")

    class NotNumeric(nodeId: String, override val traitName: String) :
        Issue(nodeId, "$traitName must be a number")

    class InvalidValue(nodeId: String, override val traitName: String, value: String) :
        Issue(nodeId, "$traitName invalid: $value")

    class IllegalChild(nodeId: String, child: String) :
        Issue(nodeId, "Child not allowed: $child")

    class IllegalEdge(nodeId: String, child: String) :
        Issue(nodeId, "Wrong relation for $child")

    class TooManyChildren(nodeId: String, max: Int) :
        Issue(nodeId, "Max $max children")

    class UnknownType(nodeId: String, type: String) :
        Issue(nodeId, "Unknown type: $type")

    class DuplicateSiblingIndex(nodeId: String, idx: Int) :
        Issue(nodeId, "Duplicate index $idx")

    /** Blocking field feedback (red) — missing required TraitRef. */
    fun isFieldBlocking(): Boolean = this is MissingRequired

    /** Non-blocking field feedback (amber) — range / type / category. */
    fun isFieldWarning(): Boolean =
        this is OutOfRange || this is BadCategory || this is NotNumeric || this is InvalidValue
}

/** Issues for one TraitRef on a node (field-level Collect coloring). */
fun List<Issue>.forTraitField(nodeId: String, traitName: String): List<Issue> =
    filter { it.nodeId == nodeId && it.traitName == traitName }
