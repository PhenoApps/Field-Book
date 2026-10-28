package com.fieldbook.tracker.ui.screens.fields.insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.insights.AxisDomain
import com.fieldbook.tracker.insights.FieldGrid
import com.fieldbook.tracker.insights.FieldInsightsData
import com.fieldbook.tracker.insights.InsightStats
import com.fieldbook.tracker.insights.InsightTrait
import com.fieldbook.tracker.insights.InsightTraitKind
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max

/** Past this many cells the map stops being readable on a phone, and is likely the wrong attributes. */
private const val MAX_GRID_CELLS = 40_000L

/**
 * Also caps each side. A long thin grid passes the cell limit, but at the minimum cell size its
 * canvas would outgrow the largest dimension Compose constraints can represent.
 */
private const val MAX_GRID_SIDE = 500

private val minCell = 14.dp
private val maxCell = 44.dp
private val cellGap = 1.dp
private val labelGap = 4.dp

private enum class MapColorMode { VALUE, DEVIATION }

/** How a trait paints the map: a color per plot, the plot's value as text, and a legend. */
private class MapPaint(
    val colorOf: (String) -> Color?,
    val valueOf: (String) -> String,
    val legend: @Composable () -> Unit
)

/**
 * The trait laid out as the field is planted, so gradients across the field show up as gradients
 * on screen. Numeric traits also get their mean along each row and column.
 */
@Composable
fun FieldMapTab(
    data: FieldInsightsData,
    trait: InsightTrait,
    rowAttribute: String?,
    columnAttribute: String?,
    onTraitChange: (InsightTrait) -> Unit,
    onRowAttributeChange: (String) -> Unit,
    onColumnAttributeChange: (String) -> Unit
) {
    var mode by rememberSaveable { mutableStateOf(MapColorMode.VALUE) }
    val none = stringResource(R.string.field_insights_none)

    val grid = remember(data, rowAttribute, columnAttribute) {
        if (rowAttribute == null || columnAttribute == null || rowAttribute == columnAttribute) null
        else InsightStats.buildGrid(
            data.unitIds,
            data.attributes[rowAttribute].orEmpty(),
            data.attributes[columnAttribute].orEmpty()
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {

        FilterRow {
            TraitDropdown(data.traits, trait, onTraitChange, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                InsightDropdown(
                    label = stringResource(R.string.field_insights_map_rows),
                    selectedText = rowAttribute ?: none,
                    options = data.attributeNames,
                    optionText = { it },
                    onSelect = onRowAttributeChange,
                    modifier = Modifier.weight(1f)
                )
                InsightDropdown(
                    label = stringResource(R.string.field_insights_map_columns),
                    selectedText = columnAttribute ?: none,
                    options = data.attributeNames,
                    optionText = { it },
                    onSelect = onColumnAttributeChange,
                    modifier = Modifier.weight(1f)
                )
            }
            if (trait.kind.isQuantitative) {
                ChoiceChips(
                    options = listOf(
                        MapColorMode.VALUE to stringResource(R.string.field_insights_map_mode_value),
                        MapColorMode.DEVIATION to stringResource(R.string.field_insights_map_mode_deviation)
                    ),
                    selected = mode,
                    onSelect = { mode = it }
                )
            }
        }

        when {
            data.attributeNames.isEmpty() -> InsightMessage(stringResource(R.string.field_insights_map_no_attributes))
            grid == null || rowAttribute == null || columnAttribute == null ->
                InsightMessage(stringResource(R.string.field_insights_map_choose_layout))
            grid.rows.toLong() * grid.columns > MAX_GRID_CELLS || max(grid.rows, grid.columns) > MAX_GRID_SIDE ->
                InsightMessage(stringResource(R.string.field_insights_map_too_large, grid.rows, grid.columns))
            else -> FieldMapContent(data, trait, grid, rowAttribute, columnAttribute, mode)
        }
    }
}

@Composable
private fun FieldMapContent(
    data: FieldInsightsData,
    trait: InsightTrait,
    grid: FieldGrid,
    rowAttribute: String,
    columnAttribute: String,
    mode: MapColorMode
) {
    val resources = LocalResources.current
    val paint = mapPaint(data, trait, mode)
    var selected by remember(grid, trait.id) { mutableStateOf<Pair<Int, Int>?>(null) }
    val selection = rememberChartSelection(grid to trait.id)

    val values = data.plotNumeric[trait.id].orEmpty()
    val rowProfile = remember(grid, values) { InsightStats.profile(grid, values, byRow = true) }
    val columnProfile = remember(grid, values) { InsightStats.profile(grid, values, byRow = false) }

    LazyColumn(contentPadding = PaddingValues(8.dp)) {

        item {
            InsightSection(title = stringResource(R.string.field_insights_tab_map)) {
                paint.legend()
                HeatGrid(
                    grid = grid,
                    colorOf = paint.colorOf,
                    selected = selected,
                    onSelect = { cell ->
                        selected = cell
                        val unit = cell?.let { (row, column) -> grid.unitAt(row, column) }
                        selection.report(
                            cell?.let { (row, column) ->
                                unit?.let {
                                    resources.getString(
                                        R.string.field_insights_cell_readout,
                                        it,
                                        paint.valueOf(it),
                                        rowAttribute,
                                        grid.rowLabels[row],
                                        columnAttribute,
                                        grid.columnLabels[column]
                                    )
                                }
                            }
                        )
                    }
                )
                ChartReadout(selection.readout)
                if (grid.unplaced > 0) {
                    InsightNote(
                        stringResource(R.string.field_insights_map_unplaced, InsightFormat.count(grid.unplaced))
                    )
                }
            }
        }

        if (trait.kind.isQuantitative) {
            listOf(
                rowAttribute to rowProfile,
                columnAttribute to columnProfile
            ).forEach { (attribute, profile) ->
                if (profile.size < 2) return@forEach
                item {
                    InsightSection(title = stringResource(R.string.field_insights_values_by, attribute)) {
                        val labels = if (attribute == rowAttribute) grid.rowLabels else grid.columnLabels
                        val first = profile.first().index.toDouble()
                        val last = profile.last().index.toDouble()
                        val format = { value: Double -> InsightFormat.traitValue(trait, value) }
                        BoxPlotChart(
                            boxes = profile.map { BoxPoint(it.index.toDouble(), it.stats) },
                            x = AxisDomain(first - 0.5, last + 0.5, InsightStats.innerTicks(first, last, 5, minStep = 1.0)),
                            y = InsightStats.niceDomain(profile.minOf { it.stats.min }, profile.maxOf { it.stats.max }, 4),
                            xLabel = { labels.getOrNull(it.toInt()) ?: "" },
                            yLabel = format,
                            describe = { i ->
                                val point = profile[i]
                                resources.getString(
                                    R.string.field_insights_profile_readout,
                                    attribute,
                                    point.label,
                                    InsightFormat.count(point.n),
                                    format(point.stats.mean),
                                    format(point.stats.median),
                                    format(point.stats.min),
                                    format(point.stats.max)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun mapPaint(data: FieldInsightsData, trait: InsightTrait, mode: MapColorMode): MapPaint {

    val palette = LocalInsightColors.current
    val noValue = stringResource(R.string.field_insights_no_value)
    val noValueLegend = LegendItem(noValue, palette.empty, outlined = true)

    return when (trait.kind) {

        InsightTraitKind.NUMERIC, InsightTraitKind.DATE -> {

            val values = data.plotNumeric[trait.id].orEmpty()
            val stats = remember(values) { InsightStats.summarize(values.values) }
            val low = stats?.min ?: 0.0
            val high = stats?.max ?: 0.0
            val mean = stats?.mean ?: 0.0
            val spread = max(abs(high - mean), abs(low - mean)).takeIf { it > 0.0 } ?: 1.0
            val format = { value: Double -> InsightFormat.traitValue(trait, value) }
            val meanLabel = stringResource(R.string.field_insights_mean)

            MapPaint(
                colorOf = { unit ->
                    values[unit]?.let { value ->
                        // Values run light to dark in one hue, since high is not better or worse in
                        // itself; the difference from the mean takes the red to green scale.
                        if (mode == MapColorMode.VALUE) {
                            palette.sequential(if (high == low) 0.5f else ((value - low) / (high - low)).toFloat())
                        } else {
                            palette.correlation(((value - mean) / spread).toFloat())
                        }
                    }
                },
                valueOf = { unit -> values[unit]?.let(format) ?: noValue },
                legend = {
                    if (mode == MapColorMode.VALUE) {
                        GradientLegend(format(low), format(high), palette::sequential)
                    } else {
                        GradientLegend(
                            startLabel = format(mean - spread),
                            endLabel = format(mean + spread),
                            colorAt = { palette.correlation(it * 2f - 1f) },
                            midLabel = "$meanLabel ${format(mean)}"
                        )
                    }
                    SwatchLegend(listOf(noValueLegend))
                }
            )
        }

        InsightTraitKind.CATEGORICAL -> {

            val categories = data.plotCategories[trait.id].orEmpty()
            val colors = remember(trait, categories, palette) {
                CategoryColors(trait, categories.values.flatten().toSet(), palette)
            }
            val hasMultiple = remember(categories) { categories.values.any { it.size > 1 } }
            val other = stringResource(R.string.field_insights_other)
            val multiple = stringResource(R.string.field_insights_multiple)

            // An entry with several categories (multicat) gets one color of its own rather than
            // whichever of its categories happens to come first.
            MapPaint(
                colorOf = { unit ->
                    categories[unit]?.let { if (it.size > 1) palette.ink else colors.colorOf(it.first()) }
                },
                valueOf = { unit -> categories[unit]?.joinToString(", ") ?: noValue },
                legend = {
                    SwatchLegend(
                        colors.order.filterNot(colors::isOther).map { LegendItem(it, colors.colorOf(it)) } +
                                (if (colors.hasOther) listOf(LegendItem(other, palette.other)) else emptyList()) +
                                (if (hasMultiple) listOf(LegendItem(multiple, palette.ink)) else emptyList()) +
                                noValueLegend
                    )
                }
            )
        }

        InsightTraitKind.OTHER -> {

            val covered = remember(data, trait) {
                data.recorded(trait.id).mapTo(HashSet()) { it.unitId }
            }
            val recorded = stringResource(R.string.field_insights_recorded)

            MapPaint(
                colorOf = { unit -> if (unit in covered) palette.series else null },
                valueOf = { unit -> if (unit in covered) recorded else noValue },
                legend = {
                    SwatchLegend(listOf(LegendItem(recorded, palette.series), noValueLegend))
                }
            )
        }
    }
}

/**
 * The plots as a grid of cells, with row labels down the side and column labels across the top.
 * Labels thin out when cells are too small for every one. Wider fields scroll sideways.
 */
@Composable
private fun HeatGrid(
    grid: FieldGrid,
    colorOf: (String) -> Color?,
    selected: Pair<Int, Int>?,
    onSelect: (Pair<Int, Int>?) -> Unit
) {
    val palette = LocalInsightColors.current
    val measurer = rememberTextMeasurer()
    val style = axisTextStyle()
    val density = LocalDensity.current
    val currentOnSelect by rememberUpdatedState(onSelect)
    var loupe by remember(grid) { mutableStateOf<Offset?>(null) }

    // The part of the canvas on screen, for keeping the magnifier in view. Updated on every
    // scroll; read only while drawing the magnifier, so it is not state.
    val visible = remember { VisibleBounds() }

    // Laid out once per grid: the text measurer's own cache holds only a handful, so drawing
    // hundreds of labels would otherwise lay each one out again on every frame.
    val rowLayouts = remember(grid, style) { grid.rowLabels.map { measurer.measure(it, style) } }
    val columnLayouts = remember(grid, style) { grid.columnLabels.map { measurer.measure(it, style) } }
    val rowLabelWidth = rowLayouts.maxOfOrNull { it.size.width } ?: 0
    val columnLabelWidth = columnLayouts.maxOfOrNull { it.size.width } ?: 0
    val labelHeight = remember(style) { measurer.measure("0", style).size.height }

    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {

        val gutter = with(density) { rowLabelWidth.toDp() } + labelGap
        val header = with(density) { labelHeight.toDp() } + labelGap
        val cell = ((maxWidth - gutter) / grid.columns).coerceIn(minCell, maxCell)

        val gutterPx = with(density) { gutter.toPx() }
        val headerPx = with(density) { header.toPx() }
        val cellPx = with(density) { cell.toPx() }

        // The plot under a point on the canvas, if there is one.
        fun cellAt(position: Offset): Pair<Int, Int>? {
            if (position.x < gutterPx || position.y < headerPx) return null
            val column = ((position.x - gutterPx) / cellPx).toInt()
            val row = ((position.y - headerPx) / cellPx).toInt()
            val inside = row in 0 until grid.rows && column in 0 until grid.columns
            return if (inside && grid.unitAt(row, column) != null) row to column else null
        }

        Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
            Box(
                modifier = Modifier
                    .size(gutter + cell * grid.columns, header + cell * grid.rows)
                    .onGloballyPositioned { coordinates ->
                        val origin = coordinates.positionInWindow()
                        visible.rect = coordinates.boundsInWindow().translate(-origin.x, -origin.y)
                    }
                    .pointerInput(grid, cellPx, gutterPx, headerPx) {
                        detectTapGestures { position -> currentOnSelect(cellAt(position)) }
                    }
                    .pointerInput(grid, cellPx, gutterPx, headerPx) {
                        // A long press opens a magnifier for picking out small cells; lifting
                        // selects the cell under its centre.
                        detectDragGesturesAfterLongPress(
                            onDragStart = { loupe = it },
                            onDrag = { change, _ ->
                                change.consume()
                                loupe = change.position
                            },
                            onDragEnd = {
                                loupe?.let { currentOnSelect(cellAt(it)) }
                                loupe = null
                            },
                            onDragCancel = { loupe = null }
                        )
                    }
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    val gap = cellGap.toPx()
                    val labelGapPx = labelGap.toPx()

                    // Only every nth label is drawn when the cells are narrower than the labels.
                    val columnStep = max(1, ceil((columnLabelWidth + labelGapPx) / cellPx).toInt())
                    val rowStep = max(1, ceil(labelHeight / cellPx).toInt())

                    for (column in 0 until grid.columns step columnStep) {
                        val layout = columnLayouts[column]
                        drawText(
                            layout,
                            topLeft = Offset(gutterPx + column * cellPx + (cellPx - layout.size.width) / 2f, 0f)
                        )
                    }

                    for (row in 0 until grid.rows step rowStep) {
                        val layout = rowLayouts[row]
                        drawText(
                            layout,
                            topLeft = Offset(
                                gutterPx - labelGapPx - layout.size.width,
                                headerPx + row * cellPx + (cellPx - layout.size.height) / 2f
                            )
                        )
                    }

                    val cellSize = Size(cellPx - gap * 2, cellPx - gap * 2)
                    for (row in 0 until grid.rows) {
                        for (column in 0 until grid.columns) {
                            val unit = grid.unitAt(row, column) ?: continue
                            val topLeft = Offset(gutterPx + column * cellPx + gap, headerPx + row * cellPx + gap)
                            val color = colorOf(unit)
                            if (color != null) {
                                drawRect(color, topLeft, cellSize)
                            } else {
                                drawRect(palette.empty, topLeft, cellSize)
                                drawRect(palette.baseline, topLeft, cellSize, style = Stroke(width = 1f))
                            }
                        }
                    }

                    selected?.let { (row, column) ->
                        drawRect(
                            palette.ink,
                            Offset(gutterPx + column * cellPx, headerPx + row * cellPx),
                            Size(cellPx, cellPx),
                            style = Stroke(width = 2.dp.toPx())
                        )
                    }
                }

                // The magnifier is a layer of its own: it moves with the finger, and redrawing it
                // should not mean redrawing every cell under it.
                Canvas(modifier = Modifier.matchParentSize()) {
                    val finger = loupe ?: return@Canvas
                    val gap = cellGap.toPx()
                    val cellSize = Size(cellPx - gap * 2, cellPx - gap * 2)

                    drawLoupe(finger, palette, visible.rect ?: Rect(Offset.Zero, size)) { lensCenter, zoom ->
                        val reach = loupeRadius.toPx() / zoom + cellPx
                        val firstColumn = ((finger.x - reach - gutterPx) / cellPx).toInt().coerceAtLeast(0)
                        val lastColumn = ((finger.x + reach - gutterPx) / cellPx).toInt().coerceAtMost(grid.columns - 1)
                        val firstRow = ((finger.y - reach - headerPx) / cellPx).toInt().coerceAtLeast(0)
                        val lastRow = ((finger.y + reach - headerPx) / cellPx).toInt().coerceAtMost(grid.rows - 1)
                        val under = cellAt(finger)

                        for (row in firstRow..lastRow) {
                            for (column in firstColumn..lastColumn) {
                                val unit = grid.unitAt(row, column) ?: continue
                                val topLeft = lensCenter +
                                        (Offset(gutterPx + column * cellPx + gap, headerPx + row * cellPx + gap) - finger) * zoom
                                drawRect(colorOf(unit) ?: palette.empty, topLeft, cellSize * zoom)
                                if (under == row to column) {
                                    drawRect(palette.ink, topLeft, cellSize * zoom, style = Stroke(width = 2.dp.toPx()))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The on-screen part of a canvas, in its own coordinates. */
private class VisibleBounds {
    var rect: Rect? = null
}
