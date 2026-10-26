package com.fieldbook.tracker.utilities.sort

/**
 * Orders a field's entries by a list of [FieldSortKey]s using natural ordering.
 *
 * Rows are sorted by the first key, split into groups of equal values, and each group is sorted by the next key.
 * A serpentine key reverses its direction in every other group of the key before it, counting from the
 * first group within the parent, so the alternation restarts in each group of earlier keys
 * (e.g. Block, Row, serpentine Column starts every block's first row in the same direction).
 * Serpentine has no effect on the first key.
 *
 * Blank values sort last in either direction, and rows that tie on every key keep their incoming order.
 */
object FieldSorter {

    /**
     * @param rowCount the number of rows being sorted
     * @param keys the columns to sort by, in priority order
     * @param value the value of a row for the key at keyIndex
     * @return row indices in sorted order
     */
    fun order(rowCount: Int, keys: List<FieldSortKey>, value: (row: Int, keyIndex: Int) -> String?): IntArray {

        if (keys.isEmpty()) return IntArray(rowCount) { it }

        val values = Array(keys.size) { keyIndex ->
            Array(rowCount) { row ->
                value(row, keyIndex)?.takeIf { it.isNotBlank() }?.let { NaturalSortKey.of(it) }
            }
        }

        val result = IntArray(rowCount)
        var next = 0

        fun sortLevel(rows: List<Int>, level: Int, groupIndex: Int) {

            if (level == keys.size || rows.size < 2) {
                rows.forEach { result[next++] = it }
                return
            }

            val key = keys[level]
            val levelValues = values[level]
            val reversed = level > 0 && key.serpentine && groupIndex % 2 == 1
            val ascending = key.ascending != reversed

            val comparator = Comparator<Int> { a, b -> compareBlanksLast(levelValues[a], levelValues[b], ascending) }

            //sortedWith is stable, which keeps ties in their incoming order
            val sorted = rows.sortedWith(comparator)

            var start = 0
            var group = 0
            while (start < sorted.size) {
                var end = start + 1
                while (end < sorted.size && comparator.compare(sorted[start], sorted[end]) == 0) end++
                sortLevel(sorted.subList(start, end), level + 1, group++)
                start = end
            }
        }

        sortLevel((0 until rowCount).toList(), 0, 0)

        return result
    }

    private fun compareBlanksLast(a: NaturalSortKey?, b: NaturalSortKey?, ascending: Boolean): Int = when {
        a == null && b == null -> 0
        a == null -> 1
        b == null -> -1
        ascending -> a.compareTo(b)
        else -> b.compareTo(a)
    }
}
