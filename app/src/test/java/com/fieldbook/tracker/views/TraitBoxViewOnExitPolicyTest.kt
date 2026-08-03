package com.fieldbook.tracker.views

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Owner test for [TraitBoxView] previous-format [BaseTraitLayout.onExit]
 * (09 Residual-risk / watchlist #4).
 *
 * Contract in [TraitBoxView.loadLayout]:
 * - Format change → previous layout `onExit()` before `inflateTrait`
 * - Same format (e.g. plot move) → `onRefresh()` only; no `onExit`
 */
class TraitBoxViewOnExitPolicyTest {

    private val source: String by lazy {
        File("src/main/java/com/fieldbook/tracker/views/TraitBoxView.kt").readText()
    }

    private val loadLayoutBody: String by lazy {
        source
            .substringAfter("fun loadLayout(skipSelection: Boolean = false)")
            .substringBefore("private fun showTraitPickerDialog")
    }

    private fun sameFormatBranch(): String {
        assertTrue(
            "loadLayout must gate on lastInflatedFormat equality",
            loadLayoutBody.contains("currentFormat == lastInflatedFormat"),
        )
        return loadLayoutBody
            .substringAfter("if (currentFormat != null && currentFormat == lastInflatedFormat)")
            .substringBefore("} else {")
    }

    private fun formatChangeBranch(): String {
        assertTrue(
            "loadLayout must have a format-change else branch",
            loadLayoutBody.contains("} else {"),
        )
        // Else arm after same-format check; ends when lastInflatedFormat is updated.
        return loadLayoutBody
            .substringAfter("if (currentFormat != null && currentFormat == lastInflatedFormat)")
            .substringAfter("} else {")
            .substringBefore("private fun showTraitPickerDialog")
            .ifBlank {
                loadLayoutBody
                    .substringAfter("} else {")
                    .substringBefore("fun showTraitPickerDialog")
            }
    }

    @Test
    fun sameFormatPath_onRefreshOnly_noOnExit() {
        val same = sameFormatBranch()
        assertTrue(
            "Same-format path must call onRefresh()",
            same.contains("onRefresh()"),
        )
        assertFalse(
            "Same-format path must not call onExit()",
            same.contains("onExit()"),
        )
        assertFalse(
            "Same-format path must not re-inflate",
            same.contains("inflateTrait"),
        )
    }

    @Test
    fun formatChangePath_previousOnExit_beforeInflateTrait() {
        val change = formatChangeBranch()
        assertTrue(
            "Format-change path must exit previous layout",
            change.contains("onExit()"),
        )
        assertTrue(
            "Format-change path must inflate the new layout",
            change.contains("inflateTrait"),
        )
        assertTrue(
            "Previous format must be resolved via lastInflatedFormat before onExit",
            change.contains("lastInflatedFormat") &&
                change.contains("getTraitLayout(previousFormat)"),
        )

        val onExitIdx = change.indexOf("onExit()")
        val inflateIdx = change.indexOf("inflateTrait")
        assertTrue("onExit must appear in format-change branch", onExitIdx >= 0)
        assertTrue("inflateTrait must appear in format-change branch", inflateIdx >= 0)
        assertTrue(
            "previous layout onExit() must run before inflateTrait",
            onExitIdx < inflateIdx,
        )
        assertFalse(
            "Format-change path must not use onRefresh()",
            change.contains("onRefresh()"),
        )
    }

    @Test
    fun loadLayout_tracksLastInflatedFormat() {
        assertTrue(
            "TraitBoxView must track lastInflatedFormat for same-format short-circuit",
            source.contains("private var lastInflatedFormat"),
        )
        assertTrue(
            "Format-change path must update lastInflatedFormat after inflate",
            formatChangeBranch().contains("lastInflatedFormat = currentFormat"),
        )
    }
}
