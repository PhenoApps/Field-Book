package com.fieldbook.tracker.utilities

import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.objects.TraitJson
import com.fieldbook.tracker.objects.toTraitJson
import org.junit.Assert.assertEquals
import org.junit.Test

class TreeDerivedTraitHelperTest {

    @Test
    fun createSummaryTrait_setsAliasToSummaryName() {
        val source = TraitObject().apply {
            id = "t1"
            name = "Архитектура сои"
            alias = name
        }

        val summary = TreeDerivedTraitHelper.createSummaryTrait(source, position = 5)

        assertEquals("Архитектура сои (summary)", summary.name)
        assertEquals(summary.name, summary.alias)
        assertEquals(listOf(summary.name), summary.synonyms)
        assertEquals(false, summary.visible)
    }

    @Test
    fun summaryTrait_roundTripsAdditionalInfoAndAlias() {
        val source = TraitObject().apply {
            id = "t1"
            name = "Архитектура сои"
            alias = name
        }

        val summary = TreeDerivedTraitHelper.createSummaryTrait(source, position = 5)
        val restored = TraitObject.fromJson(summary.toTraitJson(), maxPosition = 0, originalFileName = "traits.trt")

        assertEquals(summary.alias, restored.alias)
        assertEquals(summary.additionalInfo, restored.additionalInfo)
        // createSummaryTrait already sets visible=false; fromJson preserves JSON (no Formats clamp).
        assertEquals(false, restored.visible)
    }

    @Test
    fun coerceExportOnlySummaryVisibility_forcesInvisibleEvenIfJsonSaysVisible() {
        val restored = TraitObject.fromJson(
            TraitJson(
                name = "soy (summary)",
                format = "tree summary",
                visible = true,
            ),
            maxPosition = 0,
            originalFileName = "traits.trt",
        )
        assertEquals(true, restored.visible)
        TreeDerivedTraitHelper.coerceExportOnlySummaryVisibility(restored)
        assertEquals(false, restored.visible)
    }

    @Test
    fun coerceExportOnlySummaryVisibility_leavesNonSummaryAlone() {
        val trait = TraitObject().apply {
            format = "text"
            visible = true
        }
        TreeDerivedTraitHelper.coerceExportOnlySummaryVisibility(trait)
        assertEquals(true, trait.visible)
    }

    @Test
    fun fromJson_blankAliasFallsBackToName() {
        val restored = TraitObject.fromJson(
            TraitJson(
                name = "Архитектура сои (summary)",
                alias = "",
                format = "tree summary",
            ),
            maxPosition = 0,
            originalFileName = "traits.trt",
        )

        assertEquals("Архитектура сои (summary)", restored.alias)
    }

    @Test
    fun clearTreeLinkKeys_and_remapLinksAfterImport() {
        val source = TraitObject().apply {
            id = "old-source"
            name = "soy tree"
            format = "tree architecture"
            additionalInfo = """{"treeSummaryTraitId":"old-summary"}"""
        }
        val summary = TraitObject().apply {
            id = "old-summary"
            name = "soy tree (summary)"
            format = "tree summary"
            additionalInfo = """{"treeSourceTraitId":"old-source"}"""
        }
        // Simulate post-import new ids with stale additionalInfo
        source.id = "new-source"
        summary.id = "new-summary"
        source.additionalInfo = TreeDerivedTraitHelper.clearTreeLinkKeys(source.additionalInfo)
        assertEquals("", source.additionalInfo)

        val remapped = TreeDerivedTraitHelper.remapLinksAfterImport(listOf(source, summary))
        assertEquals(2, remapped.size)
        assertEquals("new-summary", TreeDerivedTraitHelper.summaryTraitId(source))
        assertEquals("new-source", TreeDerivedTraitHelper.sourceTraitId(summary))
    }

    @Test
    fun formatGuards_exportOnlySummaryAndCreateUsesTreeSummaryFormat() {
        val summary = TraitObject().apply {
            format = "tree summary"
        }
        assertEquals(true, TreeDerivedTraitHelper.isExportOnlySummary(summary))
        assertEquals(false, TreeDerivedTraitHelper.isExportOnlySummary(TraitObject().apply {
            format = "tree architecture"
        }))
        val created = TreeDerivedTraitHelper.createSummaryTrait(
            TraitObject().apply {
                id = "src"
                name = "Arch"
                format = "tree architecture"
            },
            position = 1,
        )
        assertEquals("tree summary", created.format)
        assertEquals(false, created.visible)
    }
}
