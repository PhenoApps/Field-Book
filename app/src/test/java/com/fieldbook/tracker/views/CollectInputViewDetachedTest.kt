package com.fieldbook.tracker.views

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import com.fieldbook.tracker.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Owner test for [CollectInputView.createDetached] (09 Residual-risk / watchlist #7).
 * Detached node buffers must never enable Collect repeated-measures chrome.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CollectInputViewDetachedTest {

    private lateinit var context: Context
    private lateinit var detached: CollectInputView

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        detached = CollectInputView.createDetached(context)
    }

    @Test
    fun createDetached_setsDetachedFromCollect() {
        assertTrue(detached.detachedFromCollect)
    }

    @Test
    fun createDetached_disablesRepeatsPath() {
        assertFalse(detached.isRepeatEnabled())
        // Still false after text mutations (no CollectActivity / trait to flip it on).
        detached.text = "sidecar"
        assertFalse(detached.isRepeatEnabled())
        assertEquals("sidecar", detached.text)
    }

    @Test
    fun createDetached_getRep_isStableOne() {
        assertEquals("1", detached.getRep())
        detached.text = "a"
        assertEquals("1", detached.getRep())
        detached.prepareEmptyObservationsMode()
        assertEquals("1", detached.getRep())
        assertEquals(emptyList<Int>(), detached.getSavedIds())
    }

    @Test
    fun createDetached_hasNoRepeatedValuesViewChrome() {
        assertNull(detached.findViewById(R.id.view_collect_input_repeat_view))
        assertNull(detached.findViewById(R.id.view_collect_input_timestamp_tv))
        assertNotNull(detached.findViewById<View>(R.id.view_collect_input_edit_text))

        try {
            detached.repeatView
            fail("detached buffer must not expose RepeatedValuesView")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message!!.contains("detached"))
        }
    }
}
