import com.fieldbook.tracker.insights.InsightObservation
import com.fieldbook.tracker.insights.InsightStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import kotlin.math.sqrt

/**
 * Covers the arithmetic behind the field insights screen: summaries, axis ticks, binning, fits,
 * and the progress, grouping and layout calculations built on them.
 */
class InsightStatsTest {

    companion object {
        private const val DELTA = 1e-9

        private val DAY_ONE: LocalDate = LocalDate.of(2026, 6, 1)
        private val DAY_TWO: LocalDate = LocalDate.of(2026, 6, 2)
    }

    private fun observation(unit: String, trait: String = "t", day: LocalDate? = DAY_ONE, value: Double? = null) =
        InsightObservation(
            unitId = unit,
            traitId = trait,
            numeric = value,
            categories = emptyList(),
            collectedOn = day,
            collectedAt = null,
            collector = "",
            rep = 1
        )

    @Test
    fun summarizeComputesSampleStatistics() {
        val stats = InsightStats.summarize(listOf(5.0, 1.0, 4.0, 2.0, 3.0))!!

        assertEquals(5, stats.n)
        assertEquals(3.0, stats.mean, DELTA)
        assertEquals(sqrt(2.5), stats.sd, DELTA)
        assertEquals(1.0, stats.min, DELTA)
        assertEquals(2.0, stats.q1, DELTA)
        assertEquals(3.0, stats.median, DELTA)
        assertEquals(4.0, stats.q3, DELTA)
        assertEquals(5.0, stats.max, DELTA)
    }

    @Test
    fun summarizeHandlesEmptyAndSingleValues() {
        assertNull(InsightStats.summarize(emptyList()))

        val single = InsightStats.summarize(listOf(7.0))!!
        assertEquals(0.0, single.sd, DELTA)
        assertNull(single.coefficientOfVariation)
    }

    @Test
    fun quantileInterpolatesBetweenRanks() {
        assertEquals(2.5, InsightStats.quantile(listOf(1.0, 2.0, 3.0, 4.0), 0.5), DELTA)
        assertEquals(1.75, InsightStats.quantile(listOf(1.0, 2.0, 3.0, 4.0), 0.25), DELTA)
    }

    @Test
    fun niceDomainWidensToRoundTicks() {
        val domain = InsightStats.niceDomain(0.3, 9.7, 5)

        assertEquals(0.0, domain.min, DELTA)
        assertEquals(10.0, domain.max, DELTA)
        assertEquals(listOf(0.0, 2.0, 4.0, 6.0, 8.0, 10.0), domain.ticks)
    }

    @Test
    fun niceDomainPadsASingleValue() {
        val domain = InsightStats.niceDomain(50.0, 50.0, 4)

        assertTrue(domain.min < 50.0 && domain.max > 50.0)
    }

    @Test
    fun innerTicksStayInsideTheRange() {
        assertEquals(listOf(5.0, 10.0, 15.0), InsightStats.innerTicks(3.0, 17.0, 4, minStep = 1.0))
        assertEquals(listOf(4.0), InsightStats.innerTicks(4.0, 4.0))
    }

    @Test
    fun histogramGivesWholeNumbersABinEach() {
        val bins = InsightStats.histogram(listOf(1.0, 2.0, 2.0, 3.0, 3.0, 3.0, 4.0, 5.0))

        assertEquals(listOf(1, 2, 3, 1, 1), bins.map { it.count })
        assertEquals(0.5, bins.first().start, DELTA)
        assertEquals(5.5, bins.last().end, DELTA)
    }

    @Test
    fun histogramKeepsValuesOnAnEdgeInTheBinTheyStart() {
        // Width 0.2 from 0.0: 0.6 / 0.2 is 2.9999999999999996 in floating point.
        val bins = InsightStats.histogram(listOf(0.1, 0.2, 0.3, 0.4, 0.5, 0.6, 0.7, 0.8, 0.9))

        val binOfSix = bins.single { 0.6 >= it.start - 1e-9 && 0.6 < it.end - 1e-9 }
        assertEquals(0.6, binOfSix.start, 1e-9)
        assertEquals(listOf(1, 2, 2, 2, 2), bins.map { it.count })
    }

    @Test
    fun histogramCountsEveryValue() {
        val values = (0 until 200).map { it * 0.37 }
        val bins = InsightStats.histogram(values)

        assertEquals(values.size, bins.sumOf { it.count })
        assertTrue(bins.first().start <= values.min() && bins.last().end > values.max())
    }

    @Test
    fun linearFitRecoversALine() {
        val xs = listOf(0.0, 1.0, 2.0, 3.0)
        val fit = InsightStats.linearFit(xs, xs.map { 2 * it + 1 })!!

        assertEquals(2.0, fit.slope, DELTA)
        assertEquals(1.0, fit.intercept, DELTA)
        assertEquals(1.0, fit.r, DELTA)
        assertEquals(4, fit.n)
    }

    @Test
    fun linearFitNeedsVariationAndThreePoints() {
        assertNull(InsightStats.linearFit(listOf(1.0, 2.0, 3.0), listOf(4.0, 4.0, 4.0)))
        assertNull(InsightStats.linearFit(listOf(1.0, 2.0), listOf(1.0, 2.0)))
    }

    @Test
    fun correlationsPairOnSharedPlotsOnly() {
        val pairs = InsightStats.correlations(
            listOf("a", "b"),
            mapOf(
                "a" to mapOf("p1" to 1.0, "p2" to 2.0, "p3" to 3.0, "p4" to 4.0),
                "b" to mapOf("p1" to 8.0, "p2" to 6.0, "p3" to 4.0, "p9" to 100.0)
            )
        )

        assertEquals(1, pairs.size)
        assertEquals(-1.0, pairs[0].r, DELTA)
        assertEquals(3, pairs[0].n)
    }

    @Test
    fun filledCellsCountsEachEntryAndTraitOnce() {
        val filled = InsightStats.filledCells(
            listOf(
                observation("A", trait = "t"),
                observation("A", trait = "t"),
                observation("B", trait = "t"),
                observation("A", trait = "u")
            )
        )

        assertEquals(3, filled)
    }

    @Test
    fun dailyProgressCountsEachCellOnItsFirstDay() {
        val progress = InsightStats.dailyProgress(
            listOf(
                observation("A", day = DAY_ONE),
                observation("A", day = DAY_TWO),
                observation("B", day = DAY_TWO),
                observation("C", day = null)
            ),
            totalCells = 4
        )

        assertEquals(listOf(DAY_ONE, DAY_TWO), progress.map { it.day })
        assertEquals(listOf(1, 2), progress.map { it.observations })
        assertEquals(listOf(1, 2), progress.map { it.filled })
        assertEquals(50.0, progress.last().percent, DELTA)
    }

    @Test
    fun compareGroupsMeasuresVarianceExplained() {
        val comparison = InsightStats.compareGroups(
            listOf("a" to 1.0, "a" to 2.0, "a" to 3.0, "b" to 7.0, "b" to 8.0, "b" to 9.0)
        )!!

        assertEquals(2, comparison.groups.size)
        assertEquals(5.0, comparison.overall.mean, DELTA)
        assertEquals(54.0 / 58.0, comparison.varianceExplained!!, DELTA)
    }

    @Test
    fun compareGroupsLeavesASingleGroupUnexplained() {
        assertNull(InsightStats.compareGroups(listOf("a" to 1.0, "a" to 2.0))!!.varianceExplained)
    }

    @Test
    fun buildGridSortsNumericLabelsAsNumbers() {
        val grid = InsightStats.buildGrid(
            listOf("p1", "p2", "p3", "p4"),
            rowOf = mapOf("p1" to "1", "p2" to "10", "p3" to "2"),
            columnOf = mapOf("p1" to "1", "p2" to "1", "p3" to "2", "p4" to "1")
        )

        assertNotNull(grid)
        grid!!
        assertEquals(listOf("1", "2", "10"), grid.rowLabels)
        assertEquals(listOf("1", "2"), grid.columnLabels)
        assertEquals("p2", grid.unitAt(2, 0))
        assertEquals("p3", grid.unitAt(1, 1))
        assertNull(grid.unitAt(0, 1))
        assertEquals(1, grid.unplaced)
    }

    @Test
    fun spatialTrendFollowsRows() {
        val units = (1..12).map { "p$it" }
        val rows = units.withIndex().associate { (i, unit) -> unit to (i / 3 + 1).toString() }
        val columns = units.withIndex().associate { (i, unit) -> unit to (i % 3 + 1).toString() }
        val grid = InsightStats.buildGrid(units, rows, columns)!!

        // Values climb by 10 a row, with no change across columns.
        val values = units.withIndex().associate { (i, unit) -> unit to (i / 3) * 10.0 }

        assertEquals(10.0, InsightStats.spatialTrend(grid, values, byRow = true)!!.slope, DELTA)
        assertEquals(0.0, InsightStats.spatialTrend(grid, values, byRow = false)!!.slope, DELTA)
        assertEquals(listOf(0.0, 10.0, 20.0, 30.0), InsightStats.profile(grid, values, byRow = true).map { it.mean })
    }

    @Test
    fun guessesRowAndColumnAttributesByWholeToken() {
        val names = listOf("Grower", "plot_id", "Range", "col")

        assertEquals("Range", InsightStats.guessRowAttribute(names))
        assertEquals("col", InsightStats.guessColumnAttribute(names))
        assertEquals("row_num", InsightStats.guessRowAttribute(listOf("Grower", "row_num")))
        assertNull(InsightStats.guessColumnAttribute(listOf("Colour", "plot")))
    }
}
