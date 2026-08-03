package com.fieldbook.tracker.traits.formats.tree

import com.fieldbook.tracker.objects.TraitObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TraitRefResolverUtilTest {

    @Test
    fun unresolvedTraitNames_reportsDistinctMissingRefs() {
        val schema = TreeSchema(
            id = "t",
            name = "Test",
            version = 1,
            rootType = "root",
            nodeTypes = listOf(
                NodeTypeDef(
                    name = "root",
                    displayName = "Root",
                    cls = "P",
                    traitRefs = listOf(
                        TraitRef("length", order = 0),
                        TraitRef("color", order = 1),
                    ),
                ),
                NodeTypeDef(
                    name = "branch",
                    displayName = "Branch",
                    cls = "B",
                    traitRefs = listOf(
                        TraitRef("length", order = 0),
                        TraitRef("Pod photo", order = 1),
                    ),
                ),
            ),
        )
        val resolver = TraitRefResolver { name ->
            if (name == "length") TraitObject().apply { this.name = name } else null
        }

        val missing = TraitRefResolverUtil.unresolvedTraitNames(schema, resolver)

        assertEquals(listOf("color", "Pod photo"), missing)
    }

    @Test
    fun unresolvedTraitNames_emptyWhenAllResolve() {
        val schema = TreeSchema(
            id = "t",
            name = "Test",
            version = 1,
            rootType = "root",
            nodeTypes = listOf(
                NodeTypeDef(
                    name = "root",
                    displayName = "Root",
                    cls = "P",
                    traitRefs = listOf(TraitRef("length", order = 0)),
                ),
            ),
        )
        val resolver = TraitRefResolver { name ->
            TraitObject().apply { this.name = name }
        }

        assertTrue(TraitRefResolverUtil.unresolvedTraitNames(schema, resolver).isEmpty())
    }
}
