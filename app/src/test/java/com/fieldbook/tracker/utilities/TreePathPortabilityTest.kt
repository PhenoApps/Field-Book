package com.fieldbook.tracker.utilities

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TreePathPortabilityTest {

    @Test
    fun mediaRelative_rewritesStaleFolderPrefix() {
        val relative = TreePathPortability.mediaRelative("NewTrait", "OldTrait/plot_node_1.jpg")
        assertEquals("NewTrait/plot_node_1.jpg", relative)
    }

    @Test
    fun mediaRelative_prefixesBareFilename() {
        assertEquals("Arch/file.jpg", TreePathPortability.mediaRelative("Arch", "file.jpg"))
    }

    @Test
    fun isRelative_rejectsAbsoluteFsPaths() {
        assertFalse(TreePathPortability.isRelative("/storage/emulated/0/x.json"))
        assertTrue(TreePathPortability.isRelative("Arch/x.json"))
    }

    @Test
    fun looksLikeMediaRef_ignoresDateLikeSlashes() {
        assertFalse(TreePathPortability.looksLikeMediaRef("2026/07/27"))
        assertTrue(TreePathPortability.looksLikeMediaRef("Arch/plot_node_1.jpg"))
    }

    @Test
    fun splitJoinMediaRefs_packsMultiShotInOneSidecarString() {
        val packed = TreePathPortability.joinMediaRefs(
            listOf("Arch/a_node_1.jpg", "Arch/b_node_2.jpg"),
        )
        assertEquals("Arch/a_node_1.jpg\nArch/b_node_2.jpg", packed)
        assertEquals(
            listOf("Arch/a_node_1.jpg", "Arch/b_node_2.jpg"),
            TreePathPortability.splitMediaRefs(packed),
        )
        assertTrue(TreePathPortability.looksLikeMediaRef(packed))
        assertEquals(
            listOf("Arch/only.jpg"),
            TreePathPortability.splitMediaRefs("Arch/only.jpg"),
        )
    }
}
