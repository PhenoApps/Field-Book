package com.fieldbook.tracker.traits.formats.tree

import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.traits.composables.collect.BreadcrumbToken
import com.fieldbook.tracker.traits.composables.collect.TreeCollectStrings
import com.fieldbook.tracker.traits.composables.collect.collapseBreadcrumb
import com.fieldbook.tracker.traits.composables.collect.spokenNumber
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Collect chrome / a11y contracts that do not need a device:
 * breadcrumb collapse, TalkBack phrasing, soft-sound prefs, locked navigate-only rules.
 */
class TreeCollectChromeContractTest {

    @Test
    fun collapseBreadcrumb_shortPath_showsAllNodes() {
        val path = nodes("R", "S", "B")
        val tokens = collapseBreadcrumb(path, maxVisible = 4)
        assertEquals(3, tokens.size)
        assertTrue(tokens.none { it is BreadcrumbToken.Ellipsis })
        assertEquals(listOf(0, 1, 2), tokens.filterIsInstance<BreadcrumbToken.Node>().map { it.pathIndex })
    }

    @Test
    fun collapseBreadcrumb_deepPath_keepsFirstTailAndEllipsis() {
        val path = nodes("R", "S", "S", "S", "B", "N")
        val tokens = collapseBreadcrumb(path, maxVisible = 4)
        assertEquals(BreadcrumbToken.Node::class, tokens[0]::class)
        assertEquals(BreadcrumbToken.Ellipsis, tokens[1])
        val nodes = tokens.filterIsInstance<BreadcrumbToken.Node>()
        assertEquals(4, nodes.size)
        assertEquals(0, nodes.first().pathIndex)
        assertEquals(listOf(3, 4, 5), nodes.drop(1).map { it.pathIndex })
        assertEquals("R1", label(nodes[0].node))
        assertEquals("N1", label(nodes.last().node))
    }

    @Test
    fun childDescription_matchesA11ySpecShape() {
        val t = "2026-07-30T12:00:00Z"
        val parent = TreeNode(id = "p", nodeType = "stem", cls = "S", idx = 2, createdAt = t)
        val grandchild = TreeNode(id = "g", nodeType = "node", cls = "N", idx = 1, createdAt = t)
        val child = TreeNode(
            id = "c",
            nodeType = "branch",
            cls = "B",
            idx = 1,
            edge = EdgeType.BEARS,
            createdAt = t,
            children = mutableListOf(grandchild),
        )
        val desc = TreeCollectStrings(
            noIssues = "No issues",
            childrenTitle = { "Children ($it)" },
            overview = "Overview",
        ).childDescription(child, "Branch", 1, parent, 2)
        assertTrue(desc.contains("Branch one"))
        assertTrue(desc.contains("borne by S2"))
        assertTrue(desc.contains("1 child"))
        assertTrue(desc.contains("2 issues"))
    }

    @Test
    fun spokenNumber_coversOneThroughTen() {
        assertEquals("one", spokenNumber(1))
        assertEquals("ten", spokenNumber(10))
        assertEquals("11", spokenNumber(11))
    }

    @Test
    fun softSoundPreferenceKeys_matchCollectWiring() {
        // TreeTraitLayout.softAdvance / softDelete / maybeTts read these prefs.
        assertEquals("RangeSound", PreferenceKeys.PRIMARY_SOUND)
        assertEquals("DELETE_OBSERVATION_SOUND", PreferenceKeys.DELETE_OBSERVATION_SOUND)
        assertEquals("TTS_LANGUAGE_ENABLED", PreferenceKeys.TTS_LANGUAGE_ENABLED)
    }

    @Test
    fun lockedNavigateOnly_contract_disablesMutationsKeepsNavLabels() {
        // UI: Add buttons enabled=!locked; delete hidden when locked; ↑ / breadcrumb / Overview stay.
        val strings = TreeCollectStrings(
            noIssues = "No issues",
            childrenTitle = { "Children ($it)" },
            overview = "Overview",
        )
        assertEquals("Up", strings.ascend)
        assertEquals("Ascend one level", strings.ascendDescription)
        assertTrue(strings.lockedBanner.contains("navigate only", ignoreCase = true))
        assertFalse(strings.lockedBanner.contains("edit", ignoreCase = true))
    }

    private fun nodes(vararg cls: String): List<TreeNode> =
        cls.mapIndexed { i, c ->
            TreeNode(
                id = "n$i",
                nodeType = c.lowercase(),
                cls = c,
                idx = 1,
                createdAt = "2026-07-30T12:00:00Z",
            )
        }

    private fun label(n: TreeNode): String = "${n.cls}${n.idx}"
}
