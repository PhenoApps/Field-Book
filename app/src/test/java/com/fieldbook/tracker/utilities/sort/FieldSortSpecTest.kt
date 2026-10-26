package com.fieldbook.tracker.utilities.sort

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FieldSortSpecTest {

    @Test
    fun jsonRoundTrip() {
        val spec = FieldSortSpec(
            listOf(
                FieldSortKey("Row", ascending = false),
                FieldSortKey("Col, with comma", serpentine = true),
            )
        )
        assertEquals(spec, FieldSortSpec.parse(spec.encode()))
    }

    @Test
    fun emptySpecEncodesToNull() {
        assertNull(FieldSortSpec().encode())
    }

    @Test
    fun legacyColumnList() {
        assertEquals(
            listOf(FieldSortKey("Plot"), FieldSortKey("Rep")),
            FieldSortSpec.parse("Plot,Rep").keys
        )
    }

    @Test
    fun legacyColumnListTakesLegacyDirection() {
        assertEquals(
            listOf(FieldSortKey("Plot", ascending = false), FieldSortKey("Rep", ascending = false)),
            FieldSortSpec.parse("Plot,Rep", legacyAscending = false).keys
        )
    }

    @Test
    fun legacyDirectionDoesNotApplyToJson() {
        val spec = FieldSortSpec(listOf(FieldSortKey("Row")))
        assertEquals(spec, FieldSortSpec.parse(spec.encode(), legacyAscending = false))
    }

    @Test
    fun missingValuesParseAsNoSort() {
        listOf(null, "", "  ", "null", ",").forEach { raw ->
            assertEquals("for '$raw'", emptyList<FieldSortKey>(), FieldSortSpec.parse(raw).keys)
        }
    }

    @Test
    fun malformedJsonParsesAsNoSort() {
        assertEquals(emptyList<FieldSortKey>(), FieldSortSpec.parse("{\"keys\": [").keys)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val spec = FieldSortSpec.parse("""{"keys":[{"column":"Row","ascending":false,"future":1}],"version":2,"other":true}""")
        assertEquals(listOf(FieldSortKey("Row", ascending = false)), spec.keys)
    }
}
