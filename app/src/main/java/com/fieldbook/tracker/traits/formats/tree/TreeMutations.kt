package com.fieldbook.tracker.traits.formats.tree

import java.util.UUID

object TreeMutations {

    fun nextIndex(root: TreeNode, cls: String): Int =
        (allNodes(root).filter { it.cls == cls }.maxOfOrNull { it.idx } ?: 0) + 1

    fun newRoot(schema: TreeSchema, createdAt: String): TreeNode {
        val rootDef = schema.typeOf(schema.rootType)
            ?: error("Unknown root type ${schema.rootType}")
        return TreeNode(
            id = UUID.randomUUID().toString(),
            nodeType = rootDef.name,
            cls = rootDef.cls,
            idx = 1,
            edge = EdgeType.PRECEDES,
            createdAt = createdAt,
        )
    }

    fun addChild(
        root: TreeNode,
        parentId: String,
        rule: ChildRule,
        schema: TreeSchema,
        createdAt: String,
    ): Pair<TreeNode, String> {
        val parent = find(root, parentId) ?: error("Parent not found")
        val typeDef = schema.typeOf(parent.nodeType)
        val maxChildren = typeDef?.maxChildren
        if (maxChildren != null && parent.children.size >= maxChildren) {
            error("maxChildren ($maxChildren) reached for ${parent.nodeType}")
        }
        val childTypeDef = schema.typeOf(rule.nodeType) ?: error("Unknown node type ${rule.nodeType}")
        val newId = UUID.randomUUID().toString()
        val child = TreeNode(
            id = newId,
            nodeType = childTypeDef.name,
            cls = childTypeDef.cls,
            idx = nextIndex(root, childTypeDef.cls),
            edge = rule.edge,
            createdAt = createdAt,
        )
        val newRoot = deepCopy(root)
        val newParent = find(newRoot, parentId)!!
        newParent.children += child
        return newRoot to newId
    }

    fun deleteNode(root: TreeNode, nodeId: String): TreeNode {
        if (root.id == nodeId) return root
        val copy = deepCopy(root)
        val parent = parentOf(copy, nodeId) ?: return copy
        parent.children.removeAll { it.id == nodeId }
        return copy
    }

    fun setTrait(root: TreeNode, nodeId: String, traitName: String, value: String, editedAt: String): TreeNode {
        val copy = deepCopy(root)
        val node = find(copy, nodeId) ?: return copy
        node.traits[traitName] = value
        node.editedAt = editedAt
        return copy
    }

    fun moveNode(
        root: TreeNode,
        nodeId: String,
        newParentId: String,
        edge: EdgeType,
        schema: TreeSchema? = null,
    ): TreeNode {
        if (nodeId == newParentId) return root
        val copy = deepCopy(root)
        val node = find(copy, nodeId) ?: return copy
        // Reject cycles: new parent cannot be in the moved node's subtree.
        if (descendants(node).any { it.id == newParentId }) return copy
        val newParent = find(copy, newParentId) ?: return copy
        val oldParent = parentOf(copy, nodeId)
        val sameParent = oldParent?.id == newParentId
        if (schema != null) {
            val typeDef = schema.typeOf(newParent.nodeType)
            val maxChildren = typeDef?.maxChildren
            if (!sameParent && maxChildren != null && newParent.children.size >= maxChildren) {
                error("maxChildren ($maxChildren) reached for ${newParent.nodeType}")
            }
        }
        oldParent?.children?.removeAll { it.id == nodeId }
        node.edge = edge
        newParent.children += node
        return copy
    }

    fun deepCopy(node: TreeNode): TreeNode = TreeNode(
        id = node.id,
        nodeType = node.nodeType,
        cls = node.cls,
        idx = node.idx,
        edge = node.edge,
        createdAt = node.createdAt,
        editedAt = node.editedAt,
        traits = node.traits.toMutableMap(),
        children = node.children.map { deepCopy(it) }.toMutableList(),
    )
}
