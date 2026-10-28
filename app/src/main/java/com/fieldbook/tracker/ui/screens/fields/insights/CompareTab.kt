package com.fieldbook.tracker.ui.screens.fields.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.insights.AxisDomain
import com.fieldbook.tracker.insights.FieldInsightsData
import com.fieldbook.tracker.insights.GroupCategoryCounts
import com.fieldbook.tracker.insights.GroupNumericSummary
import com.fieldbook.tracker.insights.InsightObservation
import com.fieldbook.tracker.insights.InsightStats
import com.fieldbook.tracker.insights.InsightTrait
import com.fieldbook.tracker.insights.InsightTraitKind
import com.fieldbook.tracker.ui.theme.AppTheme
import kotlin.math.max

/** Groupings with more distinct values than this are almost certainly plot identifiers. */
private const val MAX_GROUPS = 300

private const val LABEL_WEIGHT = 0.3f
private const val PLOT_WEIGHT = 0.5f
private const val VALUE_WEIGHT = 0.2f

private val rowPadding = 24.dp
private val boxHeight = 12.dp

private val PREFERRED_GROUPING_REGEX =
    Regex("rep|replicate|block|treatment|trt|entry|genotype|germplasm|accession|variety|line", RegexOption.IGNORE_CASE)

private enum class GroupingKind { ATTRIBUTE, COLLECTOR, DAY }

private data class Grouping(val key: String, val label: String, val kind: GroupingKind)

private enum class GroupSort { VALUE, NAME }

/**
 * One trait split by a grouping: a field attribute such as rep or entry, who collected it, or
 * which day. Shows whether the groups really differ, and by how much.
 */
@Composable
fun CompareTab(
    data: FieldInsightsData,
    trait: InsightTrait,
    onTraitChange: (InsightTrait) -> Unit
) {
    val collectorLabel = stringResource(R.string.field_insights_group_collector)
    val dayLabel = stringResource(R.string.field_insights_group_day)
    val unknownCollector = stringResource(R.string.field_insights_collector_unknown)

    val groupings = remember(data, collectorLabel, dayLabel) {
        data.attributes
            .filter { (_, values) -> values.values.toSet().size.let { it in 2..MAX_GROUPS && it < values.size } }
            .keys
            .map { Grouping("attribute:$it", it, GroupingKind.ATTRIBUTE) } +
                Grouping("collector", collectorLabel, GroupingKind.COLLECTOR) +
                Grouping("day", dayLabel, GroupingKind.DAY)
    }

    var groupingKey by rememberSaveable {
        mutableStateOf(
            groupings.firstOrNull {
                it.kind == GroupingKind.ATTRIBUTE && PREFERRED_GROUPING_REGEX.containsMatchIn(it.label)
            }?.key ?: groupings.first().key
        )
    }
    var sort by rememberSaveable { mutableStateOf(GroupSort.VALUE) }

    val grouping = groupings.firstOrNull { it.key == groupingKey } ?: groupings.first()

    val groupOf: (InsightObservation) -> String? = remember(data, grouping, unknownCollector) {
        when (grouping.kind) {
            GroupingKind.ATTRIBUTE -> {
                val values = data.attributes[grouping.label].orEmpty()
                ({ observation: InsightObservation -> values[observation.unitId] })
            }

            GroupingKind.COLLECTOR -> ({ observation: InsightObservation -> observation.collector.ifBlank { unknownCollector } })

            // ISO dates sort in date order as text.
            GroupingKind.DAY -> ({ observation: InsightObservation -> observation.collectedOn?.toString() })
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {

        FilterRow {
            TraitDropdown(data.traits, trait, onTraitChange, Modifier.fillMaxWidth())
            InsightDropdown(
                label = stringResource(R.string.field_insights_group_by),
                selectedText = grouping.label,
                options = groupings,
                optionText = { it.label },
                onSelect = { groupingKey = it.key },
                modifier = Modifier.fillMaxWidth()
            )
            ChoiceChips(
                options = listOf(
                    GroupSort.VALUE to stringResource(R.string.field_insights_sort_value),
                    GroupSort.NAME to stringResource(R.string.field_insights_sort_name)
                ),
                selected = sort,
                onSelect = { sort = it }
            )
        }

        val observations = data.observationsByTrait[trait.id].orEmpty()

        when (trait.kind) {
            InsightTraitKind.NUMERIC, InsightTraitKind.DATE ->
                NumericComparison(trait, grouping, observations, groupOf, sort)

            InsightTraitKind.CATEGORICAL ->
                CategoricalComparison(trait, grouping, observations, groupOf, sort)

            InsightTraitKind.OTHER ->
                InsightMessage(stringResource(R.string.field_insights_not_chartable))
        }
    }
}

@Composable
private fun NumericComparison(
    trait: InsightTrait,
    grouping: Grouping,
    observations: List<InsightObservation>,
    groupOf: (InsightObservation) -> String?,
    sort: GroupSort
) {
    val resources = LocalResources.current
    val format = { value: Double -> InsightFormat.traitValue(trait, value) }

    // Wrapped in a list so that null means still computing and empty means nothing to compare.
    val computed = rememberOffMain(observations, groupOf) {
        listOfNotNull(
            InsightStats.compareGroups(
                observations.mapNotNull { observation ->
                    val value = observation.numeric ?: return@mapNotNull null
                    groupOf(observation)?.let { it to value }
                }
            )
        )
    }

    if (computed == null) {
        ComputingIndicator()
        return
    }

    val comparison = computed.firstOrNull()
    if (comparison == null) {
        InsightMessage(stringResource(R.string.field_insights_compare_no_groups, grouping.label))
        return
    }

    val groups = remember(comparison, sort) {
        when (sort) {
            GroupSort.VALUE -> comparison.groups.sortedByDescending { it.stats.mean }
            GroupSort.NAME -> InsightStats.sortLabels(comparison.groups.map { it.group })
                .let { order -> comparison.groups.sortedBy { order.indexOf(it.group) } }
        }
    }
    val domain = remember(comparison) { InsightStats.niceDomain(comparison.overall.min, comparison.overall.max, 4) }
    var selected by remember(comparison) { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {

        item {
            InsightSection(
                title = stringResource(R.string.field_insights_compare_title, trait.name, grouping.label),
                subtitle = comparison.varianceExplained?.let {
                    stringResource(
                        R.string.field_insights_compare_explained,
                        grouping.label,
                        InsightFormat.percent(it * 100),
                        trait.name
                    )
                },
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {}
        }

        item { AxisHeader(domain, format) }

        items(groups, key = { it.group }) { summary ->
            GroupRangeRow(
                summary = summary,
                domain = domain,
                overallMean = comparison.overall.mean,
                format = format,
                detail = if (summary.group != selected) null else resources.getString(
                    R.string.field_insights_group_readout,
                    InsightFormat.count(summary.stats.n),
                    format(summary.stats.mean),
                    format(summary.stats.median),
                    format(summary.stats.min),
                    format(summary.stats.max)
                ),
                onClick = { selected = if (selected == summary.group) null else summary.group }
            )
        }
    }
}

/** Tick labels lined up over the plotting column of the rows below. */
@Composable
private fun AxisHeader(domain: AxisDomain, format: (Double) -> String) {
    val measurer = rememberTextMeasurer()
    val style = axisTextStyle()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = rowPadding)
            .height(20.dp)
    ) {
        Spacer(modifier = Modifier.weight(LABEL_WEIGHT))
        Canvas(
            modifier = Modifier
                .weight(PLOT_WEIGHT)
                .fillMaxHeight()
        ) {
            var lastRight = Float.NEGATIVE_INFINITY
            domain.ticks.forEach { tick ->
                val layout = measurer.measure(format(tick), style)
                val x = fraction(tick, domain) * size.width
                val left = (x - layout.size.width / 2f).coerceIn(0f, max(0f, size.width - layout.size.width))
                if (left < lastRight + 4.dp.toPx()) return@forEach
                drawText(layout, topLeft = Offset(left, (size.height - layout.size.height) / 2f))
                lastRight = left + layout.size.width
            }
        }
        Spacer(modifier = Modifier.weight(VALUE_WEIGHT))
    }
}

/**
 * One group as a range strip: the whisker spans min to max, the box the middle half, with a tick
 * at the median and a dot at the mean. The vertical rule is the mean of all groups.
 */
@Composable
private fun GroupRangeRow(
    summary: GroupNumericSummary,
    domain: AxisDomain,
    overallMean: Double,
    format: (Double) -> String,
    detail: String?,
    onClick: () -> Unit
) {
    val palette = LocalInsightColors.current
    val stats = summary.stats

    GroupRow(
        label = summary.group,
        value = format(stats.mean),
        detail = detail,
        onClick = onClick
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val centerY = size.height / 2f
            val half = boxHeight.toPx() / 2f
            fun x(value: Double) = fraction(value, domain) * size.width

            domain.ticks.forEach { tick ->
                drawLine(palette.gridline, Offset(x(tick), 0f), Offset(x(tick), size.height), 1.dp.toPx())
            }
            drawLine(palette.ink.copy(alpha = 0.5f), Offset(x(overallMean), 0f), Offset(x(overallMean), size.height), 1.5.dp.toPx())

            drawLine(palette.other, Offset(x(stats.min), centerY), Offset(x(stats.max), centerY), 1.5.dp.toPx())

            val boxLeft = x(stats.q1)
            val boxWidth = max(x(stats.q3) - boxLeft, 1f)
            drawRect(palette.series.copy(alpha = 0.25f), Offset(boxLeft, centerY - half), Size(boxWidth, half * 2))
            drawRect(palette.series, Offset(boxLeft, centerY - half), Size(boxWidth, half * 2), style = Stroke(1.dp.toPx()))
            drawLine(palette.series, Offset(x(stats.median), centerY - half), Offset(x(stats.median), centerY + half), 2.dp.toPx())

            drawCircle(palette.surface, 5.dp.toPx(), Offset(x(stats.mean), centerY))
            drawCircle(palette.ink, 4.dp.toPx(), Offset(x(stats.mean), centerY))
        }
    }
}

@Composable
private fun CategoricalComparison(
    trait: InsightTrait,
    grouping: Grouping,
    observations: List<InsightObservation>,
    groupOf: (InsightObservation) -> String?,
    sort: GroupSort
) {
    val resources = LocalResources.current
    val other = stringResource(R.string.field_insights_other)

    val groups = rememberOffMain(observations, groupOf) {
        InsightStats.compareCategoryGroups(
            observations.flatMap { observation ->
                val group = groupOf(observation) ?: return@flatMap emptyList()
                observation.categories.map { group to it }
            }
        )
    }

    if (groups == null) {
        ComputingIndicator()
        return
    }

    if (groups.isEmpty()) {
        InsightMessage(stringResource(R.string.field_insights_compare_no_groups, grouping.label))
        return
    }

    val palette = LocalInsightColors.current
    val colors = remember(trait, groups, palette) { CategoryColors(trait, groups.flatMap { it.counts.keys }.toSet(), palette) }

    // Sorting by value ranks groups by their share of the field's most common category.
    val leading = remember(groups) {
        groups.flatMap { it.counts.entries }
            .groupBy({ it.key }, { it.value })
            .maxByOrNull { (_, counts) -> counts.sum() }
            ?.key
    }
    val sorted = remember(groups, sort, leading) {
        when (sort) {
            GroupSort.VALUE -> groups.sortedByDescending { (it.counts[leading] ?: 0).toDouble() / it.total }
            GroupSort.NAME -> InsightStats.sortLabels(groups.map { it.group })
                .let { order -> groups.sortedBy { order.indexOf(it.group) } }
        }
    }
    var selected by remember(groups) { mutableStateOf<String?>(null) }

    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {

        item {
            InsightSection(
                title = stringResource(R.string.field_insights_compare_title, trait.name, grouping.label),
                modifier = Modifier.padding(horizontal = 8.dp)
            ) {
                SwatchLegend(
                    colors.order.filterNot(colors::isOther).map { LegendItem(it, colors.colorOf(it)) } +
                            (if (colors.hasOther) listOf(LegendItem(other, palette.other)) else emptyList())
                )
            }
        }

        items(sorted, key = { it.group }) { group ->
            GroupShareRow(
                group = group,
                colors = colors,
                detail = if (group.group != selected) null else colors.order
                    .filter { (group.counts[it] ?: 0) > 0 }
                    .joinToString(", ") {
                        val count = group.counts[it] ?: 0
                        "$it ${InsightFormat.count(count)} (${InsightFormat.percent(count * 100.0 / group.total)})"
                    },
                onClick = { selected = if (selected == group.group) null else group.group }
            )
        }
    }
}

/** One group as a full-width bar split by category share. */
@Composable
private fun GroupShareRow(
    group: GroupCategoryCounts,
    colors: CategoryColors,
    detail: String?,
    onClick: () -> Unit
) {
    GroupRow(
        label = group.group,
        value = "n = ${InsightFormat.count(group.total)}",
        detail = detail,
        onClick = onClick
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(16.dp)
        ) {
            val gap = 2.dp.toPx()
            var left = 0f
            colors.order.forEach { category ->
                val count = group.counts[category] ?: return@forEach
                val width = count.toFloat() / group.total * size.width
                drawRect(
                    colors.colorOf(category),
                    Offset(left, 0f),
                    Size(max(width - gap, 1f), size.height)
                )
                left += width
            }
        }
    }
}

/**
 * Label, plot and value columns shared by both kinds of group row. A tapped row is highlighted
 * and shows its [detail] underneath.
 */
@Composable
private fun GroupRow(
    label: String,
    value: String,
    detail: String?,
    onClick: () -> Unit,
    plot: @Composable () -> Unit
) {
    val selected = detail != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (selected) AppTheme.colors.interactive.selectedItemBackground else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = rowPadding)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                modifier = Modifier
                    .weight(LABEL_WEIGHT)
                    .padding(end = 8.dp),
                style = AppTheme.typography.bodyStyle,
                color = AppTheme.colors.text.primary,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.End
            )
            Box(
                modifier = Modifier
                    .weight(PLOT_WEIGHT)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                plot()
            }
            Text(
                text = value,
                modifier = Modifier
                    .weight(VALUE_WEIGHT)
                    .padding(start = 8.dp),
                style = AppTheme.typography.bodyStyle,
                color = AppTheme.colors.text.secondary,
                maxLines = 1
            )
        }
        detail?.let {
            Text(
                text = it,
                modifier = Modifier.padding(bottom = 6.dp),
                style = AppTheme.typography.bodyStyle,
                color = AppTheme.colors.text.primary
            )
        }
    }
}

private fun fraction(value: Double, domain: AxisDomain): Float =
    if (domain.max == domain.min) 0.5f else ((value - domain.min) / (domain.max - domain.min)).toFloat()
