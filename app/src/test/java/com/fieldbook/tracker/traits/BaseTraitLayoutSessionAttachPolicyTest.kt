package com.fieldbook.tracker.traits

import android.app.Activity
import android.content.Context
import android.view.LayoutInflater
import androidx.test.core.app.ApplicationProvider
import com.fieldbook.tracker.R
import com.fieldbook.tracker.objects.TraitObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Owner test for 09 L7 Collect-vs-node value-session contract on [BaseTraitLayout]:
 * Collect [init] leaves [valueSession] null (direct CollectActivity helpers); nodes
 * [attachSession] [NodeTraitValueSession]; [hasNodeSession] is true only for node sessions;
 * commit/clear go through the session when attached (node ≠ plot carousel).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BaseTraitLayoutSessionAttachPolicyTest {

    private val baseSource: String by lazy {
        File("src/main/java/com/fieldbook/tracker/traits/BaseTraitLayout.java").readText()
    }

    private val nodeSessionSource: String by lazy {
        File("src/main/java/com/fieldbook/tracker/traits/NodeTraitValueSession.kt").readText()
    }

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    // --- Collect init does NOT auto-attach CollectTraitValueSession ---

    @Test
    fun init_withRoot_doesNotAutoAttachCollectTraitValueSession() {
        val initWithRoot = baseSource
            .substringAfter("public void init(@NonNull Activity act, @NonNull View root)")
            .substringBefore("protected final <T extends View> T findTraitView")

        assertFalse(
            "Collect must not auto-attach CollectTraitValueSession on init",
            Regex(
                """valueSession\s*=\s*new\s+CollectTraitValueSession""",
            ).containsMatchIn(initWithRoot),
        )
        assertTrue(
            "init(Activity, View) must still bind traitBindRoot then call init(act)",
            initWithRoot.contains("this.traitBindRoot = root") &&
                Regex("""init\s*\(\s*act\s*\)""").containsMatchIn(initWithRoot),
        )
    }

    @Test
    fun init_withActivity_leavesValueSessionNull() {
        val activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        val layout = TextTraitLayout(activity)
        val root = LayoutInflater.from(activity).inflate(R.layout.trait_text, null)
        layout.init(activity, root)
        assertNull(
            "Collect/Activity init must leave valueSession null (direct helpers)",
            layout.valueSession,
        )
        assertFalse(layout.hasNodeSession())
    }

    // --- hasNodeSession true only for NodeTraitValueSession ---

    @Test
    fun hasNodeSession_source_trueOnlyForNodeTraitValueSession() {
        val method = baseSource
            .substringAfter("public boolean hasNodeSession()")
            .substringBefore("public abstract int layoutId()")
        assertTrue(
            Regex("""return\s+valueSession\s+instanceof\s+NodeTraitValueSession\s*;""")
                .containsMatchIn(method),
        )
        assertFalse(
            "hasNodeSession must not treat non-node sessions as node",
            method.contains("CollectTraitValueSession"),
        )
    }

    @Test
    fun hasNodeSession_executable_trueOnlyForNodeSession() {
        val layout = TextTraitLayout(context)
        assertFalse("no session → not node", layout.hasNodeSession())

        val trait = TraitObject().apply {
            name = "height"
            format = "text"
            id = "1"
        }
        layout.attachSession(
            NodeTraitValueSession(context, trait, "", locked = false, onValueChange = {}),
        )
        assertTrue("NodeTraitValueSession → hasNodeSession", layout.hasNodeSession())
        assertNotNull(layout.valueSession)
        assertTrue(layout.valueSession is NodeTraitValueSession)

        val stubCollectLike = object : TraitValueSession {
            override fun currentTrait(): TraitObject = trait
            override fun isLocked(): Boolean = false
            override fun inputView() = error("unused")
            override fun commit(trait: TraitObject, value: String) = Unit
            override fun clear(trait: TraitObject) = Unit
        }
        layout.attachSession(stubCollectLike)
        assertFalse(
            "non-NodeTraitValueSession must not report hasNodeSession",
            layout.hasNodeSession(),
        )
    }

    // --- commit / clear go through session (node ≠ plot carousel) ---

    @Test
    fun updateObservation_and_removeTrait_routeThroughSession_whenAttached() {
        val updateBody = baseSource
            .substringAfter("public void updateObservation(TraitObject trait, String value)")
            .substringBefore("protected void handleAutoSwitchToNextPlot")
        assertTrue(
            "updateObservation must commit via valueSession when attached",
            updateBody.contains("valueSession.commit(trait, value)"),
        )
        assertTrue(
            "null session must fall through to CollectActivity.updateObservation",
            updateBody.contains("getContext()).updateObservation(trait, value, null)"),
        )
        assertTrue(
            "node session must skip plot carousel auto-switch",
            updateBody.contains("!(valueSession instanceof NodeTraitValueSession)") &&
                updateBody.contains("handleAutoSwitchToNextPlot"),
        )

        val removeBody = baseSource
            .substringAfter("public void removeTrait(TraitObject trait)")
            .substringBefore("protected void clearObservationOrRemoveTrait")
        assertTrue(
            "removeTrait must clear via valueSession when attached",
            removeBody.contains("valueSession.clear(trait)"),
        )
    }

    @Test
    fun nodeSession_hitsSidecar_collectNullSession_hitsPlotPipeline() {
        assertTrue(
            "NodeTraitValueSession.commit must call onValueChange (sidecar)",
            nodeSessionSource.contains("onValueChange(value)"),
        )
        assertTrue(
            "NodeTraitValueSession.clear must call onValueChange(\"\")",
            nodeSessionSource.contains("""onValueChange("")"""),
        )
        assertFalse(
            "NodeTraitValueSession must not call CollectActivity.updateObservation",
            nodeSessionSource.contains("updateObservation"),
        )
        assertFalse(
            "NodeTraitValueSession must not call CollectActivity.removeTrait",
            nodeSessionSource.contains("removeTrait"),
        )
        val updateBody = baseSource
            .substringAfter("public void updateObservation(TraitObject trait, String value)")
            .substringBefore("protected void handleAutoSwitchToNextPlot")
        assertTrue(
            "Collect null-session path must call CollectActivity.updateObservation",
            updateBody.contains("((CollectActivity) getContext()).updateObservation(trait, value, null)"),
        )
    }

    @Test
    fun nodeCommitClear_executable_goThroughSession_notPlotCarousel() {
        val trait = TraitObject().apply {
            name = "color"
            format = "text"
            id = "2"
        }
        val committed = mutableListOf<String>()
        val session = NodeTraitValueSession(
            context,
            trait,
            initialValue = "",
            locked = false,
            onValueChange = { committed.add(it) },
        )
        val layout = TextTraitLayout(context)
        layout.attachSession(session)

        layout.updateObservation(trait, "red")
        assertEquals(listOf("red"), committed)
        assertEquals("red", session.inputView().text)

        layout.removeTrait(trait)
        assertEquals(listOf("red", ""), committed)
        assertEquals("", session.inputView().text)

        assertTrue(layout.hasNodeSession())
    }
}
