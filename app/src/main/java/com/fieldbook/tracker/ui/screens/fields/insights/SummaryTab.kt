package com.fieldbook.tracker.ui.screens.fields.insights

import android.content.res.Resources
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.insights.FieldInsightsData
import com.fieldbook.tracker.insights.InsightStats
import com.fieldbook.tracker.insights.InsightTrait
import com.fieldbook.tracker.insights.InsightTraitKind
import com.fieldbook.tracker.ui.theme.AppTheme

private data class TraitSummaryRow(
    val trait: InsightTrait,
    val coverage: Float,
    /** Short label to value: n first, then what suits the trait (mean and SD, median, or mode). */
    val stats: List<Pair<String, String>>
)

@Composable
fun SummaryTab(
    data: FieldInsightsData,
    onOpenTrait: (String) -> Unit
) {
    val resources = LocalResources.current
    val palette = LocalInsightColors.current

    val computed = rememberOffMain(data, resources) { overview(data, resources) to traitRows(data, resources) }
    if (computed == null) {
        ComputingIndicator()
        return
    }
    val (overview, rows) = computed

    LazyColumn(contentPadding = PaddingValues(8.dp)) {

        item {
            InsightSection(
                title = stringResource(R.string.field_insights_overview),
                trailing = { CompletenessPie(overview.coverage, size = 56.dp) }
            ) {
                StatTileGrid(overview.tiles)
            }
        }

        item {
            InsightSection(title = stringResource(R.string.field_insights_traits)) {
                rows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = palette.gridline)
                    TraitRow(row) { onOpenTrait(row.trait.id) }
                }
            }
        }
    }
}

@Composable
private fun TraitRow(row: TraitSummaryRow, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = row.trait.name,
                style = AppTheme.typography.bodyStyle,
                color = AppTheme.colors.text.primary,
                fontWeight = FontWeight.Bold
            )

            // Each number sits over its own label, so they read as separate values rather than
            // running together in one line.
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                row.stats.forEach { (label, value) ->
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = value,
                            style = AppTheme.typography.bodyStyle,
                            color = AppTheme.colors.text.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(text = label, style = axisTextStyle(), maxLines = 1)
                    }
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        CompletenessPie(row.coverage, size = 48.dp)
    }
}

/**
 * The overview's tiles, and the field's completeness for its pie: the same share of entry and
 * trait pairings with a value that Progress shows for all traits.
 */
private class Overview(val tiles: List<StatTile>, val coverage: Float)

private fun overview(data: FieldInsightsData, resources: Resources): Overview {

    val units = data.unitIds.size
    val recorded = data.recorded(null)
    val coveredUnits = recorded.mapTo(HashSet()) { it.unitId }.size
    val days = data.observations.mapNotNullTo(HashSet()) { it.collectedOn }.size
    val collectors = data.observations.mapNotNullTo(HashSet()) { it.collector.ifBlank { null } }.size

    return Overview(
        tiles = listOf(
            StatTile(
                resources.getString(R.string.field_insights_plots_with_data),
                "${InsightFormat.count(coveredUnits)} / ${InsightFormat.count(units)}"
            ),
            StatTile(resources.getString(R.string.field_insights_observations), InsightFormat.count(data.observations.size)),
            StatTile(resources.getString(R.string.field_insights_traits), InsightFormat.count(data.traits.size)),
            StatTile(resources.getString(R.string.field_insights_collection_days), InsightFormat.count(days)),
            StatTile(resources.getString(R.string.field_insights_collectors), InsightFormat.count(collectors)),
        ),
        coverage = (units * data.traits.size).let { total ->
            if (total == 0) 0f else InsightStats.filledCells(recorded).toFloat() / total
        }
    )
}

private fun traitRows(data: FieldInsightsData, resources: Resources): List<TraitSummaryRow> =
    data.traits.map { trait ->

        val observations = data.observationsByTrait[trait.id].orEmpty()
        // NA counts as assessed here, though it adds nothing to n or the statistics.
        val coverage = if (data.unitIds.isEmpty()) 0f
        else data.recorded(trait.id).mapTo(HashSet()) { it.unitId }.size.toFloat() / data.unitIds.size
        val n = resources.getString(R.string.field_insights_n) to InsightFormat.count(observations.size)

        val center = when (trait.kind) {
            InsightTraitKind.NUMERIC -> InsightStats.summarize(observations.mapNotNull { it.numeric })?.let {
                listOf(
                    resources.getString(R.string.field_insights_mean) to InsightFormat.number(it.mean),
                    resources.getString(R.string.field_insights_sd_short) to InsightFormat.number(it.sd)
                )
            }

            InsightTraitKind.DATE -> InsightStats.summarize(observations.mapNotNull { it.numeric })?.let {
                listOf(resources.getString(R.string.field_insights_median) to InsightFormat.traitValue(trait, it.median))
            }

            InsightTraitKind.CATEGORICAL -> observations
                .flatMap { it.categories }
                .groupingBy { it }
                .eachCount()
                .maxByOrNull { it.value }
                ?.let { listOf(resources.getString(R.string.field_insights_most_common) to it.key) }

            InsightTraitKind.OTHER -> null
        }.orEmpty()

        TraitSummaryRow(trait, coverage, listOf(n) + center)
    }
