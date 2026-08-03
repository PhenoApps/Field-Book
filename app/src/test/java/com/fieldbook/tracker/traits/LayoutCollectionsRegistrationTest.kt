package com.fieldbook.tracker.traits

import android.app.Activity
import android.util.Log
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLog

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LayoutCollectionsRegistrationTest {

    private lateinit var activity: Activity
    private lateinit var collections: LayoutCollections

    @Before
    fun setUp() {
        ShadowLog.reset()
        activity = Robolectric.buildActivity(Activity::class.java).setup().get()
        // Seed text only — avoids Hilt camera traits while exercising lazy tree registration.
        collections = LayoutCollections(activity, arrayListOf(TextTraitLayout(activity)))
    }

    @Test
    fun coldStart_doesNotConstructTreeLayouts() {
        val layouts = registeredLayouts()
        assertFalse(layouts.any { it is TreeTraitLayout })
        assertFalse(layouts.any { it is TreeSummaryTraitLayout })
        assertFalse(treeLayoutsRegistered())
        // Production constructor must not eagerly new-up tree layouts either.
        val source = java.io.File(
            "src/main/java/com/fieldbook/tracker/traits/LayoutCollections.java",
        ).readText()
        assertFalse(
            "defaultLayouts must not construct TreeTraitLayout",
            Regex("""new\s+TreeTraitLayout\s*\(""").containsMatchIn(
                source.substringAfter("defaultLayouts").substringBefore("ensureTreeLayoutsRegistered"),
            ),
        )
        assertFalse(
            "defaultLayouts must not construct TreeSummaryTraitLayout",
            Regex("""new\s+TreeSummaryTraitLayout\s*\(""").containsMatchIn(
                source.substringAfter("defaultLayouts").substringBefore("ensureTreeLayoutsRegistered"),
            ),
        )
    }

    @Test
    fun getTraitLayout_nonTree_unchangedAndStillLazy() {
        val text = collections.getTraitLayout(TextTraitLayout.type)
        assertTrue(text is TextTraitLayout)

        val layouts = registeredLayouts()
        assertFalse(layouts.any { it is TreeTraitLayout })
        assertFalse(layouts.any { it is TreeSummaryTraitLayout })
        assertFalse(treeLayoutsRegistered())
    }

    @Test
    fun getTraitLayout_treeFormat_lazyRegistersBoth() {
        assertFalse(treeLayoutsRegistered())

        val tree = collections.getTraitLayout(TreeTraitLayout.type)
        assertTrue(tree is TreeTraitLayout)
        assertTrue(treeLayoutsRegistered())

        val layouts = registeredLayouts()
        assertEquals(1, layouts.count { it is TreeTraitLayout })
        assertEquals(1, layouts.count { it is TreeSummaryTraitLayout })

        val summary = collections.getTraitLayout(TreeSummaryTraitLayout.type)
        assertTrue(summary is TreeSummaryTraitLayout)
        // Second resolve must not double-register
        assertEquals(1, registeredLayouts().count { it is TreeTraitLayout })
        assertEquals(1, registeredLayouts().count { it is TreeSummaryTraitLayout })
    }

    @Test
    fun getTraitLayout_unknownFormat_logsAndFallsBackToText() {
        val layout = collections.getTraitLayout("not_a_real_format")
        assertTrue(layout is TextTraitLayout)

        val warnings = ShadowLog.getLogsForTag("LayoutCollections")
            .filter { it.type == Log.WARN }
        assertTrue(
            warnings.any {
                it.msg.contains("not_a_real_format") && it.msg.contains("falling back to text")
            },
        )

        // Unknown format must not trigger tree registration
        assertFalse(treeLayoutsRegistered())
        assertFalse(registeredLayouts().any { it is TreeTraitLayout })
    }

    @Suppress("UNCHECKED_CAST")
    private fun registeredLayouts(): List<BaseTraitLayout> {
        val field = LayoutCollections::class.java.getDeclaredField("traitLayouts")
        field.isAccessible = true
        return field.get(collections) as ArrayList<BaseTraitLayout>
    }

    private fun treeLayoutsRegistered(): Boolean {
        val field = LayoutCollections::class.java.getDeclaredField("treeLayoutsRegistered")
        field.isAccessible = true
        return field.getBoolean(collections)
    }
}
