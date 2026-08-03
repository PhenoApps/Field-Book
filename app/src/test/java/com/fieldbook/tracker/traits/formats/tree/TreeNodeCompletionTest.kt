package com.fieldbook.tracker.traits.formats.tree

import com.fieldbook.tracker.objects.TraitObject
import org.junit.Assert.assertEquals
import org.junit.Test

class TreeNodeCompletionTest {

    private val schema = TreeSchema(
        id = "t",
        name = "soy",
        version = 1,
        rootType = "root",
        nodeTypes = listOf(
            NodeTypeDef(
                name = "root",
                displayName = "Root",
                cls = "R",
                traitRefs = emptyList(),
            ),
            NodeTypeDef(
                name = "stem",
                displayName = "Stem",
                cls = "S",
                traitRefs = listOf(
                    TraitRef("length", order = 0),
                    TraitRef("color", order = 1),
                    TraitRef("flowering date", order = 2),
                    TraitRef("branch photo", order = 3),
                ),
            ),
            NodeTypeDef(
                name = "branch",
                displayName = "Branch",
                cls = "B",
                traitRefs = listOf(
                    TraitRef("length", order = 0),
                    TraitRef("color", order = 1),
                ),
            ),
        ),
    )

    @Test
    fun noTraits_isZeroOfZero() {
        val root = TreeNode(
            id = "r1",
            nodeType = "root",
            cls = "R",
            idx = 1,
            edge = EdgeType.PRECEDES,
            createdAt = "t",
        )
        assertEquals(NodeFill(0, 0), TreeNodeCompletion.compute(root, schema))
        assertEquals(TreeNodeCompletion.NodeShape.SQUARE, TreeNodeCompletion.shapeFor(root, schema))
    }

    @Test
    fun twoOfFour_filled() {
        val stem = TreeNode(
            id = "s1",
            nodeType = "stem",
            cls = "S",
            idx = 1,
            edge = EdgeType.PRECEDES,
            createdAt = "t",
            traits = mutableMapOf("length" to "12", "color" to "green"),
        )
        assertEquals(NodeFill(2, 4), TreeNodeCompletion.compute(stem, schema))
        assertEquals(TreeNodeCompletion.NodeShape.CIRCLE, TreeNodeCompletion.shapeFor(stem, schema))
    }

    @Test
    fun naCountsAsFilled() {
        val stem = TreeNode(
            id = "s1",
            nodeType = "stem",
            cls = "S",
            idx = 1,
            edge = EdgeType.PRECEDES,
            createdAt = "t",
            traits = mutableMapOf(
                "length" to "NA",
                "color" to "x",
                "flowering date" to "y",
                "branch photo" to "z",
            ),
        )
        assertEquals(NodeFill(4, 4), TreeNodeCompletion.compute(stem, schema))
    }

    @Test
    fun blankDoesNotCount() {
        val branch = TreeNode(
            id = "b1",
            nodeType = "branch",
            cls = "B",
            idx = 1,
            edge = EdgeType.BEARS,
            createdAt = "t",
            traits = mutableMapOf("length" to "5", "color" to "  "),
        )
        assertEquals(NodeFill(1, 2), TreeNodeCompletion.compute(branch, schema))
        assertEquals(TreeNodeCompletion.NodeShape.TRIANGLE, TreeNodeCompletion.shapeFor(branch, schema))
    }

    @Test
    fun resolveTraitAlias_usesLiveName() {
        val stem = TreeNode(
            id = "s1",
            nodeType = "stem",
            cls = "S",
            idx = 1,
            edge = EdgeType.PRECEDES,
            createdAt = "t",
            traits = mutableMapOf("Length Alias" to "10"),
        )
        val resolve: (String) -> TraitObject? = { name ->
            if (name == "length") TraitObject().apply { this.name = "Length Alias" } else null
        }
        assertEquals(NodeFill(1, 4), TreeNodeCompletion.compute(stem, schema, resolve))
    }
}
