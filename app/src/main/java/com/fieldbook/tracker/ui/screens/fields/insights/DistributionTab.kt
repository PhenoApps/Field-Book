package com.fieldbook.tracker.ui.screens.fields.insights

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.insights.AxisDomain
import com.fieldbook.tracker.insights.FieldInsightsData
import com.fieldbook.tracker.insights.InsightObservation
import com.fieldbook.tracker.insights.InsightStats
import com.fieldbook.tracker.insights.InsightTrait
import com.fieldbook.tracker.insights.InsightTraitKind
import kotlin.math.floor

/** Timed scatters color by collector only while the colors stay distinguishable on every pair. */
private const val MAX_COLLECTOR_COLORS = 3

/** One trait's values: their spread, and whether they shifted while they were being collected. */
@Composable
fun DistributionTab(
    data: FieldInsightsData,
    trait: InsightTrait,
    onTraitChange: (InsightTrait) -> Unit
) {
    val observations = data.observationsByTrait[trait.id].orEmpty()

    Column(modifier = Modifier.fillMaxSize()) {

        FilterRow {
            TraitDropdown(data.traits, trait, onTraitChange, Modifier.fillMaxWidth())
        }

        when (trait.kind) {
            InsightTraitKind.NUMERIC, InsightTraitKind.DATE -> QuantitativeDistribution(trait, observations)
            InsightTraitKind.CATEGORICAL -> CategoricalDistribution(trait, observations)
            InsightTraitKind.OTHER -> LazyColumn(contentPadding = PaddingValues(8.dp)) {
                item {
                    val covered = data.recorded(trait.id).mapTo(HashSet()) { it.unitId }.size
                    InsightSection(
                        title = stringResource(R.string.field_insights_overview),
                        trailing = {
                            CompletenessPie(
                                if (data.unitIds.isEmpty()) 0f else covered.toFloat() / data.unitIds.size,
                                size = 56.dp
                            )
                        }
                    ) {
                        StatTileGrid(
                            listOf(
                                StatTile(
                                    stringResource(R.string.field_insights_plots_with_data),
                                    "${InsightFormat.count(covered)} / ${InsightFormat.count(data.unitIds.size)}"
                                ),
                                StatTile(
                                    stringResource(R.string.field_insights_observations),
                                    InsightFormat.count(observations.size)
                                ),
                            )
                        )
                        InsightNote(stringResource(R.string.field_insights_not_chartable))
                    }
                }
            }
        }
    }
}

@Composable
private fun QuantitativeDistribution(
    trait: InsightTrait,
    observations: List<InsightObservation>
) {
    val resources = LocalResources.current
    val palette = LocalInsightColors.current
    val unknownCollector = stringResource(R.string.field_insights_collector_unknown)

    val values = remember(observations) { observations.mapNotNull { it.numeric } }
    val stats = remember(values) { InsightStats.summarize(values) }
    val bins = remember(values) { InsightStats.histogram(values) }
    val timed = remember(observations) {
        observations
            .filter { it.numeric != null && it.collectedAt != null }
            .sortedBy { it.collectedAt }
    }
    val times = remember(timed) { timed.map { InsightFormat.localEpochDay(it.collectedAt!!) } }
    val fit = remember(timed) { InsightStats.linearFit(times, timed.map { it.numeric!! }) }
    val collectors = remember(timed) { timed.map { it.collector }.distinct() }

    val format = { value: Double -> InsightFormat.traitValue(trait, value) }

    if (stats == null) {
        InsightMessage(stringResource(R.string.field_insights_no_values))
        return
    }

    LazyColumn(contentPadding = PaddingValues(8.dp)) {

        item {
            InsightSection(title = stringResource(R.string.field_insights_overview)) {
                StatTileGrid(
                    if (trait.kind == InsightTraitKind.DATE) listOf(
                        StatTile(stringResource(R.string.field_insights_observations), InsightFormat.count(stats.n)),
                        StatTile(stringResource(R.string.field_insights_median), format(stats.median)),
                        StatTile(stringResource(R.string.field_insights_earliest), format(stats.min)),
                        StatTile(stringResource(R.string.field_insights_latest), format(stats.max)),
                    ) else listOf(
                        StatTile(stringResource(R.string.field_insights_observations), InsightFormat.count(stats.n)),
                        StatTile(stringResource(R.string.field_insights_mean), format(stats.mean)),
                        StatTile(stringResource(R.string.field_insights_median), format(stats.median)),
                        StatTile(stringResource(R.string.field_insights_sd), InsightFormat.number(stats.sd)),
                        StatTile(
                            stringResource(R.string.field_insights_cv),
                            stats.coefficientOfVariation?.let { InsightFormat.percent(it) } ?: "–"
                        ),
                        StatTile(
                            stringResource(R.string.field_insights_range),
                            "${format(stats.min)} – ${format(stats.max)}"
                        ),
                    )
                )
            }
        }

        item {
            InsightSection(title = stringResource(R.string.field_insights_distribution)) {
                val start = bins.first().start
                val end = bins.last().end
                RangeBarChart(
                    bars = bins.map { RangeBar(it.start, it.end, it.count.toDouble()) },
                    x = AxisDomain(start, end, InsightStats.innerTicks(start, end, 5)),
                    y = InsightStats.niceDomain(0.0, bins.maxOf { it.count }.toDouble(), 4, includeZero = true),
                    xLabel = format,
                    yLabel = InsightFormat::number,
                    references = listOf(
                        ReferenceLine(
                            if (trait.kind == InsightTraitKind.DATE) stats.median else stats.mean,
                            stringResource(
                                if (trait.kind == InsightTraitKind.DATE) R.string.field_insights_median
                                else R.string.field_insights_mean
                            )
                        )
                    ),
                    describe = { i ->
                        val bin = bins[i]
                        val share = InsightFormat.percent(bin.count * 100.0 / stats.n)
                        val centre = (bin.start + bin.end) / 2
                        if (bin.end - bin.start == 1.0 && centre == floor(centre)) {
                            resources.getString(
                                R.string.field_insights_value_readout,
                                format(centre),
                                InsightFormat.count(bin.count),
                                share
                            )
                        } else {
                            resources.getString(
                                R.string.field_insights_bin_readout,
                                format(bin.start),
                                format(bin.end),
                                InsightFormat.count(bin.count),
                                share
                            )
                        }
                    }
                )
            }
        }

        if (timed.size >= 3) {
            item {
                val colorByCollector = collectors.size in 2..MAX_COLLECTOR_COLORS
                val first = times.first()
                val last = times.last()
                val withTime = last - first < 2
                val padding = if (last == first) 0.5 else 0.0

                InsightSection(
                    title = stringResource(R.string.field_insights_over_time),
                    subtitle = fit?.let {
                        stringResource(
                            R.string.field_insights_over_time_trend,
                            (if (it.slope >= 0) "+" else "") + InsightFormat.number(it.slope),
                            InsightFormat.correlation(it.r)
                        )
                    }
                ) {
                    if (colorByCollector) {
                        SwatchLegend(
                            collectors.mapIndexed { i, collector ->
                                LegendItem(collector.ifBlank { unknownCollector }, palette.category(i))
                            }
                        )
                    }
                    ScatterChart(
                        points = timed.mapIndexed { i, observation ->
                            ScatterPoint(
                                x = times[i],
                                y = observation.numeric!!,
                                group = if (colorByCollector) collectors.indexOf(observation.collector) else 0
                            )
                        },
                        x = AxisDomain(
                            first - padding,
                            last + padding,
                            InsightStats.innerTicks(first - padding, last + padding, 3, minStep = if (withTime) 0.0 else 1.0)
                        ),
                        y = InsightStats.niceDomain(stats.min, stats.max, 4),
                        xLabel = { InsightFormat.localDay(it, withTime) },
                        yLabel = format,
                        groupColors = if (colorByCollector) palette.categorical.take(collectors.size) else null,
                        fit = fit,
                        describe = { i ->
                            val observation = timed[i]
                            resources.getString(
                                R.string.field_insights_timed_readout,
                                observation.unitId,
                                format(observation.numeric!!),
                                InsightFormat.localDay(times[i], true),
                                observation.collector.ifBlank { unknownCollector }
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoricalDistribution(
    trait: InsightTrait,
    observations: List<InsightObservation>
) {
    val counts = remember(observations) { observations.flatMap { it.categories }.groupingBy { it }.eachCount() }
    val total = counts.values.sum()

    // Defined categories keep their configured order, including any never chosen, so a gap is
    // visible; values outside the definition follow by count.
    val order = remember(trait, counts) {
        trait.categoryOrder.distinct() +
                counts.keys.filter { it !in trait.categoryOrder }.sortedByDescending { counts[it] }
    }
    val mostCommon = counts.maxByOrNull { it.value }

    LazyColumn(contentPadding = PaddingValues(8.dp)) {

        item {
            InsightSection(title = stringResource(R.string.field_insights_overview)) {
                StatTileGrid(
                    listOf(
                        StatTile(
                            stringResource(R.string.field_insights_observations),
                            InsightFormat.count(observations.size)
                        ),
                        StatTile(
                            stringResource(R.string.field_insights_categories_used),
                            if (trait.categoryOrder.isEmpty()) InsightFormat.count(counts.size)
                            else "${InsightFormat.count(counts.keys.count { it in trait.categoryOrder })} / ${InsightFormat.count(trait.categoryOrder.distinct().size)}"
                        ),
                        StatTile(
                            stringResource(R.string.field_insights_most_common),
                            mostCommon?.let {
                                "${it.key} (${InsightFormat.percent(it.value * 100.0 / total)})"
                            } ?: "–"
                        ),
                    )
                )
            }
        }

        item {
            InsightSection(title = stringResource(R.string.field_insights_counts)) {
                if (total == 0) {
                    InsightNote(stringResource(R.string.field_insights_no_values))
                } else {
                    HorizontalBarList(
                        items = order.map { BarItem(it, counts[it] ?: 0) },
                        total = total
                    )
                }
            }
        }
    }
}
