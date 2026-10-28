package com.fieldbook.tracker.insights

import java.time.LocalDate
import java.util.TreeMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class SummaryStats(
    val n: Int,
    val mean: Double,
    val sd: Double,
    val min: Double,
    val q1: Double,
    val median: Double,
    val q3: Double,
    val max: Double
) {
    /** Standard deviation as a percentage of the mean, or null where that is meaningless. */
    val coefficientOfVariation: Double?
        get() = if (n > 1 && mean != 0.0) sd / abs(mean) * 100.0 else null
}

data class HistogramBin(val start: Double, val end: Double, val count: Int)

data class LinearFit(val slope: Double, val intercept: Double, val r: Double, val n: Int) {
    fun at(x: Double) = intercept + slope * x
}

/**
 * One day of collection. [filled] is cumulative: the trait/plot cells holding a value by the end
 * of that day, out of [total].
 */
data class DayProgress(val day: LocalDate, val observations: Int, val filled: Int, val total: Int) {
    val percent: Double get() = if (total == 0) 0.0 else filled * 100.0 / total
}

data class GroupNumericSummary(val group: String, val stats: SummaryStats)

data class GroupComparison(
    val groups: List<GroupNumericSummary>,
    val overall: SummaryStats,
    /** Share of the total variation that lies between groups (eta squared), from 0 to 1. */
    val varianceExplained: Double?
)

data class GroupCategoryCounts(val group: String, val counts: Map<String, Int>) {
    val total: Int get() = counts.values.sum()
}

data class CorrelationPair(val traitA: String, val traitB: String, val r: Double, val n: Int)

data class AxisDomain(val min: Double, val max: Double, val ticks: List<Double>)

/**
 * Plots placed on a grid of distinct row and column values. Gaps in the numbering collapse, so a
 * field numbered 1, 2, 5 draws three adjacent rows.
 */
data class FieldGrid(
    val rowLabels: List<String>,
    val columnLabels: List<String>,
    /** Unit id per cell, keyed by row * columns + column. */
    val cells: Map<Int, String>,
    /** Row and column index per unit id. */
    val positions: Map<String, Pair<Int, Int>>,
    /** Units that had no row or column value and so are not on the grid. */
    val unplaced: Int
) {
    val rows: Int get() = rowLabels.size
    val columns: Int get() = columnLabels.size

    fun unitAt(row: Int, column: Int): String? = cells[row * columns + column]
}

/** The values along one grid row or column, summarized. */
data class ProfilePoint(val index: Int, val label: String, val stats: SummaryStats) {
    val mean: Double get() = stats.mean
    val n: Int get() = stats.n
}

object InsightStats {

    private val ROW_TOKEN_REGEX = Regex("rows?|ranges?", RegexOption.IGNORE_CASE)
    private val COLUMN_TOKEN_REGEX = Regex("cols?|columns?", RegexOption.IGNORE_CASE)
    private val TOKEN_DELIMITER_REGEX = Regex("[^A-Za-z]+")

    /** Tolerance for floating point error when a value is divided into bins. */
    private const val EDGE_EPSILON = 1e-9

    fun summarize(values: Collection<Double>): SummaryStats? {

        if (values.isEmpty()) return null

        val sorted = values.sorted()
        val n = sorted.size
        val mean = sorted.sum() / n
        val variance = if (n > 1) sorted.sumOf { (it - mean).pow(2) } / (n - 1) else 0.0

        return SummaryStats(
            n = n,
            mean = mean,
            sd = sqrt(variance),
            min = sorted.first(),
            q1 = quantile(sorted, 0.25),
            median = quantile(sorted, 0.5),
            q3 = quantile(sorted, 0.75),
            max = sorted.last()
        )
    }

    /** Linear interpolation between closest ranks; [sorted] must be ascending and not empty. */
    fun quantile(sorted: List<Double>, p: Double): Double {

        if (sorted.size == 1) return sorted[0]

        val position = p.coerceIn(0.0, 1.0) * (sorted.size - 1)
        val lower = floor(position).toInt()
        val upper = ceil(position).toInt()

        return sorted[lower] + (sorted[upper] - sorted[lower]) * (position - lower)
    }

    /** A step of 1, 2 or 5 times a power of ten that splits [span] into about [target] parts. */
    fun niceStep(span: Double, target: Int): Double {

        if (span <= 0.0 || span.isNaN() || span.isInfinite()) return 1.0

        val raw = span / target.coerceAtLeast(1)
        val magnitude = 10.0.pow(floor(log10(raw)))
        val nice = when (raw / magnitude) {
            in 0.0..1.0 -> 1.0
            in 1.0..2.0 -> 2.0
            in 2.0..5.0 -> 5.0
            else -> 10.0
        }

        return nice * magnitude
    }

    /** Widens [low]..[high] out to round tick values, so axes start and end on a labelled tick. */
    fun niceDomain(low: Double, high: Double, target: Int = 5, includeZero: Boolean = false): AxisDomain {

        var lo = if (includeZero) min(low, 0.0) else low
        var hi = if (includeZero) max(high, 0.0) else high

        if (!lo.isFinite() || !hi.isFinite() || !(hi - lo).isFinite()) return AxisDomain(0.0, 1.0, listOf(0.0, 1.0))

        if (lo == hi) {
            val pad = if (lo == 0.0) 1.0 else abs(lo) * 0.1
            lo -= pad
            hi += pad
        }

        val step = niceStep(hi - lo, target)
        val niceLo = floor(lo / step) * step
        val niceHi = ceil(hi / step) * step
        val count = ((niceHi - niceLo) / step).roundToInt()

        return AxisDomain(niceLo, niceHi, (0..count).map { niceLo + it * step })
    }

    /**
     * Round ticks inside [low]..[high] without widening it, for axes such as dates and grid
     * rows where the data's own ends should be the chart's ends. [minStep] keeps index axes on
     * whole numbers.
     */
    fun innerTicks(low: Double, high: Double, target: Int = 5, minStep: Double = 0.0): List<Double> {

        if (high <= low) return listOf(low)

        val step = max(niceStep(high - low, target), minStep)
        val first = ceil(low / step - 1e-9) * step
        val count = floor((high - first) / step + 1e-9).toInt()

        return (0..count).map { first + it * step }
    }

    /**
     * Bins on round widths, aiming for Sturges' bin count. Whole-number data keeps whole-number
     * bins, centred on each value when they are one wide, so a 1-9 rating gets a bar per score.
     */
    fun histogram(values: List<Double>, maxBins: Int = 24): List<HistogramBin> {

        if (values.isEmpty()) return emptyList()

        val lo = values.min()
        val hi = values.max()

        if (lo == hi) return listOf(HistogramBin(lo - 0.5, hi + 0.5, values.size))

        val allWhole = values.all { it == floor(it) }
        val target = (log2(values.size.toDouble()) + 1).roundToInt().coerceIn(5, maxBins)

        var width = niceStep(hi - lo, target)
        // Steps of 1 or more are already whole, so only smaller ones need lifting.
        if (allWhole) width = max(1.0, width)

        // The epsilon keeps a value on a bin edge in the bin it starts: 0.6 / 0.2 comes out as
        // 2.9999999999999996, which would otherwise truncate into the bin below.
        val start = if (allWhole && width == 1.0) lo - 0.5 else floor(lo / width + EDGE_EPSILON) * width
        val count = floor((hi - start) / width + EDGE_EPSILON).toInt() + 1
        val counts = IntArray(count)

        values.forEach { v ->
            counts[((v - start) / width + EDGE_EPSILON).toInt().coerceIn(0, count - 1)]++
        }

        return counts.mapIndexed { i, c -> HistogramBin(start + i * width, start + (i + 1) * width, c) }
    }

    /** Least squares line with Pearson's r. Null when either variable is constant or n < 3. */
    fun linearFit(xs: List<Double>, ys: List<Double>): LinearFit? {

        val n = min(xs.size, ys.size)
        if (n < 3) return null

        var meanX = 0.0
        var meanY = 0.0
        for (i in 0 until n) {
            meanX += xs[i]
            meanY += ys[i]
        }
        meanX /= n
        meanY /= n

        var sxx = 0.0
        var syy = 0.0
        var sxy = 0.0
        for (i in 0 until n) {
            val dx = xs[i] - meanX
            val dy = ys[i] - meanY
            sxx += dx * dx
            syy += dy * dy
            sxy += dx * dy
        }

        if (sxx == 0.0 || syy == 0.0) return null

        val slope = sxy / sxx

        return LinearFit(slope, meanY - slope * meanX, sxy / sqrt(sxx * syy), n)
    }

    /** Values of two traits on the plots where both were measured, in matching order. */
    fun paired(a: Map<String, Double>, b: Map<String, Double>): Pair<List<Double>, List<Double>> {

        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        a.forEach { (unit, x) ->
            b[unit]?.let { y ->
                xs.add(x)
                ys.add(y)
            }
        }

        return xs to ys
    }

    /** Pearson's r for every pair of traits, on plot means, using plots that have both. */
    fun correlations(
        traitIds: List<String>,
        plotValues: Map<String, Map<String, Double>>
    ): List<CorrelationPair> {

        val pairs = ArrayList<CorrelationPair>()

        for (i in traitIds.indices) {
            for (j in i + 1 until traitIds.size) {
                val a = plotValues[traitIds[i]] ?: continue
                val b = plotValues[traitIds[j]] ?: continue
                val (xs, ys) = paired(a, b)
                linearFit(xs, ys)?.let { fit ->
                    pairs.add(CorrelationPair(traitIds[i], traitIds[j], fit.r, fit.n))
                }
            }
        }

        return pairs
    }

    /**
     * Entry and trait pairings that hold at least one value. Out of entries × traits, this is
     * how complete a field is; repeats of the same pairing count once.
     */
    fun filledCells(observations: List<InsightObservation>): Int =
        observations.mapTo(HashSet()) { it.unitId + '\u0000' + it.traitId }.size

    /**
     * Collection by day. A cell is one plot for one trait, and counts as filled from the first
     * day it was recorded. [na] observations fill cells but are not counted as observations: an
     * entry assessed as NA is done, but has no value. Observations without a readable timestamp
     * are left out.
     */
    fun dailyProgress(
        observations: List<InsightObservation>,
        totalCells: Int,
        na: List<InsightObservation> = emptyList()
    ): List<DayProgress> {

        val firstDayByCell = HashMap<String, LocalDate>()
        val countByDay = TreeMap<LocalDate, Int>()

        observations.forEach { o ->
            val day = o.collectedOn ?: return@forEach
            countByDay[day] = (countByDay[day] ?: 0) + 1
        }

        (observations + na).forEach { o ->
            val day = o.collectedOn ?: return@forEach
            // A day with nothing but NA still moves completeness on, so it appears with no
            // observations.
            if (day !in countByDay) countByDay[day] = 0
            val cell = o.unitId + '\u0000' + o.traitId
            val previous = firstDayByCell[cell]
            if (previous == null || day < previous) firstDayByCell[cell] = day
        }

        val newCellsByDay = firstDayByCell.values.groupingBy { it }.eachCount()

        var filled = 0
        return countByDay.map { (day, count) ->
            filled += newCellsByDay[day] ?: 0
            DayProgress(day, count, filled, totalCells)
        }
    }

    /** Splits (group, value) pairs by group and measures how much of the spread the grouping explains. */
    fun compareGroups(values: List<Pair<String, Double>>): GroupComparison? {

        val overall = summarize(values.map { it.second }) ?: return null

        val groups = values
            .groupBy({ it.first }, { it.second })
            .mapNotNull { (group, groupValues) ->
                summarize(groupValues)?.let { GroupNumericSummary(group, it) }
            }

        val totalSquares = values.sumOf { (it.second - overall.mean).pow(2) }
        val betweenSquares = groups.sumOf { it.stats.n * (it.stats.mean - overall.mean).pow(2) }
        val explained = if (groups.size > 1 && totalSquares > 0.0) betweenSquares / totalSquares else null

        return GroupComparison(groups, overall, explained)
    }

    fun compareCategoryGroups(values: List<Pair<String, String>>): List<GroupCategoryCounts> =
        values
            .groupBy({ it.first }, { it.second })
            .map { (group, categories) -> GroupCategoryCounts(group, categories.groupingBy { it }.eachCount()) }

    fun buildGrid(
        unitIds: List<String>,
        rowOf: Map<String, String>,
        columnOf: Map<String, String>
    ): FieldGrid? {

        val placed = unitIds.mapNotNull { id ->
            val row = rowOf[id]?.trim()
            val column = columnOf[id]?.trim()
            if (row.isNullOrEmpty() || column.isNullOrEmpty()) null else Triple(id, row, column)
        }

        if (placed.isEmpty()) return null

        val rowLabels = sortLabels(placed.map { it.second }.distinct())
        val columnLabels = sortLabels(placed.map { it.third }.distinct())
        val rowIndex = rowLabels.withIndex().associate { it.value to it.index }
        val columnIndex = columnLabels.withIndex().associate { it.value to it.index }

        val cells = HashMap<Int, String>()
        val positions = HashMap<String, Pair<Int, Int>>()
        placed.forEach { (id, row, column) ->
            val r = rowIndex.getValue(row)
            val c = columnIndex.getValue(column)
            cells[r * columnLabels.size + c] = id
            positions[id] = r to c
        }

        return FieldGrid(rowLabels, columnLabels, cells, positions, unitIds.size - placed.size)
    }

    /** Summary of the values in each grid row (or column), in grid order. */
    fun profile(grid: FieldGrid, values: Map<String, Double>, byRow: Boolean): List<ProfilePoint> {

        val byIndex = HashMap<Int, MutableList<Double>>()
        values.forEach { (unit, value) ->
            val (row, column) = grid.positions[unit] ?: return@forEach
            byIndex.getOrPut(if (byRow) row else column) { ArrayList() }.add(value)
        }

        val labels = if (byRow) grid.rowLabels else grid.columnLabels

        return byIndex.keys.sorted().map { index ->
            ProfilePoint(index, labels[index], summarize(byIndex.getValue(index))!!)
        }
    }

    /** Fits value against grid row (or column) index, plot by plot. */
    fun spatialTrend(grid: FieldGrid, values: Map<String, Double>, byRow: Boolean): LinearFit? {

        val xs = ArrayList<Double>()
        val ys = ArrayList<Double>()
        values.forEach { (unit, value) ->
            val (row, column) = grid.positions[unit] ?: return@forEach
            xs.add((if (byRow) row else column).toDouble())
            ys.add(value)
        }

        return linearFit(xs, ys)
    }

    /** Numeric labels sort as numbers; anything else sorts as text. */
    fun sortLabels(labels: List<String>): List<String> =
        if (labels.all { it.toDoubleOrNull() != null }) labels.sortedBy { it.toDouble() }
        else labels.sortedWith(String.CASE_INSENSITIVE_ORDER)

    /** The first attribute named like a row (row, rows, range, ranges), as the data grid map does. */
    fun guessRowAttribute(names: List<String>): String? = guessAttribute(names, ROW_TOKEN_REGEX)

    /** The first attribute named like a column (col, cols, column, columns). */
    fun guessColumnAttribute(names: List<String>): String? = guessAttribute(names, COLUMN_TOKEN_REGEX)

    private fun guessAttribute(names: List<String>, token: Regex): String? =
        names.firstOrNull { name ->
            name.split(TOKEN_DELIMITER_REGEX).filter { it.isNotEmpty() }.any { it.matches(token) }
        }
}
