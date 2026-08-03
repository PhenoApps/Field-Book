package com.fieldbook.tracker.traits.formats.tree

import com.fieldbook.tracker.objects.TraitObject
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeTraitValueSupportTest {

    private fun categoricalTrait(): TraitObject = TraitObject().apply {
        name = "Color"
        format = "categorical"
        categories = """[{"label":"Red","value":"R"},{"label":"Blue","value":"B"}]"""
    }

    @Test
    fun isValidCategory_acceptsEmptyAndEmptyJsonArray() {
        val trait = categoricalTrait()
        assertTrue(TreeTraitValueSupport.isValidCategory("", trait))
        assertTrue(TreeTraitValueSupport.isValidCategory("[]", trait))
        assertTrue(TreeTraitValueSupport.isValidCategory("  []  ", trait))
    }

    @Test
    fun isValidCategory_acceptsBrapiJsonSelection() {
        val trait = categoricalTrait()
        val json = """[{"label":"Red","value":"R"}]"""
        assertTrue(TreeTraitValueSupport.isValidCategory(json, trait))
    }

    @Test
    fun isValidStopWatch_acceptsCircularTimerFormats() {
        assertTrue(TreeTraitValueSupport.isValidStopWatch("0:00:01"))
        assertTrue(TreeTraitValueSupport.isValidStopWatch("1:23:45.678"))
        assertTrue(TreeTraitValueSupport.isValidStopWatch("12:59:59.9"))
        org.junit.Assert.assertFalse(TreeTraitValueSupport.isValidStopWatch("1:60:00"))
        org.junit.Assert.assertFalse(TreeTraitValueSupport.isValidStopWatch("not-a-time"))
        org.junit.Assert.assertFalse(TreeTraitValueSupport.isValidStopWatch(""))
    }

    @Test
    fun isValidBoolean_acceptsCommonStoredForms() {
        assertTrue(TreeTraitValueSupport.isValidBoolean("true"))
        assertTrue(TreeTraitValueSupport.isValidBoolean("FALSE"))
        assertTrue(TreeTraitValueSupport.isValidBoolean("1"))
        assertTrue(TreeTraitValueSupport.isValidBoolean("0"))
        org.junit.Assert.assertFalse(TreeTraitValueSupport.isValidBoolean("yes"))
    }
}
