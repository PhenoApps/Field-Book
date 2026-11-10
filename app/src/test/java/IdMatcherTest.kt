import com.fieldbook.tracker.utilities.IdMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Covers the approximate matching used to suggest IDs when a scanned or typed barcode has no exact match.
 */
class IdMatcherTest {

    private val plots = listOf(
        "13RPN00001", "13RPN00002", "13RPN00010", "13RPN00100", "13RPN01000", "14ABC00001"
    )

    private fun rank(query: String?, candidates: List<String> = plots) =
        IdMatcher.rank(query, candidates, { listOf(it) }).map { it.item }

    @Test
    fun normalizeIgnoresCaseWhitespaceAndControlCharacters() {
        assertEquals("13RPN00001", IdMatcher.normalize(" 13rpn\t0000 1\r\n\u0000"))
        assertEquals("", IdMatcher.normalize(null))
    }

    @Test
    fun caseAndWhitespaceDifferencesRankFirst() {
        val matches = IdMatcher.rank("13rpn00001 ", plots, { listOf(it) })
        assertEquals("13RPN00001", matches.first().item)
        assertEquals(IdMatcher.TIER_NORMALIZED, matches.first().score.tier)
    }

    @Test
    fun truncatedScanMatchesAsPartial() {
        val score = IdMatcher.score("RPN00001", "13RPN00001")
        assertEquals(IdMatcher.Score(IdMatcher.TIER_PARTIAL, 2), score)
    }

    @Test
    fun partialMatchNeedsEnoughOfTheId() {
        // too short to be a meaningful partial scan
        assertNull(IdMatcher.score("RPN", "13RPN00001"))
        // long enough, but less than half of the candidate
        assertNull(IdMatcher.score("RPN0", "13RPN00001"))
    }

    @Test
    fun singleEditsMatch() {
        // substitution
        assertEquals(IdMatcher.Score(IdMatcher.TIER_EDIT, 1), IdMatcher.score("13RPN00007", "13RPN00001"))
        // deletion
        assertEquals(1, IdMatcher.osaDistance("13RPN0001", "13RPN00001"))
        // insertion
        assertEquals(IdMatcher.Score(IdMatcher.TIER_EDIT, 1), IdMatcher.score("13RPNN00001", "13RPN00001"))
    }

    @Test
    fun transpositionCountsAsOneEdit() {
        assertEquals(1, IdMatcher.osaDistance("13PRN00001", "13RPN00001"))
        assertEquals(IdMatcher.Score(IdMatcher.TIER_EDIT, 1), IdMatcher.score("13PRN00001", "13RPN00001"))
    }

    @Test
    fun distanceStopsEarlyPastTheLimit() {
        assertEquals(3, IdMatcher.osaDistance("AAAA", "BBBB", 2))
        assertEquals(4, IdMatcher.osaDistance("AAAA", "BBBB"))
    }

    @Test
    fun shortIdsAllowOnlyOneEdit() {
        assertEquals(1, IdMatcher.maxDistance(4))
        assertTrue(rank("A12", listOf("A13")).isNotEmpty())
        assertTrue(rank("A12", listOf("B13")).isEmpty())
    }

    @Test
    fun longerIdsAllowMoreEditsUpToThree() {
        assertEquals(2, IdMatcher.maxDistance(10))
        assertEquals(3, IdMatcher.maxDistance(40))
        assertTrue(rank("13RPX00009").contains("13RPN00001"))
        assertTrue(rank("19RPX00009").isEmpty())
    }

    @Test
    fun resultsAreRankedByTierThenDistanceThenText() {
        val candidates = listOf("ABCDE12399", "ABCDE12354", "ABCDE123456", "ABCDE12344", "abcde12345")
        assertEquals(
            listOf(
                "abcde12345",   // case only
                "ABCDE123456",  // partial
                "ABCDE12344",   // one substitution, sorted before the transposition by text
                "ABCDE12354",   // one transposition
                "ABCDE12399"    // two substitutions
            ),
            rank("ABCDE12345", candidates)
        )
    }

    @Test
    fun resultsAreLimited() {
        val many = (0 until 20).map { "PLOT%02d".format(it) }
        assertEquals(IdMatcher.DEFAULT_LIMIT, rank("PLOT0", many).size)
    }

    @Test
    fun itemMatchedByTwoTextsIsReturnedOnceWithItsBestScore() {
        data class Unit(val id: String, val searchValue: String)

        val units = listOf(Unit("13RPN00001", "Plot 101"), Unit("13RPN00002", "Plot 102"))
        val matches = IdMatcher.rank("plot101", units, { listOf(it.id, it.searchValue) }, { it.id })

        assertEquals("13RPN00001", matches.first().item.id)
        assertEquals("Plot 101", matches.first().text)
        assertEquals(1, matches.count { it.item.id == "13RPN00001" })
    }

    @Test
    fun emptyInputMatchesNothing() {
        assertTrue(rank("").isEmpty())
        assertTrue(rank(" \n").isEmpty())
        assertTrue(rank(null).isEmpty())
    }
}
