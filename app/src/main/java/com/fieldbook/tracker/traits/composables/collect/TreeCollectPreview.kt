package com.fieldbook.tracker.traits.composables.collect

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.fieldbook.tracker.traits.composables.constructor.previewStudyTraits
import com.fieldbook.tracker.traits.composables.constructor.previewUserSchema
import com.fieldbook.tracker.traits.formats.tree.EdgeType
import com.fieldbook.tracker.traits.formats.tree.TreeMutations
import com.fieldbook.tracker.traits.formats.tree.TreeNode

/**
 * Compose Preview for Collect on sample1: R1 › S1 › S2 › B1 with flowering date.
 * Open in Android Studio Design view. This preview shows only the isolated tree collect panel,
 * not the surrounding Field Book collect screen or navigation chrome.
 */
@Preview(showBackground = true, widthDp = 400, heightDp = 720, name = "TreeCollect_branch_date")
@Composable
private fun TreeCollectBranchPreview() {
    val schema = previewUserSchema()
    val traits = previewStudyTraits()
    val root = remember { previewCollectedTree(schema) }
    val branchId = root.children[0].children[0].children.first { it.edge == EdgeType.BEARS }.id
    TreeCollectScreen(
        schema = schema,
        root = root,
        currentNodeId = branchId,
        issues = emptyList(),
        locked = false,
        onNavigate = {},
        onAddChild = {},
        onDeleteChild = {},
        onTraitChange = { _, _ -> },
        onRequestPhoto = {},
        onShowOverview = {},
        resolveTrait = { name -> traits.firstOrNull { it.name == name } },
        strings = TreeCollectStrings(
            noIssues = "No issues",
            childrenTitle = { "Children ($it)" },
            overview = "Overview",
            ascend = "Up",
        ),
    )
}

private fun previewCollectedTree(schema: com.fieldbook.tracker.traits.formats.tree.TreeSchema): TreeNode {
    val t = "2026-07-27T10:00:00Z"
    var root = TreeMutations.newRoot(schema, t)
    val stemRule = schema.typeOf("root")!!.allowedChildren.first()
    val nextStem = schema.typeOf("stem")!!.allowedChildren.first { it.edge == EdgeType.PRECEDES }
    val branchRule = schema.typeOf("stem")!!.allowedChildren.first { it.edge == EdgeType.BEARS }
    val (a, s1) = TreeMutations.addChild(root, root.id, stemRule, schema, t)
    root = a
    val (b, s2) = TreeMutations.addChild(root, s1, nextStem, schema, t)
    root = b
    val (c, _) = TreeMutations.addChild(root, s2, nextStem, schema, t)
    root = c
    val (d, b1) = TreeMutations.addChild(root, s2, branchRule, schema, t)
    root = d
    root = TreeMutations.setTrait(root, b1, "flowering date", "2026-07-27", t)
    root = TreeMutations.setTrait(root, b1, "length", "5", t)
    root = TreeMutations.setTrait(root, b1, "color", "yellow", t)
    return root
}
