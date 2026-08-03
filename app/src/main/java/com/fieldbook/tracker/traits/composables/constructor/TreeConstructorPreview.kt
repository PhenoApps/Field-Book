package com.fieldbook.tracker.traits.composables.constructor

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.tree.ChildRule
import com.fieldbook.tracker.traits.formats.tree.EdgeType
import com.fieldbook.tracker.traits.formats.tree.NodeTypeDef
import com.fieldbook.tracker.traits.formats.tree.TraitRef
import com.fieldbook.tracker.traits.formats.tree.TreeSchema

/**
 * Android Studio Compose Preview for Constructor (Blank root/stem/branch + study traits).
 * Open in Design/Split view. This is only the isolated Constructor panel, not a full Field Book screen.
 */
@Preview(showBackground = true, widthDp = 400, heightDp = 800, name = "TreeConstructor_blank_soy")
@Composable
private fun TreeConstructorBlankPreview() {
    val traits = previewStudyTraits()
    TreeConstructorScreen(
        initialSchema = previewUserSchema(),
        availableTraits = traits,
        traitNameHint = "soy tree-carrier",
        onSave = { _, _ -> },
        onCancel = {},
    )
}

internal fun previewStudyTraits(): List<TraitObject> = listOf(
    TraitObject().apply { name = "length"; format = "numeric" },
    TraitObject().apply { name = "color"; format = "text" },
    TraitObject().apply { name = "flowering date"; format = "date"; details = "yyyy-MM-dd" },
    TraitObject().apply { name = "branch photo"; format = "photo" },
)

internal fun previewUserSchema(): TreeSchema {
    val lengthColor = listOf(TraitRef("length", order = 0), TraitRef("color", order = 1))
    return TreeSchema(
        id = "soy_tree_carrier_v1",
        name = "soy tree-carrier",
        version = 1,
        rootType = "root",
        nodeTypes = listOf(
            NodeTypeDef(
                "root", "Root", "R",
                allowedChildren = listOf(ChildRule("stem", EdgeType.PRECEDES, "Add Stem")),
                traitRefs = lengthColor,
            ),
            NodeTypeDef(
                "stem", "Stem", "S",
                allowedChildren = listOf(
                    ChildRule("stem", EdgeType.PRECEDES, "Add Stem"),
                    ChildRule("branch", EdgeType.BEARS, "Add Branch"),
                ),
                traitRefs = lengthColor,
            ),
            NodeTypeDef(
                "branch", "Branch", "B",
                traitRefs = lengthColor + listOf(
                    TraitRef("flowering date", order = 2),
                    TraitRef("branch photo", order = 3),
                ),
            ),
        ),
    )
}
