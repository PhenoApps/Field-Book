package com.fieldbook.tracker.ui.screens.fields.insights

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.insights.AxisDomain
import com.fieldbook.tracker.insights.LinearFit
import com.fieldbook.tracker.insights.SummaryStats
import com.fieldbook.tracker.ui.components.widgets.CardView
import com.fieldbook.tracker.ui.theme.AppTheme
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val chartHeight = 200.dp
private val axisGap = 6.dp
private val markerRadius = 4.dp
private val markerRing = 1.dp
private val lineWidth = 2.dp
private val hairline = 1.dp
private val barCorner = 4.dp

// ---------------------------------------------------------------------------------------------
// Layout pieces
// ---------------------------------------------------------------------------------------------

/**
 * A card holding one view of the data. [trailing] sits in the top right corner, where the field
 * detail page's trait cards show their completeness.
 */
@Composable
fun InsightSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    CardView(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = AppTheme.typography.titleStyle,
                        color = AppTheme.colors.text.primary
                    )
                    subtitle?.let {
                        Text(
                            text = it,
                            style = AppTheme.typography.bodyStyle,
                            color = AppTheme.colors.text.secondary
                        )
                    }
                }
                trailing?.let {
                    Spacer(modifier = Modifier.width(12.dp))
                    it()
                }
            }
            content()
        }
    }
}

data class StatTile(val label: String, val value: String)

/** Headline numbers, two to a row. */
@Composable
fun StatTileGrid(tiles: List<StatTile>, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        tiles.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { tile ->
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = tile.value,
                            style = AppTheme.typography.titleStyle,
                            color = AppTheme.colors.text.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = tile.label,
                            style = AppTheme.typography.bodyStyle,
                            color = AppTheme.colors.text.secondary
                        )
                    }
                }
                if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

/** A ring filled to [fraction], with the percentage in the middle. */
@Composable
fun CompletenessPie(fraction: Float, size: Dp, modifier: Modifier = Modifier) {
    val palette = LocalInsightColors.current
    val share = fraction.coerceIn(0f, 1f)

    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.12f
            val inset = stroke / 2f
            val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
            drawArc(palette.pieTrack, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(palette.pieFill, -90f, share * 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        }
        Text(
            text = InsightFormat.percent(share * 100.0),
            style = axisTextStyle().copy(color = AppTheme.colors.text.primary)
        )
    }
}

@Composable
fun InsightMessage(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp),
        style = AppTheme.typography.bodyStyle,
        color = AppTheme.colors.text.secondary,
        textAlign = TextAlign.Center
    )
}

@Composable
fun InsightNote(text: String) {
    Text(
        text = text,
        style = AppTheme.typography.bodyStyle,
        color = AppTheme.colors.text.secondary
    )
}

/** Holds the filters that scope everything below them on a tab. */
@Composable
fun FilterRow(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        content = content
    )
}

@Composable
fun <T> InsightDropdown(
    label: String,
    selectedText: String,
    options: List<T>,
    optionText: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(8.dp)

    Box(modifier = modifier) {
        Surface(
            shape = shape,
            color = AppTheme.colors.background,
            border = BorderStroke(1.dp, AppTheme.colors.surface.border),
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .clickable { expanded = true }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = label,
                        style = axisTextStyle()
                    )
                    Text(
                        text = selectedText,
                        style = AppTheme.typography.bodyStyle,
                        color = AppTheme.colors.text.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = null,
                    tint = AppTheme.colors.surface.iconTint
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = AppTheme.colors.background
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionText(option)) },
                    onClick = {
                        expanded = false
                        onSelect(option)
                    }
                )
            }
        }
    }
}

@Composable
fun <T> ChoiceChips(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = value == selected,
                onClick = { onSelect(value) },
                label = { Text(label) },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = AppTheme.colors.chip.selectableBackground,
                    selectedContainerColor = AppTheme.colors.chip.defaultBackground,
                    labelColor = AppTheme.colors.text.primary,
                    selectedLabelColor = AppTheme.colors.text.primary
                ),
                border = FilterChipDefaults.filterChipBorder(
                    enabled = true,
                    selected = value == selected,
                    borderColor = AppTheme.colors.chip.selectableStroke,
                    selectedBorderColor = AppTheme.colors.chip.selectableStroke
                )
            )
        }
    }
}

data class LegendItem(val label: String, val color: Color, val outlined: Boolean = false)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SwatchLegend(items: List<LegendItem>, modifier: Modifier = Modifier) {
    val palette = LocalInsightColors.current

    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(item.color)
                        .then(
                            if (item.outlined) Modifier.border(1.dp, palette.baseline, RoundedCornerShape(2.dp))
                            else Modifier
                        )
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = item.label,
                    style = AppTheme.typography.bodyStyle,
                    color = AppTheme.colors.text.secondary
                )
            }
        }
    }
}

/** A continuous color scale with its end labels, and optionally its midpoint. */
@Composable
fun GradientLegend(
    startLabel: String,
    endLabel: String,
    colorAt: (Float) -> Color,
    midLabel: String? = null
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Brush.horizontalGradient((0..12).map { colorAt(it / 12f) }))
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(text = startLabel, style = axisTextStyle())
            midLabel?.let { Text(text = it, style = axisTextStyle()) }
            Text(text = endLabel, style = axisTextStyle())
        }
    }
}

@Composable
fun axisTextStyle(): TextStyle = TextStyle(
    fontSize = AppTheme.typography.bodySize * 0.85f,
    color = AppTheme.colors.text.secondary
)

// ---------------------------------------------------------------------------------------------
// Horizontal bars, laid out with rows rather than drawn, so long labels wrap and stay readable
// ---------------------------------------------------------------------------------------------

data class BarItem(val label: String, val value: Int, val color: Color? = null)

@Composable
fun HorizontalBarList(items: List<BarItem>, total: Int, modifier: Modifier = Modifier) {

    val palette = LocalInsightColors.current
    val largest = items.maxOfOrNull { it.value } ?: 0
    val shape = RoundedCornerShape(topEnd = barCorner, bottomEnd = barCorner)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items.forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 28.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.label,
                    modifier = Modifier
                        .weight(0.35f)
                        .padding(end = 8.dp),
                    style = AppTheme.typography.bodyStyle,
                    color = AppTheme.colors.text.primary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.End
                )
                Box(
                    modifier = Modifier
                        .weight(0.45f)
                        .height(16.dp)
                ) {
                    if (item.value > 0 && largest > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(item.value.toFloat() / largest)
                                .fillMaxHeight()
                                .clip(shape)
                                .background(item.color ?: palette.series)
                        )
                    }
                }
                Text(
                    text = if (total > 0) {
                        "${InsightFormat.count(item.value)} (${InsightFormat.percent(item.value * 100.0 / total)})"
                    } else InsightFormat.count(item.value),
                    modifier = Modifier
                        .weight(0.2f)
                        .padding(start = 8.dp),
                    style = AppTheme.typography.bodyStyle,
                    color = AppTheme.colors.text.secondary,
                    maxLines = 1
                )
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Selection
// ---------------------------------------------------------------------------------------------

/** The description of the tapped mark, written under the chart. Null until something is tapped. */
@Stable
class ChartSelection {

    var readout by mutableStateOf<String?>(null)
        private set

    fun report(message: String?) {
        readout = message
    }
}

/** A fresh selection whenever [key] changes, so a stale readout never outlives its data. */
@Composable
fun rememberChartSelection(key: Any?): ChartSelection = remember(key) { ChartSelection() }

/** The tapped mark's values, under the chart. Nothing until something is tapped. */
@Composable
fun ChartReadout(text: String?) {
    if (text == null) return
    Text(
        text = text,
        style = AppTheme.typography.bodyStyle,
        color = AppTheme.colors.text.primary
    )
}

// ---------------------------------------------------------------------------------------------
// XY charts
// ---------------------------------------------------------------------------------------------

/** Maps data values to pixels inside [plot]. */
class XYScale(val plot: Rect, val x: AxisDomain, val y: AxisDomain) {

    fun px(value: Double): Float = plot.left + fraction(value, x) * plot.width

    fun py(value: Double): Float = plot.bottom - fraction(value, y) * plot.height

    fun valueAtX(pixel: Float): Double = x.min + (pixel - plot.left) / plot.width * (x.max - x.min)

    private fun fraction(value: Double, domain: AxisDomain): Float =
        if (domain.max == domain.min) 0.5f else ((value - domain.min) / (domain.max - domain.min)).toFloat()
}

/** The scale from the latest draw, for hit testing taps. Not state: reading it never recomposes. */
private class ScaleHolder {
    var scale: XYScale? = null
}

/**
 * Axes, gridlines and tick labels, with the marks drawn by [drawMarks]. Tick labels that would
 * overlap the previous one are skipped rather than squeezed.
 *
 * [onLoupe] receives a long press and the drag after it: the finger's position while it moves,
 * then null with released = true when it lifts, or null with released = false if cancelled.
 *
 * [drawOverlay] draws on a separate layer above the marks, for things that change often, like
 * the magnifier, so that changing them does not redraw every mark underneath.
 */
@Composable
fun XYChart(
    x: AxisDomain,
    y: AxisDomain,
    xLabel: (Double) -> String,
    yLabel: (Double) -> String,
    modifier: Modifier = Modifier,
    height: Dp = chartHeight,
    onTap: ((Offset, XYScale) -> Unit)? = null,
    onLoupe: ((position: Offset?, scale: XYScale, released: Boolean) -> Unit)? = null,
    drawOverlay: (DrawScope.(XYScale) -> Unit)? = null,
    drawMarks: DrawScope.(XYScale) -> Unit
) {
    val palette = LocalInsightColors.current
    val measurer = rememberTextMeasurer()
    val style = axisTextStyle()
    val holder = remember { ScaleHolder() }
    val currentOnTap by rememberUpdatedState(onTap)
    val currentOnLoupe by rememberUpdatedState(onLoupe)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Canvas(
            modifier = Modifier
                .matchParentSize()
                .pointerInput(Unit) {
                    detectTapGestures { position ->
                        val scale = holder.scale ?: return@detectTapGestures
                        currentOnTap?.invoke(position, scale)
                    }
                }
                .then(
                    if (onLoupe == null) Modifier
                    else Modifier.pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { position ->
                                holder.scale?.let { currentOnLoupe?.invoke(position, it, false) }
                            },
                            onDrag = { change, _ ->
                                change.consume()
                                holder.scale?.let { currentOnLoupe?.invoke(change.position, it, false) }
                            },
                            onDragEnd = { holder.scale?.let { currentOnLoupe?.invoke(null, it, true) } },
                            onDragCancel = { holder.scale?.let { currentOnLoupe?.invoke(null, it, false) } }
                        )
                    }
                )
        ) {
            val gap = axisGap.toPx()
            val yLayouts = y.ticks.map { measurer.measure(yLabel(it), style) }
            val gutter = (yLayouts.maxOfOrNull { it.size.width } ?: 0) + gap
            val lineHeight = measurer.measure("0", style).size.height.toFloat()

            val plot = Rect(
                left = gutter,
                top = lineHeight / 2f,
                right = size.width - gap * 2,
                bottom = size.height - lineHeight - gap
            )
            val scale = XYScale(plot, x, y)
            holder.scale = scale

            y.ticks.forEachIndexed { i, tick ->
                val py = scale.py(tick)
                drawLine(palette.gridline, Offset(plot.left, py), Offset(plot.right, py), hairline.toPx())
                val layout = yLayouts[i]
                drawText(layout, topLeft = Offset(gutter - gap - layout.size.width, py - layout.size.height / 2f))
            }

            drawLine(palette.baseline, Offset(plot.left, plot.bottom), Offset(plot.right, plot.bottom), hairline.toPx())

            var lastRight = Float.NEGATIVE_INFINITY
            x.ticks.forEach { tick ->
                val layout = measurer.measure(xLabel(tick), style)
                val left = (scale.px(tick) - layout.size.width / 2f)
                    .coerceIn(0f, max(0f, size.width - layout.size.width))
                if (left < lastRight + gap) return@forEach
                drawText(layout, topLeft = Offset(left, plot.bottom + gap))
                lastRight = left + layout.size.width
            }

            drawMarks(scale)
        }

        drawOverlay?.let { overlay ->
            Canvas(modifier = Modifier.matchParentSize()) {
                holder.scale?.let { overlay(it) }
            }
        }
    }
}

/** A single series over x, with a crosshair on the tapped point. [points] must be sorted by x. */
@Composable
fun LineChart(
    points: List<Pair<Double, Double>>,
    x: AxisDomain,
    y: AxisDomain,
    xLabel: (Double) -> String,
    yLabel: (Double) -> String,
    describe: (Int) -> String,
    modifier: Modifier = Modifier,
    fillArea: Boolean = false,
    showMarkers: Boolean = points.size <= 40,
    height: Dp = chartHeight
) {
    val palette = LocalInsightColors.current
    val lineColor = palette.series
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    val selection = rememberChartSelection(points)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {

        XYChart(
            x = x,
            y = y,
            xLabel = xLabel,
            yLabel = yLabel,
            height = height,
            onTap = { position, scale ->
                selected = points.indices.minByOrNull { abs(scale.px(points[it].first) - position.x) }
                selection.report(selected?.let(describe))
            }
        ) { scale ->

            if (points.isEmpty()) return@XYChart

            val line = Path()
            points.forEachIndexed { i, (px, py) ->
                if (i == 0) line.moveTo(scale.px(px), scale.py(py))
                else line.lineTo(scale.px(px), scale.py(py))
            }

            if (fillArea && points.size > 1) {
                val area = Path().apply {
                    addPath(line)
                    lineTo(scale.px(points.last().first), scale.plot.bottom)
                    lineTo(scale.px(points.first().first), scale.plot.bottom)
                    close()
                }
                drawPath(area, lineColor.copy(alpha = 0.15f))
            }

            drawPath(
                line,
                lineColor,
                style = Stroke(width = lineWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
            )

            if (showMarkers || points.size == 1) {
                points.forEach { (px, py) -> drawMarker(Offset(scale.px(px), scale.py(py)), lineColor, palette.surface) }
            }

            selected?.let { points.getOrNull(it) }?.let { (px, py) ->
                val center = Offset(scale.px(px), scale.py(py))
                drawLine(palette.baseline, Offset(center.x, scale.plot.top), Offset(center.x, scale.plot.bottom), hairline.toPx())
                drawMarker(center, lineColor, palette.surface, markerRadius.toPx() + 2.dp.toPx())
            }
        }

        ChartReadout(selection.readout)
    }
}

data class RangeBar(val start: Double, val end: Double, val value: Double)

data class ReferenceLine(val x: Double, val label: String)

/**
 * Bars spanning ranges of a continuous x axis: histogram bins, or days. The tapped bar stays at
 * full strength and the rest fade, so it can be picked out of a dense chart.
 */
@Composable
fun RangeBarChart(
    bars: List<RangeBar>,
    x: AxisDomain,
    y: AxisDomain,
    xLabel: (Double) -> String,
    yLabel: (Double) -> String,
    describe: (Int) -> String,
    modifier: Modifier = Modifier,
    references: List<ReferenceLine> = emptyList(),
    height: Dp = chartHeight
) {
    val palette = LocalInsightColors.current
    var selected by remember(bars) { mutableStateOf<Int?>(null) }
    val selection = rememberChartSelection(bars)
    val measurer = rememberTextMeasurer()
    val style = axisTextStyle()

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {

        XYChart(
            x = x,
            y = y,
            xLabel = xLabel,
            yLabel = yLabel,
            height = height,
            onTap = { position, scale ->
                val value = scale.valueAtX(position.x)
                selected = bars.indexOfFirst { value >= it.start && value < it.end }.takeIf { it >= 0 }
                selection.report(selected?.let(describe))
            }
        ) { scale ->

            val gap = 1.dp.toPx()
            val baselineY = scale.py(max(y.min, 0.0))

            bars.forEachIndexed { i, bar ->
                if (bar.value <= 0.0) return@forEachIndexed
                val left = scale.px(bar.start) + gap
                val width = max(scale.px(bar.end) - gap - left, 1f)
                val top = scale.py(bar.value)
                val radius = CornerRadius(min(barCorner.toPx(), width / 2f))
                val barColor = if (selected == null || selected == i) palette.series else palette.series.copy(alpha = 0.35f)
                val path = Path().apply {
                    addRoundRect(
                        RoundRect(
                            left = left,
                            top = top,
                            right = left + width,
                            bottom = baselineY,
                            topLeftCornerRadius = radius,
                            topRightCornerRadius = radius,
                            bottomRightCornerRadius = CornerRadius.Zero,
                            bottomLeftCornerRadius = CornerRadius.Zero
                        )
                    )
                }
                drawPath(path, barColor)
            }

            references.forEach { reference ->
                val px = scale.px(reference.x)
                drawLine(palette.ink, Offset(px, scale.plot.top), Offset(px, scale.plot.bottom), 1.5.dp.toPx())
                val layout = measurer.measure(reference.label, style)
                val left = if (px + 4.dp.toPx() + layout.size.width <= scale.plot.right) px + 4.dp.toPx()
                else px - 4.dp.toPx() - layout.size.width
                drawText(layout, topLeft = Offset(left, scale.plot.top))
            }
        }

        ChartReadout(selection.readout)
    }
}

/** One box of a [BoxPlotChart], at position [x]. */
data class BoxPoint(val x: Double, val stats: SummaryStats)

/**
 * A box plot per position, joined by a line through the means. The whisker spans the lowest to
 * highest value, the box the middle half, and the bar across it is the median.
 */
@Composable
fun BoxPlotChart(
    boxes: List<BoxPoint>,
    x: AxisDomain,
    y: AxisDomain,
    xLabel: (Double) -> String,
    yLabel: (Double) -> String,
    describe: (Int) -> String,
    modifier: Modifier = Modifier,
    height: Dp = chartHeight
) {
    val palette = LocalInsightColors.current
    var selected by remember(boxes) { mutableStateOf<Int?>(null) }
    val selection = rememberChartSelection(boxes)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {

        XYChart(
            x = x,
            y = y,
            xLabel = xLabel,
            yLabel = yLabel,
            height = height,
            onTap = { position, scale ->
                selected = boxes.indices.minByOrNull { abs(scale.px(boxes[it].x) - position.x) }
                selection.report(selected?.let(describe))
            }
        ) { scale ->

            if (boxes.isEmpty()) return@XYChart

            val half = min(8.dp.toPx(), scale.plot.width / boxes.size * 0.3f).coerceAtLeast(1f)

            boxes.forEachIndexed { i, box ->
                val center = scale.px(box.x)
                val stats = box.stats
                val faded = selected != null && selected != i
                val boxColor = if (faded) palette.series.copy(alpha = 0.35f) else palette.series

                drawLine(
                    if (faded) palette.other.copy(alpha = 0.35f) else palette.other,
                    Offset(center, scale.py(stats.min)),
                    Offset(center, scale.py(stats.max)),
                    1.5.dp.toPx()
                )

                val top = scale.py(stats.q3)
                val boxHeight = max(scale.py(stats.q1) - top, 1f)
                drawRect(boxColor.copy(alpha = boxColor.alpha * 0.3f), Offset(center - half, top), Size(half * 2, boxHeight))
                drawRect(boxColor, Offset(center - half, top), Size(half * 2, boxHeight), style = Stroke(1.dp.toPx()))
                drawLine(boxColor, Offset(center - half, scale.py(stats.median)), Offset(center + half, scale.py(stats.median)), 2.dp.toPx())
            }

            if (boxes.size > 1) {
                val means = Path()
                boxes.forEachIndexed { i, box ->
                    if (i == 0) means.moveTo(scale.px(box.x), scale.py(box.stats.mean))
                    else means.lineTo(scale.px(box.x), scale.py(box.stats.mean))
                }
                drawPath(means, palette.ink.copy(alpha = 0.6f), style = Stroke(width = 1.5.dp.toPx(), join = StrokeJoin.Round))
            }

            boxes.forEach { box ->
                drawMarker(Offset(scale.px(box.x), scale.py(box.stats.mean)), palette.ink, palette.surface, 3.dp.toPx())
            }
        }

        ChartReadout(selection.readout)
    }
}

data class ScatterPoint(val x: Double, val y: Double, val group: Int = 0)

/**
 * Points with an optional fitted line. A tap selects the nearest point within reach. For dense
 * clouds, a long press opens a magnifier that follows the finger; lifting selects the point at
 * its centre.
 */
@Composable
fun ScatterChart(
    points: List<ScatterPoint>,
    x: AxisDomain,
    y: AxisDomain,
    xLabel: (Double) -> String,
    yLabel: (Double) -> String,
    describe: (Int) -> String,
    modifier: Modifier = Modifier,
    groupColors: List<Color>? = null,
    fit: LinearFit? = null,
    height: Dp = chartHeight + 40.dp
) {
    val palette = LocalInsightColors.current
    val colors = groupColors ?: listOf(palette.series)
    var selected by remember(points) { mutableStateOf<Int?>(null) }
    var loupe by remember(points) { mutableStateOf<Offset?>(null) }
    val selection = rememberChartSelection(points)
    val density = LocalDensity.current
    val hitRadius = with(density) { 24.dp.toPx() }
    val loupeReach = with(density) { loupeRadius.toPx() } / LOUPE_ZOOM

    fun select(index: Int?) {
        selected = index
        selection.report(index?.let(describe))
    }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {

        XYChart(
            x = x,
            y = y,
            xLabel = xLabel,
            yLabel = yLabel,
            height = height,
            onTap = { position, scale -> select(nearestPoint(points, scale, position, hitRadius)) },
            onLoupe = { position, scale, released ->
                if (released) loupe?.let { select(nearestPoint(points, scale, it, loupeReach)) }
                loupe = position
            },
            drawOverlay = { scale ->
                loupe?.let { finger ->
                    drawLoupe(finger, palette) { lensCenter, zoom ->
                        val nearest = nearestPoint(points, scale, finger, loupeReach)
                        points.forEachIndexed { i, point ->
                            val center = lensCenter + (Offset(scale.px(point.x), scale.py(point.y)) - finger) * zoom
                            drawMarker(
                                center,
                                colors[point.group % colors.size],
                                palette.surface,
                                markerRadius.toPx() * 1.5f
                            )
                            if (i == nearest) {
                                drawCircle(palette.ink, markerRadius.toPx() * 1.5f + 3.dp.toPx(), center, style = Stroke(lineWidth.toPx()))
                            }
                        }
                    }
                }
            }
        ) { scale ->

            points.forEach { point ->
                drawMarker(
                    Offset(scale.px(point.x), scale.py(point.y)),
                    colors[point.group % colors.size].copy(alpha = 0.8f),
                    palette.surface
                )
            }

            fit?.let {
                clipRect(scale.plot.left, scale.plot.top, scale.plot.right, scale.plot.bottom) {
                    drawLine(
                        palette.ink.copy(alpha = 0.7f),
                        Offset(scale.px(x.min), scale.py(it.at(x.min))),
                        Offset(scale.px(x.max), scale.py(it.at(x.max))),
                        lineWidth.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            selected?.let { points.getOrNull(it) }?.let { point ->
                drawCircle(
                    palette.ink,
                    markerRadius.toPx() + 3.dp.toPx(),
                    Offset(scale.px(point.x), scale.py(point.y)),
                    style = Stroke(width = lineWidth.toPx())
                )
            }
        }

        ChartReadout(selection.readout)
    }
}

/** The point nearest [at] within [reach] pixels, if any. */
private fun nearestPoint(points: List<ScatterPoint>, scale: XYScale, at: Offset, reach: Float): Int? {
    var best: Int? = null
    var bestDistance = reach * reach
    points.forEachIndexed { i, point ->
        val dx = scale.px(point.x) - at.x
        val dy = scale.py(point.y) - at.y
        val distance = dx * dx + dy * dy
        if (distance <= bestDistance) {
            best = i
            bestDistance = distance
        }
    }
    return best
}

/** How much the magnifier enlarges what is under the finger. */
const val LOUPE_ZOOM = 3f

val loupeRadius = 56.dp

/**
 * A magnifier lifted clear of the finger, so what is under it stays visible. [drawContent] draws
 * the magnified marks, clipped to the lens: anything at an offset d from [finger] belongs at
 * lensCenter + d * zoom.
 *
 * [visible] is the part of the canvas on screen. A canvas inside a scrolling list or a sideways
 * scroll can be larger than what shows, and the lens is kept within the part that does.
 */
fun DrawScope.drawLoupe(
    finger: Offset,
    palette: InsightColors,
    visible: Rect = Rect(Offset.Zero, size),
    drawContent: DrawScope.(lensCenter: Offset, zoom: Float) -> Unit
) {
    val radius = loupeRadius.toPx()
    val lift = radius + 32.dp.toPx()
    val above = finger.y - lift
    val center = Offset(
        finger.x.coerceIn(visible.left + radius, max(visible.left + radius, visible.right - radius)),
        if (above - radius >= visible.top) above
        else (finger.y + lift).coerceAtMost(max(visible.top + radius, visible.bottom - radius))
    )

    drawCircle(palette.ink.copy(alpha = 0.5f), radius / LOUPE_ZOOM, finger, style = Stroke(1.dp.toPx()))

    drawCircle(palette.surface, radius, center)
    clipPath(Path().apply { addOval(Rect(center, radius)) }) {
        drawContent(center, LOUPE_ZOOM)
        val tick = 6.dp.toPx()
        drawLine(palette.baseline, center - Offset(tick, 0f), center + Offset(tick, 0f), 1.dp.toPx())
        drawLine(palette.baseline, center - Offset(0f, tick), center + Offset(0f, tick), 1.dp.toPx())
    }
    drawCircle(palette.ink, radius, center, style = Stroke(1.5.dp.toPx()))
}

/** A filled marker with a surface ring, so overlapping markers stay distinct. */
private fun DrawScope.drawMarker(center: Offset, color: Color, ring: Color, radius: Float = markerRadius.toPx()) {
    drawCircle(ring, radius + markerRing.toPx(), center)
    drawCircle(color, radius, center)
}
