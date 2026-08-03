package com.fieldbook.tracker.traits.composables.collect

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.traits.composables.TreeActionButton
import com.fieldbook.tracker.traits.composables.TreeTextLink
import com.fieldbook.tracker.traits.formats.tree.ChildRule
import com.fieldbook.tracker.traits.formats.tree.EdgeType
import com.fieldbook.tracker.traits.formats.tree.Issue
import com.fieldbook.tracker.traits.formats.tree.TreeNode
import com.fieldbook.tracker.traits.formats.tree.TreeSchema
import com.fieldbook.tracker.traits.formats.tree.find
import com.fieldbook.tracker.traits.formats.tree.forTraitField
import com.fieldbook.tracker.traits.formats.tree.parentOf
import com.fieldbook.tracker.traits.formats.tree.pathTo
import com.fieldbook.tracker.ui.dialogs.builder.AppAlertDialog
import com.fieldbook.tracker.ui.theme.AppTheme

private val MinTouch = 56.dp

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TreeCollectScreen(
    schema: TreeSchema,
    root: TreeNode,
    currentNodeId: String,
    issues: List<Issue>,
    locked: Boolean,
    onNavigate: (String) -> Unit,
    onAddChild: (ChildRule) -> Unit,
    onDeleteChild: (String) -> Unit,
    onTraitChange: (String, String) -> Unit,
    onRequestPhoto: (String) -> Unit,
    onRequestPhotoCropSettings: (String) -> Unit = {},
    onShowOverview: () -> Unit,
    resolveTrait: (String) -> com.fieldbook.tracker.objects.TraitObject?,
    strings: TreeCollectStrings,
    /** When true, lay out full scrollable height (for Roborazzi full-length screenshots). */
    expandVertically: Boolean = false,
) {
    val current = pathTo(root, currentNodeId).lastOrNull() ?: root
    val breadcrumb = pathTo(root, currentNodeId)
    val tokens = remember(breadcrumb) { collapseBreadcrumb(breadcrumb) }
    val typeDef = schema.typeOf(current.nodeType)
    val blocking = issues.count { it is Issue.MissingRequired }
    val issueCount = issues.size
    val badgeColor = when {
        blocking > 0 -> AppTheme.colors.status.error
        issueCount > 0 -> TreeFieldWarningColor
        else -> AppTheme.colors.text.primary
    }
    var pendingDeleteId by remember { mutableStateOf<String?>(null) }
    val parent = parentOf(root, currentNodeId)
    val atMaxChildren = typeDef?.maxChildren?.let { current.children.size >= it } == true
    val canHaveChildren = !typeDef?.allowedChildren.isNullOrEmpty()
    val precedesColor = AppTheme.colors.primary
    val bearsColor = AppTheme.colors.accent
    val breadcrumbScroll = rememberScrollState()

    LaunchedEffect(currentNodeId, tokens.size) {
        breadcrumbScroll.animateScrollTo(breadcrumbScroll.maxValue)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (expandVertically) Modifier else Modifier.verticalScroll(rememberScrollState()),
            )
            .padding(8.dp)
            .semantics { contentDescription = "Tree collection" },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (locked) {
            Text(
                text = strings.lockedBanner,
                style = AppTheme.typography.bodyStyle,
                color = AppTheme.colors.status.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = strings.lockedBanner },
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = MinTouch)
                .padding(horizontal = 8.dp)
                .semantics { traversalIndex = 0f },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (parent != null) {
                TreeTextLink(
                    text = "↑ ${strings.ascend}",
                    onClick = { onNavigate(parent.id) },
                    modifier = Modifier
                        .defaultMinSize(minWidth = MinTouch, minHeight = MinTouch)
                        .semantics {
                            contentDescription = strings.ascendDescription
                        },
                )
            }
            val ellipsisIndex = tokens.indexOfFirst { it is BreadcrumbToken.Ellipsis }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (ellipsisIndex >= 0) {
                    // Pin root + ellipsis so collapse stays visible after scroll-to-end.
                    tokens.take(ellipsisIndex + 1).forEachIndexed { i, token ->
                        BreadcrumbTokenLabel(
                            token = token,
                            displayIndex = i,
                            schema = schema,
                            strings = strings,
                            onNavigate = onNavigate,
                            onShowOverview = onShowOverview,
                        )
                    }
                    Row(
                        modifier = Modifier.horizontalScroll(breadcrumbScroll),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tokens.drop(ellipsisIndex + 1).forEachIndexed { i, token ->
                            BreadcrumbTokenLabel(
                                token = token,
                                displayIndex = ellipsisIndex + 1 + i,
                                schema = schema,
                                strings = strings,
                                onNavigate = onNavigate,
                                onShowOverview = onShowOverview,
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.horizontalScroll(breadcrumbScroll),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tokens.forEachIndexed { i, token ->
                            BreadcrumbTokenLabel(
                                token = token,
                                displayIndex = i,
                                schema = schema,
                                strings = strings,
                                onNavigate = onNavigate,
                                onShowOverview = onShowOverview,
                            )
                        }
                    }
                }
            }
            Text(
                text = if (issueCount > 0) "⚠ $issueCount" else strings.noIssues,
                color = badgeColor,
                modifier = Modifier
                    .defaultMinSize(minWidth = MinTouch, minHeight = MinTouch)
                    .clickable(enabled = issueCount > 0, onClick = onShowOverview)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .semantics {
                        contentDescription = strings.issueBadgeDescription(issueCount, blocking)
                    },
            )
        }

        // Always immediately under the breadcrumb row (not mixed with Add-child actions).
        TreeActionButton(
            text = strings.overview,
            onClick = onShowOverview,
            modifier = Modifier
                .fillMaxWidth()
                .semantics {
                    traversalIndex = 0.5f
                    contentDescription = strings.overview
                },
        )

        Column(modifier = Modifier.semantics { traversalIndex = 1f }) {
            typeDef?.traitRefs?.sortedBy { it.order }?.forEach { ref ->
                NodeTraitField(
                    traitRef = ref,
                    trait = resolveTrait(ref.traitName),
                    value = current.traits[ref.traitName].orEmpty(),
                    locked = locked,
                    nodeId = current.id,
                    onValueChange = { onTraitChange(ref.traitName, it) },
                    onRequestPhoto = { onRequestPhoto(ref.traitName) },
                    onRequestPhotoCropSettings = { onRequestPhotoCropSettings(ref.traitName) },
                    fieldIssues = issues.forTraitField(current.id, ref.traitName),
                )
            }
        }

        if (canHaveChildren || current.children.isNotEmpty()) {
            Text(
                text = strings.childrenTitle(current.children.size),
                style = AppTheme.typography.bodyStyle,
                color = AppTheme.colors.text.primary,
            )
            Column(modifier = Modifier.semantics { traversalIndex = 2f }) {
                current.children.forEachIndexed { position, child ->
                    val edgeColor = if (child.edge == EdgeType.BEARS) bearsColor else precedesColor
                    val typeLabel = schema.typeOf(child.nodeType)?.displayName ?: child.nodeType
                    val childIssues = issues.count { it.nodeId == child.id }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = MinTouch)
                            .clickable { onNavigate(child.id) }
                            .padding(vertical = 8.dp)
                            .semantics {
                                contentDescription = strings.childDescription(
                                    child,
                                    typeLabel,
                                    position + 1,
                                    current,
                                    childIssues,
                                )
                            },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = child.edge.symbol,
                            color = edgeColor,
                            style = AppTheme.typography.titleStyle,
                            modifier = Modifier.padding(end = 8.dp),
                        )
                        // Existing children: type display name only — never ChildRule.label
                        // ("Add Branch"). Keep "Add …" on the separate FlowRow add buttons.
                        Text(
                            text = "${child.cls}${child.idx} $typeLabel",
                            style = AppTheme.typography.bodyStyle,
                            color = AppTheme.colors.text.primary,
                            modifier = Modifier.weight(1f),
                        )
                        if (!locked) {
                            TreeTextLink(
                                text = strings.deleteChild,
                                onClick = {
                                    val victim = find(root, child.id)
                                    if (victim != null && victim.children.isNotEmpty()) {
                                        pendingDeleteId = child.id
                                    } else {
                                        onDeleteChild(child.id)
                                    }
                                },
                                modifier = Modifier.semantics {
                                    contentDescription =
                                        "Delete ${strings.childDescription(child, typeLabel, position + 1, current, childIssues)}"
                                },
                            )
                        }
                    }
                }
            }
        }

        val addRules = typeDef?.allowedChildren.orEmpty()
        if (addRules.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { traversalIndex = 3f },
            ) {
                addRules.forEach { rule ->
                    TreeActionButton(
                        text = "${rule.edge.symbol} ${rule.label}",
                        onClick = { onAddChild(rule) },
                        enabled = !locked && !atMaxChildren,
                        modifier = Modifier.semantics {
                            contentDescription =
                                "Add ${rule.label}, ${rule.edge.name.lowercase()} relation"
                        },
                    )
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        val victim = current.children.firstOrNull { it.id == id }
        val hasSubtree = victim?.children?.isNotEmpty() == true
        AppAlertDialog(
            title = if (hasSubtree) {
                strings.confirmDeleteSubtreeTitle
            } else {
                strings.deleteChild
            },
            content = {
                Text(
                    text = if (hasSubtree) {
                        strings.confirmDeleteSubtreeMessage
                    } else {
                        strings.confirmDelete
                    },
                    style = AppTheme.typography.bodyStyle,
                    color = AppTheme.colors.text.primary,
                )
            },
            positiveButtonText = stringResource(R.string.delete),
            positiveTextColor = AppTheme.colors.status.error,
            onPositive = {
                onDeleteChild(id)
                pendingDeleteId = null
            },
            negativeButtonText = stringResource(R.string.cancel),
            onNegative = { pendingDeleteId = null },
        )
    }
}

@Composable
private fun BreadcrumbTokenLabel(
    token: BreadcrumbToken,
    displayIndex: Int,
    schema: TreeSchema,
    strings: TreeCollectStrings,
    onNavigate: (String) -> Unit,
    onShowOverview: () -> Unit,
) {
    when (token) {
        is BreadcrumbToken.Ellipsis -> {
            Text(
                text = if (displayIndex == 0) "…" else " › …",
                color = AppTheme.colors.primary,
                modifier = Modifier
                    .defaultMinSize(minWidth = MinTouch, minHeight = MinTouch)
                    .clickable(onClick = onShowOverview)
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .semantics {
                        contentDescription = strings.breadcrumbEllipsisDescription
                    },
            )
        }
        is BreadcrumbToken.Node -> {
            val node = token.node
            val typeLabel = schema.typeOf(node.nodeType)?.displayName ?: node.nodeType
            val label = "${node.cls}${node.idx}"
            val prefix = if (displayIndex == 0) "" else " › "
            Text(
                text = "$prefix$label",
                color = AppTheme.colors.primary,
                modifier = Modifier
                    .defaultMinSize(minWidth = MinTouch, minHeight = MinTouch)
                    .clickable { onNavigate(node.id) }
                    .padding(horizontal = 8.dp, vertical = 12.dp)
                    .semantics {
                        contentDescription =
                            strings.breadcrumbDescription(
                                typeLabel,
                                node.idx,
                                token.pathIndex,
                            )
                    },
            )
        }
    }
}

data class TreeCollectStrings(
    val noIssues: String,
    val childrenTitle: (Int) -> String,
    val overview: String,
    val missingSchema: String = "Attach a tree schema before collecting.",
    val deleteChild: String = "Delete",
    val confirmDelete: String = "Delete this node and all of its descendants?",
    val confirmDeleteSubtreeTitle: String = "Delete subtree?",
    val confirmDeleteSubtreeMessage: String =
        "This node has children. Deleting it removes the entire subtree and any photos under those nodes.",
    val ascend: String = "Up",
    val ascendDescription: String = "Ascend one level",
    val lockedBanner: String = "Observation locked — navigate only",
    val breadcrumbEllipsisDescription: String = "Hidden ancestors, open overview",
    val breadcrumbDescription: (typeLabel: String, index: Int, depth: Int) -> String =
        { type, idx, depth ->
            "Navigate to $type ${spokenNumber(idx)}, level ${depth + 1}"
        },
    val issueBadgeDescription: (total: Int, blocking: Int) -> String = { total, blocking ->
        when {
            total == 0 -> "No issues"
            blocking > 0 -> "$total issues, $blocking required missing, open overview"
            else -> "$total warnings, open overview"
        }
    },
    val childDescription: (
        TreeNode,
        String,
        Int,
        TreeNode,
        Int,
    ) -> String = { node, typeLabel, position, parent, issueCount ->
        val relation = if (node.edge == EdgeType.BEARS) "borne by" else "precedes from"
        val parentLabel = "${parent.cls}${parent.idx}"
        val childCount = node.children.size
        val issuesPart = when (issueCount) {
            0 -> "no issues"
            1 -> "1 issue"
            else -> "$issueCount issues"
        }
        val kidsPart = when (childCount) {
            0 -> "no children"
            1 -> "1 child"
            else -> "$childCount children"
        }
        "$typeLabel ${spokenNumber(node.idx)}, $relation $parentLabel, position $position, $kidsPart, $issuesPart"
    },
)

internal fun spokenNumber(n: Int): String = when (n) {
    1 -> "one"
    2 -> "two"
    3 -> "three"
    4 -> "four"
    5 -> "five"
    6 -> "six"
    7 -> "seven"
    8 -> "eight"
    9 -> "nine"
    10 -> "ten"
    else -> n.toString()
}
