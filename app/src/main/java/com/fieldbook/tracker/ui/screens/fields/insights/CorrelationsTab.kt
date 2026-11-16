package com.fieldbook.tracker.ui.screens.fields.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.insights.CorrelationPair
import com.fieldbook.tracker.insights.FieldInsightsData
import com.fieldbook.tracker.insights.InsightStats
import com.fieldbook.tracker.insights.InsightTrait
import kotlin.math.abs

/** A trait needs this many plot values before it can be correlated at all. */
private const val MIN_PLOT_VALUES = 3

/** Wide enough for "-0.00" in the upper triangle's cells. */
private val minMatrixCell = 36.dp
private val maxMatrixCell = 48.dp
private val maxGutter = 140.dp
private val labelGap = 4.dp

/**
 * Correlations between numeric traits, plot by plot: a matrix of every pair, and below it the
 * point cloud of the pair picked in the matrix.
 */
@Composable
fun CorrelationsTab(
    data: FieldInsightsData,
    selectedPair: Pair<String, String>?,
    onPairChange: (Pair<String, String>) -> Unit
) {
    val palette = LocalInsightColors.current

    val traits = remember(data) {
        data.traits.filter { it.kind.isQuantitative && (data.plotNumeric[it.id]?.size ?: 0) >= MIN_PLOT_VALUES }
    }

    if (traits.size < 2) {
        InsightMessage(stringResource(R.string.field_insights_correlations_need_two))
        return
    }

    val pairs = rememberOffMain(data, traits) { InsightStats.correlations(traits.map { it.id }, data.plotNumeric) }
    if (pairs == null) {
        ComputingIndicator()
        return
    }
    val byPair = remember(pairs) {
        pairs.flatMap { listOf((it.traitA to it.traitB) to it, (it.traitB to it.traitA) to it) }.toMap()
    }
    val strongest = remember(pairs) { pairs.maxByOrNull { abs(it.r) } }

    val traitIds = traits.map { it.id }
    val pair = selectedPair?.takeIf { (first, second) -> first != second && first in traitIds && second in traitIds }
        ?: strongest?.let { it.traitA to it.traitB }
        ?: (traits[0].id to traits[1].id)

    val xTrait = traits.first { it.id == pair.first }
    val yTrait = traits.first { it.id == pair.second }

    LazyColumn(contentPadding = PaddingValues(8.dp)) {

        item {
            InsightSection(title = stringResource(R.string.field_insights_correlation_matrix)) {
                GradientLegend(
                    startLabel = "−1",
                    endLabel = "+1",
                    colorAt = { palette.correlation(it * 2f - 1f) },
                    midLabel = "0"
                )
                CorrelationMatrix(
                    traits = traits,
                    correlationOf = { a, b -> byPair[a to b] },
                    selected = pair,
                    onSelect = onPairChange
                )
            }
        }

        item {
            PairScatter(data, xTrait, yTrait)
        }
    }
}

@Composable
private fun PairScatter(data: FieldInsightsData, xTrait: InsightTrait, yTrait: InsightTrait) {

    val resources = LocalResources.current

    val points = remember(data, xTrait, yTrait) {
        val xs = data.plotNumeric[xTrait.id].orEmpty()
        val ys = data.plotNumeric[yTrait.id].orEmpty()
        xs.keys.filter { it in ys }.map { unit -> Triple(unit, xs.getValue(unit), ys.getValue(unit)) }
    }
    val fit = remember(points) { InsightStats.linearFit(points.map { it.second }, points.map { it.third }) }

    InsightSection(
        title = stringResource(R.string.field_insights_scatter_title, yTrait.name, xTrait.name),
        subtitle = fit?.let {
            stringResource(
                R.string.field_insights_scatter_subtitle,
                InsightFormat.correlation(it.r),
                InsightFormat.count(it.n),
                InsightFormat.number(it.slope)
            )
        }
    ) {
        if (points.isEmpty()) return@InsightSection

        ScatterChart(
            points = points.map { ScatterPoint(it.second, it.third) },
            x = InsightStats.niceDomain(points.minOf { it.second }, points.maxOf { it.second }, 4),
            y = InsightStats.niceDomain(points.minOf { it.third }, points.maxOf { it.third }, 4),
            xLabel = { InsightFormat.traitValue(xTrait, it) },
            yLabel = { InsightFormat.traitValue(yTrait, it) },
            fit = fit,
            describe = { i ->
                val (unit, x, y) = points[i]
                resources.getString(
                    R.string.field_insights_scatter_readout,
                    unit,
                    xTrait.name,
                    InsightFormat.traitValue(xTrait, x),
                    yTrait.name,
                    InsightFormat.traitValue(yTrait, y)
                )
            }
        )
    }
}

/**
 * Every pair of traits, numbered down the side and numbered and named across the top. Below the
 * diagonal each pair is a colored cell; above it, the same pair carries its r value. Tapping a
 * cell picks that pair for the point cloud: the column's trait across, the row's trait up.
 */
@Composable
private fun CorrelationMatrix(
    traits: List<InsightTrait>,
    correlationOf: (String, String) -> CorrelationPair?,
    selected: Pair<String, String>,
    onSelect: (Pair<String, String>) -> Unit
) {
    val palette = LocalInsightColors.current
    val measurer = rememberTextMeasurer()
    val style = axisTextStyle()
    val density = LocalDensity.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    val count = traits.size

    // Columns carry the trait's number and name, turned upright across the top; rows carry only
    // the number, which keeps the side narrow. Long names are cut short with an ellipsis.
    val maxLabelPx = with(density) { maxGutter.roundToPx() }
    val labelGapPx = with(density) { labelGap.roundToPx() }
    val columnLabels = remember(traits, style, maxLabelPx) {
        traits.mapIndexed { i, trait ->
            measurer.measure(
                "${i + 1}  ${trait.name}",
                style,
                overflow = TextOverflow.Ellipsis,
                maxLines = 1,
                constraints = Constraints(maxWidth = maxLabelPx)
            )
        }
    }
    val rowLabels = remember(traits, style) { traits.indices.map { measurer.measure("${it + 1}", style) } }
    val gutterPx = rowLabels.maxOf { it.size.width } + labelGapPx
    val headerPx = columnLabels.maxOf { it.size.width } + labelGapPx

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {

        val gutter = with(density) { gutterPx.toDp() }
        val header = with(density) { headerPx.toDp() }
        val cell = ((maxWidth - gutter) / count).coerceIn(minMatrixCell, maxMatrixCell)
        val cellPx = with(density) { cell.toPx() }

        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Canvas(
                modifier = Modifier
                    .size(gutter + cell * count, header + cell * count)
                    .pointerInput(traits, cellPx, gutterPx, headerPx) {
                        detectTapGestures { position ->
                            val column = ((position.x - gutterPx) / cellPx).toInt()
                            val row = ((position.y - headerPx) / cellPx).toInt()
                            if (position.x >= gutterPx && position.y >= headerPx &&
                                row in 0 until count && column in 0 until count && row != column
                            ) {
                                currentOnSelect(traits[column].id to traits[row].id)
                            }
                        }
                    }
            ) {
                val gap = 1.dp.toPx()

                columnLabels.forEachIndexed { i, label ->

                    // Rotated a quarter turn back about its own top left corner, a label reads
                    // bottom to top and starts just above its column.
                    val pivot = Offset(
                        gutterPx + i * cellPx + (cellPx - label.size.height) / 2f,
                        (headerPx - labelGapPx).toFloat()
                    )
                    rotate(degrees = -90f, pivot = pivot) {
                        drawText(label, topLeft = pivot)
                    }
                }

                rowLabels.forEachIndexed { i, label ->
                    drawText(
                        label,
                        topLeft = Offset(
                            (gutterPx - labelGapPx - label.size.width).toFloat(),
                            headerPx + i * cellPx + (cellPx - label.size.height) / 2f
                        )
                    )
                }

                for (row in 0 until count) {
                    for (column in 0 until count) {
                        val topLeft = Offset(gutterPx + column * cellPx + gap, headerPx + row * cellPx + gap)
                        val cellSize = Size(cellPx - gap * 2, cellPx - gap * 2)
                        val correlation = if (row == column) null else correlationOf(traits[column].id, traits[row].id)

                        if (correlation == null) {
                            drawRect(palette.empty, topLeft, cellSize)
                            continue
                        }

                        val fill = palette.correlation(correlation.r.toFloat())
                        drawRect(fill, topLeft, cellSize)

                        if (row < column) {
                            val text = measurer.measure(
                                InsightFormat.correlation(correlation.r),
                                style.copy(color = palette.onFill(fill))
                            )
                            drawText(
                                text,
                                topLeft = Offset(
                                    topLeft.x + (cellSize.width - text.size.width) / 2f,
                                    topLeft.y + (cellSize.height - text.size.height) / 2f
                                )
                            )
                        }
                    }
                }

                // The chosen pair sits in two cells, one either side of the diagonal.
                val first = traits.indexOfFirst { it.id == selected.first }
                val second = traits.indexOfFirst { it.id == selected.second }
                if (first >= 0 && second >= 0) {
                    listOf(second to first, first to second).forEach { (row, column) ->
                        drawRect(
                            palette.ink,
                            Offset(gutterPx + column * cellPx, headerPx + row * cellPx),
                            Size(cellPx, cellPx),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }
            }
        }
    }
}
