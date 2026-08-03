package com.fieldbook.tracker.traits.composables.collect

import com.fieldbook.tracker.traits.formats.tree.TreeNode

/**
 * Breadcrumb display tokens for Collect (04 §3 A).
 *
 * Deep paths collapse the middle to a single ellipsis that opens Overview:
 * `P1 › … › B1 › N1` when [path] is longer than [maxVisible].
 */
sealed class BreadcrumbToken {
    data class Node(val node: TreeNode, val pathIndex: Int) : BreadcrumbToken()
    data object Ellipsis : BreadcrumbToken()
}

/**
 * @param maxVisible max node tokens shown (ellipsis does not count). Default 4:
 *   first + last three when collapsed, or all when path fits.
 */
fun collapseBreadcrumb(
    path: List<TreeNode>,
    maxVisible: Int = 4,
): List<BreadcrumbToken> {
    require(maxVisible >= 2) { "maxVisible must be ≥ 2 to keep first + current" }
    if (path.isEmpty()) return emptyList()
    if (path.size <= maxVisible) {
        return path.mapIndexed { i, n -> BreadcrumbToken.Node(n, i) }
    }
    val keepTail = maxVisible - 1 // first occupies one slot; rest are the tail
    val result = ArrayList<BreadcrumbToken>(maxVisible + 1)
    result.add(BreadcrumbToken.Node(path.first(), 0))
    result.add(BreadcrumbToken.Ellipsis)
    val start = path.size - keepTail
    for (i in start until path.size) {
        result.add(BreadcrumbToken.Node(path[i], i))
    }
    return result
}
