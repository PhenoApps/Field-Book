package com.fieldbook.tracker.traits

import com.fieldbook.tracker.enums.ThreeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Owner test for 09 §3 [BooleanTraitLayout] Collect vs node delete parity:
 * [deleteTraitListener] gates on [BaseTraitLayout.hasNodeSession];
 * node → [BaseTraitLayout.clearObservationOrRemoveTrait]; Collect → removeTrait();
 * both reset seekbar to [ThreeState.NEUTRAL] before super.deleteTraitListener().
 */
class BooleanCollectNodeDeleteParityTest {

    private val source: String by lazy {
        File("src/main/java/com/fieldbook/tracker/traits/BooleanTraitLayout.java").readText()
    }

    private val deleteBody: String by lazy {
        source
            .substringAfter("public void deleteTraitListener()")
            .substringBefore("@NonNull")
            .substringBefore("public Boolean validate")
    }

    private fun nodeBranch(): String {
        assertTrue(
            "deleteTraitListener must gate on hasNodeSession()",
            deleteBody.contains("hasNodeSession()"),
        )
        return deleteBody
            .substringAfter("if (hasNodeSession())")
            .substringBefore("((CollectActivity)")
            .ifBlank {
                deleteBody
                    .substringAfter("if (hasNodeSession())")
                    .substringBefore("} else")
            }
    }

    private fun collectBranch(): String {
        // Collect path is the fall-through after the node early-return.
        val afterNode = deleteBody.substringAfter("if (hasNodeSession())")
        val afterReturn = afterNode.substringAfter("return;")
        return afterReturn.ifBlank {
            afterNode.substringAfter("} else {")
        }
    }

    @Test
    fun deleteTraitListener_gatesOnHasNodeSession() {
        assertTrue(
            "deleteTraitListener must check hasNodeSession()",
            deleteBody.contains("hasNodeSession()"),
        )
        assertTrue(
            "gate must appear before either delete path",
            deleteBody.indexOf("hasNodeSession()") <
                deleteBody.indexOf("clearObservationOrRemoveTrait()") &&
                deleteBody.indexOf("hasNodeSession()") <
                deleteBody.indexOf("removeTrait()"),
        )
    }

    @Test
    fun nodePath_clearObservationOrRemoveTrait_thenNeutral_thenSuper() {
        val node = nodeBranch()
        assertTrue(
            "Node delete must call clearObservationOrRemoveTrait()",
            node.contains("clearObservationOrRemoveTrait()"),
        )
        assertFalse(
            "Node delete must not call CollectActivity.removeTrait()",
            node.contains("((CollectActivity)") && node.contains("removeTrait()"),
        )

        val clearIdx = node.indexOf("clearObservationOrRemoveTrait()")
        val neutralIdx = node.indexOf("ThreeState.NEUTRAL.getValue()")
        val superIdx = node.indexOf("super.deleteTraitListener()")
        assertTrue("node must reset seekbar to NEUTRAL", neutralIdx >= 0)
        assertTrue("node must call super.deleteTraitListener()", superIdx >= 0)
        assertTrue(
            "node: clear → NEUTRAL → super",
            clearIdx < neutralIdx && neutralIdx < superIdx,
        )
    }

    @Test
    fun collectPath_removeTrait_thenNeutral_thenSuper() {
        val collect = collectBranch()
        assertTrue(
            "Collect delete must call removeTrait() on CollectActivity",
            collect.contains("((CollectActivity)") && collect.contains("removeTrait()"),
        )
        assertFalse(
            "Collect delete must not call clearObservationOrRemoveTrait()",
            collect.contains("clearObservationOrRemoveTrait()"),
        )

        val removeIdx = collect.indexOf("removeTrait()")
        val neutralIdx = collect.indexOf("ThreeState.NEUTRAL.getValue()")
        val superIdx = collect.indexOf("super.deleteTraitListener()")
        assertTrue("Collect must reset seekbar to NEUTRAL", neutralIdx >= 0)
        assertTrue("Collect must call super.deleteTraitListener()", superIdx >= 0)
        assertTrue(
            "Collect: removeTrait → NEUTRAL → super",
            removeIdx < neutralIdx && neutralIdx < superIdx,
        )
    }

    @Test
    fun neutralProgress_isUnsetSeekbarPosition() {
        // Runtime lock-in: NEUTRAL is the unset seekbar slot both paths reset to.
        assertEquals(1, ThreeState.NEUTRAL.value)
        assertEquals("UNSET", ThreeState.NEUTRAL.state)
    }
}
