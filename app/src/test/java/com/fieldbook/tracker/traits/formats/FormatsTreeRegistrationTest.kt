package com.fieldbook.tracker.traits.formats

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FormatsTreeRegistrationTest {

    @Test
    fun getMainFormats_excludesTreeFormats() {
        val main = Formats.getMainFormats()
        assertFalse(Formats.TREE_ARCHITECTURE in main)
        assertFalse(Formats.TREE_SUMMARY in main)
    }

    @Test
    fun getExperimentalFormats_includesBothTreeFormats() {
        val experimental = Formats.getExperimentalFormats()
        assertTrue(Formats.TREE_ARCHITECTURE in experimental)
        assertTrue(Formats.TREE_SUMMARY in experimental)
    }

    @Test
    fun getCreatableExperimentalFormats_onlyTreeArchitecture() {
        val creatable = Formats.getCreatableExperimentalFormats()
        assertTrue(Formats.TREE_ARCHITECTURE in creatable)
        assertFalse(Formats.TREE_SUMMARY in creatable)
    }
}
