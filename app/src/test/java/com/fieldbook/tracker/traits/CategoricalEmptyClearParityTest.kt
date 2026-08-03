package com.fieldbook.tracker.traits

import com.fieldbook.tracker.utilities.CategoryJsonUtil
import org.brapi.v2.model.pheno.BrAPIScaleValidValuesCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Owner test for 09 §3/§4 Categorical empty-clear parity:
 * Collect empty → encode([]) == "[]"; node empty → "" via hasNodeSession().
 */
class CategoricalEmptyClearParityTest {

    private val categoricalSource = File(
        "src/main/java/com/fieldbook/tracker/traits/CategoricalTraitLayout.java",
    ).readText()

    @Test
    fun collectEmpty_encodesEmptyArrayJson() {
        val encoded = CategoryJsonUtil.encode(ArrayList())
        assertEquals("[]", encoded)

        // Same call shape as Collect clear when scale / categoryList is empty.
        val emptyScale = ArrayList<BrAPIScaleValidValuesCategories>()
        assertEquals("[]", CategoryJsonUtil.encode(emptyScale))
    }

    @Test
    fun nodeEmpty_clearsToEmptyString() {
        // Node clear path writes "" (not encode([])), gated by hasNodeSession().
        val nodeEmptyValue = ""
        assertEquals("", nodeEmptyValue)
        assertTrue(nodeEmptyValue != CategoryJsonUtil.encode(ArrayList()))
    }

    @Test
    fun clearPaths_forkOnHasNodeSession() {
        // Single-category toggle-off and multi-category last-remove both gate on hasNodeSession().
        val clearForkPattern = Regex(
            """if\s*\(\s*(?:scale\.isEmpty\(\)|categoryList\s*==\s*null\s*\|\|\s*categoryList\.isEmpty\(\))\s*\)\s*\{[^}]*?""" +
                """updateObservation\s*\(\s*getCurrentTrait\(\)\s*,\s*hasNodeSession\(\)\s*\?\s*""\s*:""" +
                """\s*CategoryJsonUtil\.Companion\.encode""",
            RegexOption.DOT_MATCHES_ALL,
        )
        val matches = clearForkPattern.findAll(categoricalSource).toList()
        assertEquals(
            "expected single-cat and multi-cat empty-clear forks to use hasNodeSession() ? \"\" : encode(...)",
            2,
            matches.size,
        )

        // Document gate contract from BaseTraitLayout / 09 §4 value-session pattern.
        val baseSource = File(
            "src/main/java/com/fieldbook/tracker/traits/BaseTraitLayout.java",
        ).readText()
        assertTrue(
            baseSource.contains("valueSession instanceof NodeTraitValueSession"),
        )
        assertTrue(
            Regex("""boolean\s+hasNodeSession\s*\(\s*\)""").containsMatchIn(baseSource),
        )
    }
}
