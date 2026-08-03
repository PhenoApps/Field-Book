package com.fieldbook.tracker.traits

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Owner test for §3 [CounterTraitLayout] / §4 Counter +/- gate:
 * Collect stays obs-first (main parity); node uses live input text;
 * both forks behind [BaseTraitLayout.hasNodeSession].
 */
class CounterCollectNodeForkParityTest {

    private val source: String by lazy {
        File("src/main/java/com/fieldbook/tracker/traits/CounterTraitLayout.java").readText()
    }

    private val initBody: String by lazy {
        source
            .substringAfter("public void init(Activity act)")
            .substringBefore("public void afterLoadExists")
    }

    private fun addListenerBody(): String {
        // First hasNodeSession fork is the add button.
        return initBody
            .substringAfter("addCounterBtn.setOnClickListener")
            .substringBefore("minusCounterBtn.setOnClickListener")
    }

    private fun minusListenerBody(): String {
        return initBody
            .substringAfter("minusCounterBtn.setOnClickListener")
            .substringBefore("addCounterBtn.requestFocus")
    }

    private fun nodeBranch(listener: String): String {
        assertTrue(
            "listener must gate on hasNodeSession()",
            listener.contains("hasNodeSession()"),
        )
        return listener
            .substringAfter("if (hasNodeSession())")
            .substringBefore("} else {")
    }

    private fun collectBranch(listener: String): String {
        // Collect is the else of hasNodeSession().
        val afterGate = listener.substringAfter("if (hasNodeSession())")
        return afterGate
            .substringAfter("} else {")
            .let { body ->
                // Trim to the matching close of the else before updateObservation / value assign.
                val end = body.indexOf("String value = getCollectInputView().getText()")
                if (end >= 0) body.substring(0, end) else body
            }
    }

    @Test
    fun addAndMinus_bothForksBehindHasNodeSession() {
        val add = addListenerBody()
        val minus = minusListenerBody()
        assertTrue("add +/- must check hasNodeSession()", add.contains("hasNodeSession()"))
        assertTrue("minus +/- must check hasNodeSession()", minus.contains("hasNodeSession()"))
        // Gate must appear before either path's seed value writes.
        assertTrue(add.indexOf("hasNodeSession()") < add.indexOf("\"1\""))
        assertTrue(minus.indexOf("hasNodeSession()") < minus.indexOf("\"-1\""))
    }

    @Test
    fun collectPath_obsFirst_nullOrNaSeedsOneAndMinusOne() {
        val collectAdd = collectBranch(addListenerBody())
        val collectMinus = collectBranch(minusListenerBody())

        assertTrue(
            "Collect add must read ObservationModel first",
            collectAdd.contains("getCurrentObservation()"),
        )
        assertTrue(
            "Collect minus must read ObservationModel first",
            collectMinus.contains("getCurrentObservation()"),
        )
        assertTrue(
            "Collect add seeds \"1\" when obs null or NA",
            collectAdd.contains("obs == null || \"NA\".equals(obs.getValue())") &&
                collectAdd.contains("setText(\"1\")"),
        )
        assertTrue(
            "Collect minus seeds \"-1\" when obs null or NA",
            collectMinus.contains("obs == null || \"NA\".equals(obs.getValue())") &&
                collectMinus.contains("setText(\"-1\")"),
        )
        // Collect must not decide empty/NA from live input text alone.
        assertFalse(
            "Collect add must not use emptyOrNa live-input gate",
            collectAdd.contains("emptyOrNa"),
        )
        assertFalse(
            "Collect minus must not use emptyOrNa live-input gate",
            collectMinus.contains("emptyOrNa"),
        )
    }

    @Test
    fun nodePath_usesLiveInputText_whenNoObservationModel() {
        val nodeAdd = nodeBranch(addListenerBody())
        val nodeMinus = nodeBranch(minusListenerBody())

        assertTrue(
            "Node add must read live input text",
            nodeAdd.contains("getCollectInputView().getText()"),
        )
        assertTrue(
            "Node minus must read live input text",
            nodeMinus.contains("getCollectInputView().getText()"),
        )
        assertTrue(
            "Node add treats empty/NA input → \"1\"",
            nodeAdd.contains("emptyOrNa") && nodeAdd.contains("setText(\"1\")"),
        )
        assertTrue(
            "Node minus treats empty/NA input → \"-1\"",
            nodeMinus.contains("emptyOrNa") && nodeMinus.contains("setText(\"-1\")"),
        )
        // Node session has no ObservationModel — must not call getCurrentObservation in the fork.
        assertFalse(
            "Node add must not use getCurrentObservation()",
            nodeAdd.contains("getCurrentObservation()"),
        )
        assertFalse(
            "Node minus must not use getCurrentObservation()",
            nodeMinus.contains("getCurrentObservation()"),
        )
    }
}
