package com.fieldbook.tracker.traits.formats.tree

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Owner test for 09 §3.1 residual soft-bad:
 * [NodeTraitField] / [CollectLayoutHost] must fail closed when
 * [com.fieldbook.tracker.traits.TraitLayoutFactory.create] returns null —
 * no soft `create("text")` mis-host of unknown / unhostable formats.
 */
class NodeTraitFieldUnknownHostFailClosedTest {

    private val source: String by lazy {
        File(
            "src/main/java/com/fieldbook/tracker/traits/composables/collect/NodeTraitField.kt",
        ).readText()
    }

    private val collectHost: String by lazy {
        source
            .substringAfter("private fun CollectLayoutHost(")
            .substringBefore("\nprivate data class HostState")
    }

    @Test
    fun collectLayoutHost_hasNoSoftTextCreateFallback() {
        assertFalse(
            "CollectLayoutHost must not soft-fall back unknown → create(\"text\")",
            collectHost.contains("""TraitLayoutFactory.create("text""""),
        )
        assertFalse(
            "CollectLayoutHost must not Elvis soft-create text",
            Regex("""\?:\s*TraitLayoutFactory\.create\(\s*"text"""").containsMatchIn(collectHost),
        )
    }

    @Test
    fun collectLayoutHost_failsClosedWhenCreateNull() {
        assertTrue(
            "CollectLayoutHost must call TraitLayoutFactory.create",
            collectHost.contains("TraitLayoutFactory.create"),
        )
        assertTrue(
            "null create must fail closed (controller == null), not inflate Text chrome",
            Regex("""controller\s*==\s*null""").containsMatchIn(collectHost) ||
                Regex("""if\s*\(\s*controller\s*==\s*null\s*\)""").containsMatchIn(collectHost),
        )
        assertTrue(
            "fail-closed path must surface tree_format_unsupported (no Text chrome)",
            collectHost.contains("tree_format_unsupported"),
        )
        assertFalse(
            "fail-closed path must not construct TextTraitLayout",
            Regex("""(?:new\s+)?TextTraitLayout\s*\(""").containsMatchIn(collectHost),
        )
    }

    @Test
    fun photoChromeHost_untouchedByFailClosedChange() {
        val photoHost = source
            .substringAfter("private fun PhotoChromeHost(")
            .substringBefore("\ninternal fun bindPhotoPreview(")
        assertTrue(photoHost.contains("inflate(R.layout.trait_tree_photo"))
        assertFalse(photoHost.contains("TraitLayoutFactory"))
        assertFalse(photoHost.contains("tree_format_unsupported"))
    }
}
