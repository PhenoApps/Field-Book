package com.fieldbook.tracker.traits.composables.collect

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.tree.Issue
import com.fieldbook.tracker.traits.formats.tree.NodeFill
import com.fieldbook.tracker.traits.formats.tree.TreeNode
import com.fieldbook.tracker.traits.formats.tree.TreeNodeCompletion
import com.fieldbook.tracker.traits.formats.tree.TreeSchema
import com.otaliastudios.zoom.ZoomLayout
import dev.bandb.graphview.AbstractGraphAdapter
import dev.bandb.graphview.graph.Graph
import dev.bandb.graphview.graph.Node
import dev.bandb.graphview.layouts.tree.BuchheimWalkerConfiguration
import dev.bandb.graphview.layouts.tree.BuchheimWalkerLayoutManager
import dev.bandb.graphview.layouts.tree.TreeEdgeDecoration

data class TreeGraphNodeData(
    val treeNode: TreeNode,
    val label: String,
    val shape: TreeNodeCompletion.NodeShape,
    val fill: NodeFill,
    val isCurrent: Boolean,
    val issueText: String = "",
)

/** Stable key for parent/child structure (ignores trait values and current selection). */
fun treeTopologySignature(root: TreeNode): String {
    val sb = StringBuilder()
    fun walk(node: TreeNode) {
        sb.append(node.id).append(':').append(node.cls).append(node.idx).append('{')
        node.children.forEachIndexed { i, child ->
            if (i > 0) sb.append(',')
            walk(child)
        }
        sb.append('}')
    }
    walk(root)
    return sb.toString()
}

/**
 * Default overview growth: roots/stem at the bottom, children upward
 * (plant-intuitive). List|Graph mode toggle is separate; there is no
 * direction toggle — only this default.
 */
val TreeOverviewDefaultOrientation: Int =
    BuchheimWalkerConfiguration.ORIENTATION_BOTTOM_TOP

/**
 * Overview graph: GraphView Buchheim–Walker + ZoomLayout (library-advised config).
 * AndroidView remounts only when [treeTopologySignature] changes; highlight/fill
 * updates go through [update] without tearing down ZoomLayout.
 */
@Composable
fun TreeOverviewGraph(
    root: TreeNode,
    schema: TreeSchema,
    currentNodeId: String,
    resolveTrait: (String) -> TraitObject?,
    onJumpTo: (String) -> Unit,
    issues: List<Issue> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val topology = remember(root) { treeTopologySignature(root) }
    key(topology) {
        AndroidView(
            modifier = modifier
                .fillMaxWidth()
                .fillMaxSize(),
            factory = { context ->
                LayoutInflater.from(context).inflate(R.layout.tree_overview_graph, null, false).also { host ->
                    bindTreeOverviewGraph(
                        host = host,
                        root = root,
                        schema = schema,
                        currentNodeId = currentNodeId,
                        resolveTrait = resolveTrait,
                        onJumpTo = onJumpTo,
                        issues = issues,
                    )
                }
            },
            update = { host ->
                // Topology unchanged (keyed above): refresh node data only via submitGraph.
                submitTreeOverviewGraph(
                    host = host,
                    root = root,
                    schema = schema,
                    currentNodeId = currentNodeId,
                    resolveTrait = resolveTrait,
                    onJumpTo = onJumpTo,
                    issues = issues,
                )
            },
        )
    }
}

fun bindTreeOverviewGraph(
    host: View,
    root: TreeNode,
    schema: TreeSchema,
    currentNodeId: String,
    resolveTrait: (String) -> TraitObject?,
    onJumpTo: (String) -> Unit,
    issues: List<Issue> = emptyList(),
) {
    ensureTreeOverviewGraphLayout(host)
    submitTreeOverviewGraph(host, root, schema, currentNodeId, resolveTrait, onJumpTo, issues)
}

private fun ensureTreeOverviewGraphLayout(host: View) {
    val recycler = host.findViewById<RecyclerView>(R.id.tree_overview_graph_rv)
    if (recycler.layoutManager is BuchheimWalkerLayoutManager) return

    val configuration = BuchheimWalkerConfiguration.Builder()
        .setSiblingSeparation(100)
        .setLevelSeparation(100)
        .setSubtreeSeparation(100)
        .setOrientation(TreeOverviewDefaultOrientation)
        .build()
    recycler.layoutManager = BuchheimWalkerLayoutManager(host.context, configuration).apply {
        useMaxSize = true
    }
    val edgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            3f,
            host.resources.displayMetrics,
        )
        color = resolveOutline(host.context)
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    recycler.addItemDecoration(TreeEdgeDecoration(edgePaint))
}

@Suppress("UNCHECKED_CAST")
fun submitTreeOverviewGraph(
    host: View,
    root: TreeNode,
    schema: TreeSchema,
    currentNodeId: String,
    resolveTrait: (String) -> TraitObject?,
    onJumpTo: (String) -> Unit,
    issues: List<Issue> = emptyList(),
) {
    val recycler = host.findViewById<RecyclerView>(R.id.tree_overview_graph_rv)
    val zoom = host.findViewById<ZoomLayout>(R.id.tree_overview_zoom)
    host.setTag(R.id.tree_overview_graph_rv, onJumpTo)

    val graph = buildGraph(root, schema, currentNodeId, resolveTrait, issues)
    val existing = recycler.adapter as? AbstractGraphAdapter<NodeVH>
    val adapter = existing ?: object : AbstractGraphAdapter<NodeVH>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NodeVH {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.tree_overview_graph_node, parent, false)
            return NodeVH(view)
        }

        override fun onBindViewHolder(holder: NodeVH, position: Int) {
                        val data = getNodeData(position) as TreeGraphNodeData
            val displayLabel = buildString {
                append(data.label)
                if (data.isCurrent) append(" HERE")
                if (data.issueText.isNotEmpty()) append(" ⚠")
            }
            holder.glyph.bind(displayLabel, data.shape, data.fill, data.isCurrent)
            val here = if (data.isCurrent) " HERE" else ""
            val warn = if (data.issueText.isNotEmpty()) " ⚠ ${data.issueText}" else ""
            holder.itemView.contentDescription = "${data.label}$here$warn"
            holder.itemView.setOnClickListener {
                @Suppress("UNCHECKED_CAST")
                val jump = host.getTag(R.id.tree_overview_graph_rv) as? (String) -> Unit
                jump?.invoke(data.treeNode.id)
            }
        }
    }
    if (existing == null) {
        recycler.adapter = adapter
    }
    adapter.submitGraph(graph)
    zoom.setHasClickableChildren(true)
}

private class NodeVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
    val glyph: TreeNodeGlyphView = itemView.findViewById(R.id.tree_node_glyph)
}

fun buildGraph(
    root: TreeNode,
    schema: TreeSchema,
    currentNodeId: String,
    resolveTrait: (String) -> TraitObject?,
    issues: List<Issue> = emptyList(),
): Graph {
    val graph = Graph()
    val map = LinkedHashMap<String, Node>()
    val issuesByNode = issues.groupBy { it.nodeId }

    fun visit(treeNode: TreeNode) {
        val nodeIssues = issuesByNode[treeNode.id].orEmpty()
        val data = TreeGraphNodeData(
            treeNode = treeNode,
            label = "${treeNode.cls}${treeNode.idx}",
            shape = TreeNodeCompletion.shapeFor(treeNode, schema),
            fill = TreeNodeCompletion.compute(treeNode, schema, resolveTrait),
            isCurrent = treeNode.id == currentNodeId,
            issueText = nodeIssues.joinToString("; ") { it.message },
        )
        val gNode = Node(data)
        map[treeNode.id] = gNode
        graph.addNode(gNode)
        treeNode.children.forEach { child ->
            visit(child)
            graph.addEdge(gNode, map.getValue(child.id))
        }
    }
    visit(root)
    return graph
}

private fun resolveOutline(context: Context): Int {
    val tv = TypedValue()
    return if (context.theme.resolveAttribute(R.attr.fb_color_text_dark, tv, true)) {
        if (tv.resourceId != 0) ContextCompat.getColor(context, tv.resourceId) else tv.data
    } else {
        Color.DKGRAY
    }
}
