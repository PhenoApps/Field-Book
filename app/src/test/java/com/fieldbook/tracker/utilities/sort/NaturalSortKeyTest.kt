package com.fieldbook.tracker.utilities.sort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NaturalSortKeyTest {

    private fun sorted(vararg values: String) = values.sortedBy { NaturalSortKey.of(it) }

    private fun assertBefore(first: String, second: String) {
        assertTrue("$first should sort before $second", NaturalSortKey.of(first) < NaturalSortKey.of(second))
        assertTrue("$second should sort after $first", NaturalSortKey.of(second) > NaturalSortKey.of(first))
    }

    @Test
    fun numbersCompareByValue() {
        assertEquals(listOf("1", "2", "9", "10", "100"), sorted("100", "10", "9", "2", "1"))
    }

    @Test
    fun embeddedNumbersCompareByValue() {
        assertEquals(listOf("A1", "A2", "A10", "B1"), sorted("A10", "B1", "A2", "A1"))
        assertEquals(listOf("plot_9_rep2", "plot_10_rep1"), sorted("plot_10_rep1", "plot_9_rep2"))
    }

    @Test
    fun negativeAndDecimalNumbers() {
        assertEquals(listOf("-10", "-1.5", "0", "1.25", "1.5", "2"), sorted("2", "1.5", "-1.5", "0", "1.25", "-10"))
    }

    @Test
    fun minusInsideValueIsASeparator() {
        assertEquals(listOf("A-2", "A-10"), sorted("A-10", "A-2"))
    }

    @Test
    fun versionLikeValuesCompareEachPart() {
        assertEquals(listOf("1.2.9", "1.2.10"), sorted("1.2.10", "1.2.9"))
    }

    @Test
    fun textIgnoresCase() {
        assertBefore("apple", "Banana")
        assertBefore("Apple", "banana")
    }

    @Test
    fun numbersBeforeText() {
        assertBefore("10", "A1")
        assertBefore("3a", "4")
    }

    @Test
    fun leadingZerosOnlyBreakTies() {
        assertBefore("plot_2", "plot_10")
        assertBefore("plot_002", "plot_10")
        assertBefore("plot_002", "plot_2")
    }

    @Test
    fun onlyIdenticalValuesAreEqual() {
        assertEquals(0, NaturalSortKey.of("Plot 1").compareTo(NaturalSortKey.of("Plot 1")))
        assertTrue(NaturalSortKey.of("a").compareTo(NaturalSortKey.of("A")) != 0)
        assertTrue(NaturalSortKey.of("1.0").compareTo(NaturalSortKey.of("1.00")) != 0)
    }

    @Test
    fun longDigitRunsDoNotOverflow() {
        assertBefore("99999999999999999999999", "100000000000000000000000")
    }

    @Test
    fun orderingIsTransitiveAcrossMixedValues() {
        val values = listOf("1.10", "1.5", "1.7x", "-2", "A2", "a10", "10", "3a", "", " 4", "B", "1e5", "007")
        val keys = values.map { NaturalSortKey.of(it) }
        for (a in keys) for (b in keys) for (c in keys) {
            if (a <= b && b <= c) assertTrue("$a <= $b <= $c", a <= c)
        }
        for (a in keys) for (b in keys) {
            assertEquals(Integer.signum(a.compareTo(b)), -Integer.signum(b.compareTo(a)))
        }
    }
}
