package com.fieldbook.tracker.activities

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Owner test for trait-tree/09 CollectActivity residual gates (matrix row + L2 +
 * residual watchlist onPause→onExit).
 *
 * Source-contract only — does not redesign Collect. Photo L3/L4 request-code
 * separation is owned by [com.fieldbook.tracker.traits.formats.tree.PhotoLayoutSeparationPolicyTest].
 */
class CollectActivityGatePolicyTest {

    private val collectActivitySource: String by lazy {
        File("src/main/java/com/fieldbook/tracker/activities/CollectActivity.java").readText()
    }

    private fun navigateIfDataIsValidBody(): String =
        collectActivitySource
            .substringAfter("public void navigateIfDataIsValid(")
            .substringAfter("{")
            .substringBefore("\n    private void setNaText(")

    private fun onPauseBody(): String =
        collectActivitySource
            .substringAfter("public void onPause() {")
            .substringBefore("\n    @Override\n    public void onDestroy()")
            .substringBefore("\n    public void onDestroy()")

    // --- L2 / gate commit: toast+Overview only for TreeTraitLayout ---

    @Test
    fun navigateIfDataIsValid_toastAndOverview_onlyInsideTreeTraitLayoutGate() {
        val body = navigateIfDataIsValidBody()

        assertTrue(
            "blocked path must gate on instanceof TreeTraitLayout",
            body.contains("instanceof TreeTraitLayout"),
        )
        assertTrue(
            "tree blocked nav must toast tree_nav_blocked",
            body.contains("R.string.tree_nav_blocked") && body.contains("Utils.makeToast"),
        )
        assertTrue(
            "tree blocked nav must open Overview",
            body.contains("openOverviewForBlockedNav()"),
        )

        // Toast / Overview / early return must sit inside the TreeTraitLayout arm only.
        val treeArm = body
            .substringAfter("if (layout instanceof TreeTraitLayout) {")
            .substringBefore("}")
        assertTrue(treeArm.contains("Utils.makeToast"))
        assertTrue(treeArm.contains("openOverviewForBlockedNav()"))
        assertTrue(treeArm.contains("return;"))

        // Outside the tree arm: no toast / Overview when block() is true for Date/Text/etc.
        val afterTreeArm = body.substringAfter("if (layout instanceof TreeTraitLayout) {")
            .substringAfter("}")
        assertFalse(
            "non-tree block must not toast",
            afterTreeArm.contains("Utils.makeToast") || afterTreeArm.contains("tree_nav_blocked"),
        )
        assertFalse(
            "non-tree block must not open Overview",
            afterTreeArm.contains("openOverviewForBlockedNav"),
        )
        assertFalse(
            "non-tree isTraitBlocked path must not early-return before validateData",
            Regex("""if\s*\(\s*isTraitBlocked\s*\(\s*\)\s*\)\s*\{[^}]*return\s*;""")
                .containsMatchIn(body.replace(treeArm, "")),
        )

        // Contract comment: Date/Text block() stays RepeatedValuesView-only (no toast/early-return).
        assertTrue(
            body.contains("do not toast or early-return") ||
                body.contains("RepeatedValuesView only"),
        )
    }

    @Test
    fun navigateIfDataIsValid_nonTreeBlocked_fallsThroughToValidateData() {
        val body = navigateIfDataIsValidBody()

        // Structure: isTraitBlocked → optional tree return → validateData (fall-through for non-tree).
        val blockedIdx = body.indexOf("isTraitBlocked()")
        val validateIdx = body.indexOf("validateData(")
        assertTrue(blockedIdx >= 0 && validateIdx > blockedIdx)

        val between = body.substring(blockedIdx, validateIdx)
        // Only one early return in the blocked region, and it is inside the tree arm.
        val returns = Regex("""\breturn\s*;""").findAll(between).count()
        assertTrue(
            "only the TreeTraitLayout arm may return before validateData (found $returns)",
            returns == 1 && between.contains("instanceof TreeTraitLayout"),
        )
    }

    // --- Residual: onPause → onExit for all formats ---

    @Test
    fun onPause_callsGetTraitLayoutOnExit() {
        val body = onPauseBody()
        assertTrue(
            "onPause must flush current layout via getTraitLayout().onExit()",
            body.contains("getTraitLayout().onExit()"),
        )
        assertTrue(
            "onExit must run before super.onPause()",
            body.indexOf("getTraitLayout().onExit()") < body.indexOf("super.onPause()"),
        )
    }

    @Test
    fun onDestroy_alsoCallsGetTraitLayoutOnExit() {
        val destroyBody = collectActivitySource
            .substringAfter("public void onDestroy() {")
            .substringBefore("\n    @Override\n    public void onResume()")
            .substringBefore("\n    public void onResume()")
        assertTrue(
            "onDestroy lifecycle path also calls getTraitLayout().onExit()",
            destroyBody.contains("getTraitLayout().onExit()"),
        )
    }
}
