package com.fieldbook.tracker.traits.composables.collect

import dev.bandb.graphview.layouts.tree.BuchheimWalkerConfiguration
import org.junit.Assert.assertEquals
import org.junit.Test

class TreeOverviewGraphOrientationTest {

    @Test
    fun defaultOrientation_isBottomToTop() {
        assertEquals(
            BuchheimWalkerConfiguration.ORIENTATION_BOTTOM_TOP,
            TreeOverviewDefaultOrientation,
        )
    }
}
