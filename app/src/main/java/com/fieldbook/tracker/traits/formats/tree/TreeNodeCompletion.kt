package com.fieldbook.tracker.traits.formats.tree

import com.fieldbook.tracker.objects.TraitObject

/**
 * How many of a node's schema [TraitRef]s already have a non-blank observation value.
 * Blank = empty; [NA](https://) and any other non-blank string count as filled.
 */
data class NodeFill(val filled: Int, val total: Int) {
    val fraction: Float get() = if (total <= 0) 0f else filled.toFloat() / total.toFloat()
}

object TreeNodeCompletion {

    fun compute(
        node: TreeNode,
        schema: TreeSchema,
        resolveTrait: (String) -> TraitObject? = { null },
    ): NodeFill {
        val refs = schema.typeOf(node.nodeType)?.traitRefs.orEmpty().sortedBy { it.order }
        if (refs.isEmpty()) return NodeFill(0, 0)
        var filled = 0
        for (ref in refs) {
            // Prefer live trait name if resolved (alias), else schema TraitRef name.
            val key = resolveTrait(ref.traitName)?.name ?: ref.traitName
            val value = node.traits[key] ?: node.traits[ref.traitName]
            if (!value.isNullOrBlank()) filled++
        }
        return NodeFill(filled = filled, total = refs.size)
    }

    /** Shape used in Overview graph: square root, circle stem, triangle branch. */
    enum class NodeShape { SQUARE, CIRCLE, TRIANGLE }

    fun shapeFor(node: TreeNode, schema: TreeSchema): NodeShape {
        val type = schema.typeOf(node.nodeType)
        val cls = type?.cls?.uppercase() ?: node.cls.uppercase()
        return when {
            node.nodeType == schema.rootType || cls == "R" || cls == "P" -> NodeShape.SQUARE
            cls == "B" -> NodeShape.TRIANGLE
            cls == "S" || cls == "N" || cls == "C" -> NodeShape.CIRCLE
            else -> NodeShape.CIRCLE
        }
    }
}
