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
import com.fieldbook.tracker.insights.DayProgress
import com.fieldbook.tracker.insights.FieldInsightsData
import com.fieldbook.tracker.insights.InsightStats
import com.fieldbook.tracker.insights.InsightTrait

private val percentDomain = AxisDomain(0.0, 100.0, listOf(0.0, 25.0, 50.0, 75.0, 100.0))

/**
 * How collection has gone over time: coverage building up, the pace per day, and who recorded
 * the values. Scoped to one trait or to all of them.
 */
@Composable
fun ProgressTab(
    data: FieldInsightsData,
    trait: InsightTrait?,
    onTraitChange: (InsightTrait?) -> Unit
) {
    val allTraits = stringResource(R.string.field_insights_all_traits)

    val stats = rememberOffMain(data, trait) { progressStats(data, trait) }

    Column(modifier = Modifier.fillMaxSize()) {

        FilterRow {
            InsightDropdown(
                label = stringResource(R.string.field_insights_trait),
                selectedText = trait?.name ?: allTraits,
                options = listOf<InsightTrait?>(null) + data.traits,
                optionText = { it?.name ?: allTraits },
                onSelect = onTraitChange,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (stats == null) ComputingIndicator() else ProgressCharts(stats, trait)
    }
}

@Composable
private fun ProgressCharts(stats: ProgressStats, trait: InsightTrait?) {

    val resources = LocalResources.current
    val unknownCollector = stringResource(R.string.field_insights_collector_unknown)

    val totalCells = stats.totalCells
    val filled = stats.filled
    val progress = stats.progress
    val undated = stats.undated
    val collectorCounts = stats.collectorCounts

    LazyColumn(contentPadding = PaddingValues(8.dp)) {

        item {
            // With one trait the pie is entries with a value; with all of them, it is the share of
            // every entry and trait pairing that has one.
            InsightSection(
                title = stringResource(R.string.field_insights_tab_progress),
                trailing = { CompletenessPie(if (totalCells == 0) 0f else filled.toFloat() / totalCells, size = 56.dp) }
            ) {
                StatTileGrid(
                    listOfNotNull(
                        trait?.let {
                            StatTile(
                                stringResource(R.string.field_insights_plots_filled),
                                "${InsightFormat.count(filled)} / ${InsightFormat.count(totalCells)}"
                            )
                        },
                        StatTile(
                            stringResource(R.string.field_insights_observations),
                            InsightFormat.count(stats.observationCount)
                        ),
                        StatTile(
                            stringResource(R.string.field_insights_collection_days),
                            InsightFormat.count(progress.size)
                        ),
                        StatTile(
                            stringResource(R.string.field_insights_collectors),
                            InsightFormat.count(collectorCounts.count { it.first.isNotBlank() })
                        ),
                    )
                )
            }
        }

        if (progress.isNotEmpty()) {

            val firstDay = progress.first().day.toEpochDay().toDouble()
            val lastDay = progress.last().day.toEpochDay().toDouble()
            val dayTicks = InsightStats.innerTicks(firstDay, lastDay, 4, minStep = 1.0)

            item {
                InsightSection(title = stringResource(R.string.field_insights_coverage_over_time)) {
                    val padding = if (firstDay == lastDay) 1.0 else 0.0
                    LineChart(
                        points = progress.map { it.day.toEpochDay().toDouble() to it.percent },
                        x = AxisDomain(firstDay - padding, lastDay + padding, dayTicks),
                        y = percentDomain,
                        xLabel = InsightFormat::epochDay,
                        yLabel = InsightFormat::percent,
                        fillArea = true,
                        describe = { i ->
                            val day = progress[i]
                            resources.getString(
                                R.string.field_insights_progress_readout,
                                InsightFormat.day(day.day),
                                InsightFormat.percent(day.percent),
                                InsightFormat.count(day.filled),
                                InsightFormat.count(day.total)
                            )
                        }
                    )
                    if (undated > 0) {
                        InsightNote(
                            stringResource(R.string.field_insights_undated_note, InsightFormat.count(undated))
                        )
                    }
                }
            }

            item {
                InsightSection(title = stringResource(R.string.field_insights_observations_per_day)) {
                    RangeBarChart(
                        bars = progress.map {
                            val day = it.day.toEpochDay().toDouble()
                            RangeBar(day - 0.4, day + 0.4, it.observations.toDouble())
                        },
                        x = AxisDomain(firstDay - 0.5, lastDay + 0.5, dayTicks),
                        y = InsightStats.niceDomain(0.0, progress.maxOf { it.observations }.toDouble(), 4, includeZero = true),
                        xLabel = InsightFormat::epochDay,
                        yLabel = InsightFormat::number,
                        describe = { i ->
                            resources.getString(
                                R.string.field_insights_day_readout,
                                InsightFormat.day(progress[i].day),
                                InsightFormat.count(progress[i].observations)
                            )
                        }
                    )
                }
            }
        }

        item {
            InsightSection(title = stringResource(R.string.field_insights_by_collector)) {
                HorizontalBarList(
                    items = collectorCounts.map { (collector, count) ->
                        BarItem(collector.ifBlank { unknownCollector }, count)
                    },
                    total = stats.observationCount
                )
            }
        }
    }
}

private class ProgressStats(
    val observationCount: Int,
    val totalCells: Int,
    val filled: Int,
    val undated: Int,
    val progress: List<DayProgress>,
    val collectorCounts: List<Pair<String, Int>>
)

/** Everything the tab shows for one trait, or for all of them when [trait] is null. */
private fun progressStats(data: FieldInsightsData, trait: InsightTrait?): ProgressStats {

    val observations = trait?.let { data.observationsByTrait[it.id].orEmpty() } ?: data.observations
    val totalCells = data.unitIds.size * (if (trait == null) data.traits.size else 1)

    return ProgressStats(
        observationCount = observations.size,
        totalCells = totalCells,
        filled = InsightStats.filledCells(data.recorded(trait?.id)),
        undated = observations.count { it.collectedOn == null },
        progress = InsightStats.dailyProgress(observations, totalCells, na = data.naFor(trait?.id)),
        collectorCounts = observations.groupingBy { it.collector }.eachCount().toList().sortedByDescending { it.second }
    )
}
