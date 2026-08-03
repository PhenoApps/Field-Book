package com.fieldbook.tracker.traits.formats.tree

fun find(root: TreeNode, id: String): TreeNode? {
    if (root.id == id) return root
    for (child in root.children) {
        find(child, id)?.let { return it }
    }
    return null
}

fun parentOf(root: TreeNode, id: String, parent: TreeNode? = null): TreeNode? {
    if (root.id == id) return parent
    for (child in root.children) {
        parentOf(child, id, root)?.let { return it }
    }
    return null
}

fun pathTo(root: TreeNode, id: String): List<TreeNode> {
    fun walk(node: TreeNode, trail: List<TreeNode>): List<TreeNode>? {
        val next = trail + node
        if (node.id == id) return next
        for (child in node.children) {
            walk(child, next)?.let { return it }
        }
        return null
    }
    return walk(root, emptyList()) ?: emptyList()
}

fun flatten(root: TreeNode): List<Pair<TreeNode, Int>> {
    val out = ArrayList<Pair<TreeNode, Int>>()
    fun walk(node: TreeNode, depth: Int) {
        out += node to depth
        node.children.forEach { walk(it, depth + 1) }
    }
    walk(root, 0)
    return out
}

fun descendants(node: TreeNode): Sequence<TreeNode> = sequence {
    val queue = ArrayDeque<TreeNode>()
    queue.addAll(node.children)
    while (queue.isNotEmpty()) {
        val next = queue.removeFirst()
        yield(next)
        queue.addAll(next.children)
    }
}

fun allNodes(root: TreeNode): Sequence<TreeNode> = sequence {
    yield(root)
    yieldAll(descendants(root))
}

fun mtgPath(node: TreeNode, root: TreeNode): String {
    val chain = pathTo(root, node.id)
    return chain.joinToString("/") { "${it.cls}${it.idx}" }
}
