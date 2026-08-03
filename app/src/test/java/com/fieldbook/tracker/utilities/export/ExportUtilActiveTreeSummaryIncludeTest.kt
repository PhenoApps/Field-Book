package com.fieldbook.tracker.utilities.export

import com.fieldbook.tracker.objects.TraitObject
import com.fieldbook.tracker.traits.formats.Formats
import com.fieldbook.tracker.utilities.TreeDerivedTraitHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Owner test for ExportUtil Active-traits + hidden TREE_SUMMARY include
 * (09 Residual-risk / watchlist #10 — Export pipeline).
 *
 * Contract: under Active traits, invisible `tree summary` rows ship iff their linked
 * tree-architecture source is already in the visible exportTrait set AND the summary
 * has at least one meaningful observation (not blank / "0").
 */
class ExportUtilActiveTreeSummaryIncludeTest {

    @Test
    fun sourceContract_activePathIncludesInvisibleTreeSummaryIffSourceExported() {
        val source = File(
            "src/main/java/com/fieldbook/tracker/utilities/export/ExportUtil.kt",
        ).readText()
        val activeBlock = source
            .substringAfter("if (isActiveTraitsChecked) {")
            .substringBefore("if (isAllTraitsChecked) {")

        assertTrue(
            "Active path must seed exportTrait from visible traits only",
            activeBlock.contains("if (t.visible)") && activeBlock.contains("exportTrait.add(t)"),
        )
        assertTrue(
            "Active path must consider invisible TREE_SUMMARY for linked include",
            activeBlock.contains("Formats.TREE_SUMMARY.getDatabaseName()") &&
                activeBlock.contains("!t.visible") &&
                activeBlock.contains("TreeDerivedTraitHelper.resolveSourceTrait"),
        )
        assertTrue(
            "Include only when linked architecture source is already in exportTrait",
            activeBlock.contains("exportedIds.contains(source.id)"),
        )
        assertTrue(
            "Include only when summary has meaningful observations",
            activeBlock.contains("hasMeaningfulSummaryObservations"),
        )
        val allTraitsBlock = source
            .substringAfter("if (isAllTraitsChecked) {")
            .substringBefore("checkDbBool = checkDB.isChecked")
        assertTrue(allTraitsBlock.contains("exportTrait.addAll("))
        assertTrue(
            "All-traits must filter empty TREE_SUMMARY companions",
            allTraitsBlock.contains("isExportOnlySummary") &&
                allTraitsBlock.contains("hasMeaningfulSummaryObservations"),
        )
    }

    @Test
    fun activeSelection_includesHiddenTreeSummary_whenArchitectureSourceExported() {
        val arch = trait("arch-1", "soy tree", Formats.TREE_ARCHITECTURE.getDatabaseName(), visible = true)
        val summary = TreeDerivedTraitHelper.createSummaryTrait(arch, position = 2).apply {
            id = "sum-1"
        }
        TreeDerivedTraitHelper.linkTraits(arch, summary)
        val height = trait("h-1", "height", "numeric", visible = true)

        val selected = selectActiveExportTraits(
            listOf(arch, summary, height),
            resolveSource = { id -> resolveSourceFrom(listOf(arch, summary, height), id) },
            hasMeaningfulSummary = { true },
        )

        assertEquals(listOf("arch-1", "h-1", "sum-1"), selected.map { it.id })
    }

    @Test
    fun activeSelection_omitsEmptyTreeSummary_evenWhenSourceExported() {
        val arch = trait("arch-1", "soy tree", Formats.TREE_ARCHITECTURE.getDatabaseName(), visible = true)
        val summary = TreeDerivedTraitHelper.createSummaryTrait(arch, position = 2).apply {
            id = "sum-1"
        }
        TreeDerivedTraitHelper.linkTraits(arch, summary)

        val selected = selectActiveExportTraits(
            listOf(arch, summary),
            resolveSource = { id -> resolveSourceFrom(listOf(arch, summary), id) },
            hasMeaningfulSummary = { false },
        )

        assertEquals(listOf("arch-1"), selected.map { it.id })
        assertFalse(selected.any { it.id == "sum-1" })
    }

    @Test
    fun activeSelection_omitsHiddenTreeSummary_whenArchitectureSourceNotExported() {
        val arch = trait("arch-1", "soy tree", Formats.TREE_ARCHITECTURE.getDatabaseName(), visible = false)
        val summary = TreeDerivedTraitHelper.createSummaryTrait(arch, position = 2).apply {
            id = "sum-1"
        }
        TreeDerivedTraitHelper.linkTraits(arch, summary)
        val height = trait("h-1", "height", "numeric", visible = true)

        val selected = selectActiveExportTraits(
            listOf(arch, summary, height),
            resolveSource = { id -> resolveSourceFrom(listOf(arch, summary, height), id) },
            hasMeaningfulSummary = { true },
        )

        assertEquals(listOf("h-1"), selected.map { it.id })
        assertFalse(selected.any { it.id == "sum-1" })
    }

    @Test
    fun activeSelection_nonTreeVisibleOnly_unchanged() {
        val traits = listOf(
            trait("t-1", "height", "numeric", visible = true),
            trait("t-2", "notes", "text", visible = false),
            trait("t-3", "date", "date", visible = true),
        )

        val selected = selectActiveExportTraits(
            traits,
            resolveSource = { id -> resolveSourceFrom(traits, id) },
            hasMeaningfulSummary = { true },
        )

        assertEquals(listOf("t-1", "t-3"), selected.map { it.id })
    }

    @Test
    fun shouldSkipBundledMediaLeaf_skipsMtgCompanionsAndNodesCsv() {
        assertTrue(TreeExportHelper.shouldSkipBundledMediaLeaf("x.mtg"))
        assertTrue(TreeExportHelper.shouldSkipBundledMediaLeaf("x.mtg.txt"))
        assertTrue(TreeExportHelper.shouldSkipBundledMediaLeaf("x.mtg (1).txt"))
        assertTrue(TreeExportHelper.shouldSkipBundledMediaLeaf("x.mtg (20).txt"))
        assertTrue(TreeExportHelper.shouldSkipBundledMediaLeaf("field_trait_nodes.csv"))
        assertFalse(TreeExportHelper.shouldSkipBundledMediaLeaf("plot_trait_2026.json"))
        assertFalse(TreeExportHelper.shouldSkipBundledMediaLeaf("plot_node.jpg"))
    }

    /**
     * Extractable pure form of ExportUtil's Active-traits exportTrait build
     * (visible seed + linked invisible TREE_SUMMARY include when meaningful).
     */
    internal fun selectActiveExportTraits(
        allTraits: List<TraitObject>,
        resolveSource: (summaryTraitId: String) -> TraitObject?,
        hasMeaningfulSummary: (summaryTraitId: String) -> Boolean,
    ): List<TraitObject> {
        val exportTrait = ArrayList<TraitObject>()
        for (t in allTraits) {
            if (t.visible) {
                exportTrait.add(t)
            }
        }
        val exportedIds = exportTrait.map { it.id }.toHashSet()
        val treeSummaryFormat = Formats.TREE_SUMMARY.getDatabaseName()
        for (t in allTraits) {
            if (!t.visible && t.format.equals(treeSummaryFormat, ignoreCase = true)) {
                val source = resolveSource(t.id)
                if (source != null && exportedIds.contains(source.id) &&
                    exportTrait.none { it.id == t.id } &&
                    hasMeaningfulSummary(t.id)
                ) {
                    exportTrait.add(t)
                }
            }
        }
        return exportTrait
    }

    /** Same guards as [TreeDerivedTraitHelper.resolveSourceTrait], over an in-memory list. */
    private fun resolveSourceFrom(traits: List<TraitObject>, summaryTraitId: String): TraitObject? {
        val summary = traits.firstOrNull { it.id == summaryTraitId } ?: return null
        if (!summary.format.equals(Formats.TREE_SUMMARY.getDatabaseName(), ignoreCase = true)) {
            return null
        }
        val sourceId = TreeDerivedTraitHelper.sourceTraitId(summary) ?: return null
        val source = traits.firstOrNull { it.id == sourceId } ?: return null
        if (!source.format.equals(Formats.TREE_ARCHITECTURE.getDatabaseName(), ignoreCase = true)) {
            return null
        }
        return source
    }

    private fun trait(
        id: String,
        name: String,
        format: String,
        visible: Boolean,
    ): TraitObject = TraitObject().apply {
        this.id = id
        this.name = name
        this.alias = name
        this.format = format
        this.visible = visible
    }
}
