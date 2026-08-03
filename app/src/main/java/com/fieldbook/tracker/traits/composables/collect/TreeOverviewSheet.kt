package com.fieldbook.tracker.traits.composables.collect

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.ui.dialogs.builder.AppAlertDialog
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.traits.formats.tree.EdgeType
import com.fieldbook.tracker.traits.formats.tree.Issue
import com.fieldbook.tracker.traits.formats.tree.NodeFill
import com.fieldbook.tracker.traits.formats.tree.TreeNode
import com.fieldbook.tracker.traits.formats.tree.TreeNodeCompletion
import com.fieldbook.tracker.traits.formats.tree.TreeSchema
import com.fieldbook.tracker.traits.formats.tree.TreeSummary
import com.fieldbook.tracker.traits.formats.tree.flatten

enum class OverviewMode { List, Graph }

/** Extra bottom inset so deepest rows clear the in-sheet summary footer. */
private val OverviewScrollBottomPad = 80.dp

/** Sheet content inset — keeps graph/list + summary off the screen edges. */
private val OverviewSheetPad = 16.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TreeOverviewSheet(
    root: TreeNode,
    schema: TreeSchema,
    currentNodeId: String,
    issues: List<Issue>,
    summary: TreeSummary,
    resolveTrait: (String) -> TraitObject?,
    onJumpTo: (String) -> Unit,
    initialMode: OverviewMode = OverviewMode.Graph,
    onModeChange: (OverviewMode) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val nodes = flatten(root)
    var historyNode by remember { mutableStateOf<TreeNode?>(null) }
    var mode by remember { mutableStateOf(initialMode) }
    var selectedIndex by remember { mutableIntStateOf(if (initialMode == OverviewMode.List) 0 else 1) }
    val context = LocalContext.current
    val savedColor = remember {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val argb = prefs.getInt(PreferenceKeys.SAVED_DATA_COLOR, 0xFF4CAF50.toInt())
        Color(argb or 0xFF000000.toInt())
    }
    val footer = when (mode) {
        OverviewMode.List -> stringResource(R.string.tree_overview_footer)
        OverviewMode.Graph -> stringResource(R.string.tree_overview_footer_graph)
    }
    val segmentedColors = SegmentedButtonDefaults.colors(
        activeContainerColor = AppTheme.colors.button.categoricalSelected,
        activeContentColor = AppTheme.colors.text.highContrast,
        inactiveContainerColor = AppTheme.colors.chip.defaultBackground,
        inactiveContentColor = AppTheme.colors.text.primary,
    )

    fun setMode(next: OverviewMode, index: Int) {
        selectedIndex = index
        mode = next
        onModeChange(next)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(OverviewSheetPad),
    ) {
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = selectedIndex == 0,
                onClick = { setMode(OverviewMode.List, 0) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                colors = segmentedColors,
                label = { Text("List") },
            )
            SegmentedButton(
                selected = selectedIndex == 1,
                onClick = { setMode(OverviewMode.Graph, 1) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                colors = segmentedColors,
                label = { Text("Graph") },
            )
        }

        when (mode) {
            OverviewMode.List -> {
                // Bounded by sheet height fraction; weight gives LazyColumn a finite viewport.
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    contentPadding = PaddingValues(bottom = OverviewScrollBottomPad),
                ) {
                    items(nodes) { (node, depth) ->
                        val fill = TreeNodeCompletion.compute(node, schema, resolveTrait)
                        val marker = if (node.id == currentNodeId) " ◀ HERE" else ""
                        val nodeIssues = issues.filter { it.nodeId == node.id }
                        val warn = if (nodeIssues.isNotEmpty()) {
                            " ⚠ ${nodeIssues.joinToString("; ") { it.message }}"
                        } else {
                            ""
                        }
                        val edge = when {
                            depth == 0 -> ""
                            node.edge == EdgeType.BEARS -> "+"
                            else -> "<"
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = { onJumpTo(node.id) },
                                    onLongClick = { historyNode = node },
                                )
                                .padding(start = (16 * depth).dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
                                .semantics {
                                    contentDescription =
                                        "Node ${node.cls}${node.idx}$marker. ${nodeIssues.joinToString { it.message }}"
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            CompletionPie(fill = fill, savedColor = savedColor)
                            Text(
                                text = "$edge ${node.cls}${node.idx}$marker$warn",
                                style = AppTheme.typography.bodyStyle,
                                color = AppTheme.colors.text.primary,
                                modifier = Modifier.weight(1f),
                            )
                            if (fill.total > 0) {
                                Text(
                                    text = "${fill.filled}/${fill.total}",
                                    style = AppTheme.typography.subheadingStyle,
                                    color = AppTheme.colors.text.secondary,
                                )
                            }
                        }
                    }
                }
            }
            OverviewMode.Graph -> {
                TreeOverviewGraph(
                    root = root,
                    schema = schema,
                    currentNodeId = currentNodeId,
                    resolveTrait = resolveTrait,
                    onJumpTo = onJumpTo,
                    issues = issues,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                )
            }
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = footer,
            style = AppTheme.typography.bodyStyle,
            color = AppTheme.colors.text.primary,
        )
        val totalLabel = if (summary.usesLengthMetric) "Length total" else "Pod total"
        Text(
            text = "Nodes ${summary.nodeCount} · $totalLabel ${summary.podTotal} · Branches ${summary.branchCount}",
            style = AppTheme.typography.bodyStyle,
            color = AppTheme.colors.text.primary,
            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
        )
    }

    historyNode?.let { node ->
        AppAlertDialog(
            title = "${node.cls}${node.idx}",
            content = {
                Column {
                    Text(
                        text = "Created: ${node.createdAt}",
                        style = AppTheme.typography.bodyStyle,
                        color = AppTheme.colors.text.primary,
                    )
                    Text(
                        text = "Edited: ${node.editedAt ?: "—"}",
                        style = AppTheme.typography.bodyStyle,
                        color = AppTheme.colors.text.primary,
                    )
                }
            },
            positiveButtonText = stringResource(android.R.string.ok),
            onPositive = { historyNode = null },
            onNegative = { historyNode = null },
        )
    }
}

@Composable
private fun CompletionPie(
    fill: NodeFill,
    savedColor: Color,
) {
    val emptyColor = AppTheme.colors.button.traitBackground
    val outlineColor = AppTheme.colors.text.secondary
    val total = fill.total.coerceAtLeast(0)
    Canvas(modifier = Modifier.size(22.dp)) {
        if (total == 0) {
            drawCircle(color = emptyColor, radius = size.minDimension / 2f)
            drawCircle(color = outlineColor, radius = size.minDimension / 2f, style = Stroke(width = 2f))
            return@Canvas
        }
        val sweep = 360f / total
        var start = -90f
        for (i in 0 until total) {
            val color = if (i < fill.filled) savedColor else emptyColor
            drawArc(
                color = color,
                startAngle = start,
                sweepAngle = sweep,
                useCenter = true,
                size = Size(size.width, size.height),
            )
            start += sweep
        }
        drawCircle(color = outlineColor, radius = size.minDimension / 2f, style = Stroke(width = 1.5f))
    }
}
