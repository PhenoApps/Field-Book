package com.fieldbook.tracker.traits.formats.tree

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeSchemaRenameTraitRefsTest {

    private val schema = TreeSchema(
        id = "t1",
        name = "Test",
        version = 1,
        rootType = "stem",
        nodeTypes = listOf(
            NodeTypeDef(
                name = "stem",
                displayName = "Stem",
                cls = "I",
                traitRefs = listOf(
                    TraitRef("Height", order = 0),
                    TraitRef("Color", order = 1),
                ),
            ),
            NodeTypeDef(
                name = "pod",
                displayName = "Pod",
                cls = "P",
                traitRefs = listOf(TraitRef("Seed count", requiredOverride = true, order = 0)),
            ),
        ),
        summaryPodTraitName = "Seed count",
    )

    @Test
    fun renameTraitRefs_rewritesMatchingRefsAndSummary() {
        val updated = schema.renameTraitRefs("Height", "Internode height")
        assertTrue(updated.referencesTraitName("Internode height"))
        assertFalse(updated.referencesTraitName("Height"))
        assertEquals("Height", schema.nodeTypes[0].traitRefs[0].traitName)
        assertEquals("Internode height", updated.nodeTypes[0].traitRefs[0].traitName)
        assertEquals("Color", updated.nodeTypes[0].traitRefs[1].traitName)

        val summaryUpdated = schema.renameTraitRefs("Seed count", "Pods")
        assertEquals("Pods", summaryUpdated.summaryPodTraitName)
        assertEquals("Pods", summaryUpdated.nodeTypes[1].traitRefs[0].traitName)
    }

    @Test
    fun renameTraitRefs_noOpWhenNamesEqualOrBlank() {
        assertEquals(schema, schema.renameTraitRefs("Height", "Height"))
        assertEquals(schema, schema.renameTraitRefs("", "x"))
    }

    @Test
    fun renameTraitRefs_caseInsensitiveMatch() {
        val updated = schema.renameTraitRefs("height", "Internode height")
        assertEquals("Internode height", updated.nodeTypes[0].traitRefs[0].traitName)
        assertTrue(updated.referencesTraitName("internode height"))
    }
}
