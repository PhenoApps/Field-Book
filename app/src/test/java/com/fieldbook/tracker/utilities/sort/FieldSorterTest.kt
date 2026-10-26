package com.fieldbook.tracker.utilities.sort

import org.junit.Assert.assertEquals
import org.junit.Test

class FieldSorterTest {

    /** Sorts rows of named values and returns the value of [label] for each row in sorted order. */
    private fun sort(rows: List<Map<String, String?>>, keys: List<FieldSortKey>, label: String = "id"): List<String?> {
        val order = FieldSorter.order(rows.size, keys) { row, key -> rows[row][keys[key].column] }
        return order.map { rows[it][label] }
    }

    /** A rows by cols grid in shuffled order, with ids like "r2c3". */
    private fun grid(rows: Int, cols: Int, block: String? = null) = (1..rows).flatMap { r ->
        (1..cols).map { c ->
            mapOf("id" to "${block ?: ""}r${r}c$c", "Block" to block, "Row" to "$r", "Col" to "$c")
        }
    }.shuffled(java.util.Random(42))

    @Test
    fun noKeysKeepsIncomingOrder() {
        val rows = listOf(mapOf("id" to "b"), mapOf("id" to "a"), mapOf("id" to "c"))
        assertEquals(listOf("b", "a", "c"), sort(rows, emptyList()))
    }

    @Test
    fun multipleKeysWithMixedDirections() {
        val keys = listOf(FieldSortKey("Row", ascending = false), FieldSortKey("Col"))
        assertEquals(
            listOf("r2c1", "r2c2", "r2c3", "r1c1", "r1c2", "r1c3"),
            sort(grid(2, 3), keys)
        )
    }

    @Test
    fun serpentineReversesEveryOtherRow() {
        val keys = listOf(FieldSortKey("Row"), FieldSortKey("Col", serpentine = true))
        assertEquals(
            listOf("r1c1", "r1c2", "r1c3", "r2c3", "r2c2", "r2c1", "r3c1", "r3c2", "r3c3"),
            sort(grid(3, 3), keys)
        )
    }

    @Test
    fun serpentineStartsFromTheKeysOwnDirection() {
        val keys = listOf(FieldSortKey("Row"), FieldSortKey("Col", ascending = false, serpentine = true))
        assertEquals(
            listOf("r1c2", "r1c1", "r2c1", "r2c2"),
            sort(grid(2, 2), keys)
        )
    }

    @Test
    fun serpentineCountsGroupsNotValues() {
        //rows 2 and 4 are missing, so alternation follows the 1st, 2nd, 3rd row present rather than odd/even row numbers
        val rows = grid(5, 2).filter { it["Row"] != "2" && it["Row"] != "4" }
        val keys = listOf(FieldSortKey("Row"), FieldSortKey("Col", serpentine = true))
        assertEquals(
            listOf("r1c1", "r1c2", "r3c2", "r3c1", "r5c1", "r5c2"),
            sort(rows, keys)
        )
    }

    @Test
    fun serpentineRestartsInEachParentGroup() {
        val rows = grid(3, 2, block = "A") + grid(3, 2, block = "B")
        val keys = listOf(FieldSortKey("Block"), FieldSortKey("Row"), FieldSortKey("Col", serpentine = true))
        assertEquals(
            listOf(
                "Ar1c1", "Ar1c2", "Ar2c2", "Ar2c1", "Ar3c1", "Ar3c2",
                "Br1c1", "Br1c2", "Br2c2", "Br2c1", "Br3c1", "Br3c2",
            ),
            sort(rows, keys)
        )
    }

    @Test
    fun serpentineOnFirstKeyIsIgnored() {
        val keys = listOf(FieldSortKey("Row", serpentine = true), FieldSortKey("Col"))
        assertEquals(listOf("r1c1", "r1c2", "r2c1", "r2c2"), sort(grid(2, 2), keys))
    }

    @Test
    fun usesNaturalOrder() {
        val rows = listOf("A10", "A2", "a1", "A3").map { mapOf("id" to it) }
        assertEquals(listOf("a1", "A2", "A3", "A10"), sort(rows, listOf(FieldSortKey("id"))))
    }

    @Test
    fun blanksSortLastInBothDirections() {
        val rows = listOf("2", null, "1", "", "3").map { mapOf("id" to it, "v" to it) }
        assertEquals(listOf("1", "2", "3", null, ""), sort(rows, listOf(FieldSortKey("v"))))
        assertEquals(listOf("3", "2", "1", null, ""), sort(rows, listOf(FieldSortKey("v", ascending = false))))
    }

    @Test
    fun tiesKeepIncomingOrder() {
        val rows = listOf("x" to "2", "y" to "1", "z" to "2", "w" to "1").map { (id, v) -> mapOf("id" to id, "v" to v) }
        assertEquals(listOf("y", "w", "x", "z"), sort(rows, listOf(FieldSortKey("v"))))
        assertEquals(listOf("x", "z", "y", "w"), sort(rows, listOf(FieldSortKey("v", ascending = false))))
    }
}
