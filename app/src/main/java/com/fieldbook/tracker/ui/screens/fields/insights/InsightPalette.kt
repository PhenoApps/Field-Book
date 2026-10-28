package com.fieldbook.tracker.ui.screens.fields.insights

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import com.fieldbook.tracker.insights.InsightTrait
import com.fieldbook.tracker.insights.InsightTraitKind
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.ui.theme.colors.AppColors
import com.fieldbook.tracker.ui.theme.colors.DefaultAppColors
import com.fieldbook.tracker.ui.theme.colors.Gray
import com.fieldbook.tracker.utilities.DateJsonUtil
import java.text.DecimalFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

private const val MILLIS_PER_DAY = 86_400_000.0

/**
 * Categories past the theme's own colors. The theme has no categorical set of its own, so these
 * fill it out with hues that stay apart from each other.
 */
private val extraCategoryColors = listOf(
    Color(0xFFEB6834), // orange
    Color(0xFF2A78D6), // blue
    Color(0xFFEDA100), // yellow
    Color(0xFFE87BA4), // magenta
    Color(0xFF6250D6), // violet
    Color(0xFFE34948), // red
)

/** Chart colors, taken from the app theme so the charts follow it. */
@Immutable
class InsightColors(
    /** The color of a chart with a single series. */
    val series: Color,
    /** Filled share of a completeness pie, as the field detail page draws it. */
    val pieFill: Color,
    val pieTrack: Color,
    val surface: Color,
    val ink: Color,
    val gridline: Color,
    val baseline: Color,
    /** A plot or cell that has no value. */
    val empty: Color,
    /** Categories past the last slot fold into this. */
    val other: Color,
    val categorical: List<Color>,
    private val ramp: List<Color>,
    private val correlationNegative: Color,
    private val correlationNeutral: Color,
    private val correlationPositive: Color,
) {

    fun category(index: Int): Color = categorical.getOrElse(index) { other }

    /** [t] from 0 (least) to 1 (most), light to dark along the theme's heatmap colors. */
    fun sequential(t: Float): Color {
        val position = t.coerceIn(0f, 1f) * (ramp.size - 1)
        val lower = position.toInt().coerceAtMost(ramp.size - 2)
        return lerp(ramp[lower], ramp[lower + 1], position - lower)
    }

    /**
     * [r] from -1 through 0 to 1, on the theme's correlation scale. The map uses it too, for
     * below to above the field mean.
     */
    fun correlation(r: Float): Color {
        val clamped = r.coerceIn(-1f, 1f)
        return if (clamped < 0f) lerp(correlationNeutral, correlationNegative, -clamped)
        else lerp(correlationNeutral, correlationPositive, clamped)
    }

    /** Ink that stays readable on top of [background]. */
    fun onFill(background: Color): Color =
        if (background.luminance() < 0.3f) Color.White else ink

    companion object {

        fun from(colors: AppColors): InsightColors {

            // interactive.accent is the theme's primary, except where the primary would vanish
            // into the background, as it does in the high contrast theme.
            val series = colors.interactive.accent
            val heatmap = colors.dataVisualization.heatmap
            val themed = listOf(series, colors.accent).distinct()

            return InsightColors(
                series = series,
                pieFill = colors.primary,
                pieTrack = colors.button.traitBackground,
                surface = colors.background,
                ink = colors.text.primary,
                gridline = colors.lightGray,
                baseline = lerp(colors.lightGray, colors.text.secondary, 0.5f),
                empty = colors.dataVisualization.dataGrid.emptyCell,
                other = Gray,
                categorical = themed + extraCategoryColors,
                ramp = listOf(heatmap.low, heatmap.medium, heatmap.high, heatmap.max),
                correlationNegative = colors.dataVisualization.correlation.negative,
                correlationNeutral = colors.dataVisualization.correlation.neutral,
                correlationPositive = colors.dataVisualization.correlation.positive,
            )
        }
    }
}

val LocalInsightColors = staticCompositionLocalOf { InsightColors.from(DefaultAppColors) }

@Composable
fun rememberInsightColors(): InsightColors {
    val colors = AppTheme.colors
    return remember(colors) { InsightColors.from(colors) }
}

/**
 * Assigns each category of a trait a fixed color slot: defined categories first in their
 * configured order, then any others by name. Past the last slot, the remainder share
 * [InsightColors.other].
 */
class CategoryColors(trait: InsightTrait, observed: Collection<String>, private val palette: InsightColors) {

    val order: List<String> = trait.categoryOrder.filter { it in observed } +
            observed.filter { it !in trait.categoryOrder }.distinct().sorted()

    private val slots = order.withIndex().associate { (i, category) ->
        category to if (order.size <= palette.categorical.size || i < palette.categorical.size - 1) i else -1
    }

    val hasOther: Boolean get() = slots.values.any { it < 0 }

    fun colorOf(category: String): Color = palette.category(slots[category] ?: -1)

    fun isOther(category: String): Boolean = (slots[category] ?: -1) < 0
}

object InsightFormat {

    private val dayFormatter: DateTimeFormatter by lazy {
        DateTimeFormatter.ofPattern(
            android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd")
        )
    }

    // ICU can hand back pattern letters, such as B for the day period, that older java.time
    // rejects, so a locale whose best pattern uses one falls back to the stock short format.
    private val timeFormatter: DateTimeFormatter by lazy {
        try {
            DateTimeFormatter.ofPattern(
                android.text.format.DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMd jm")
            )
        } catch (_: IllegalArgumentException) {
            DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)
        }
    }

    fun number(value: Double): String {
        val magnitude = abs(value)
        val pattern = when {
            magnitude == 0.0 -> "0"
            magnitude >= 1000 -> "#,##0"
            magnitude >= 100 -> "#,##0.#"
            magnitude >= 1 -> "#,##0.##"
            magnitude >= 0.001 -> "0.###"
            else -> "0.##E0"
        }
        return DecimalFormat(pattern).format(value)
    }

    fun count(value: Int): String = DecimalFormat("#,##0").format(value)

    fun percent(value: Double): String = "${value.roundToInt()}%"

    fun correlation(r: Double): String = DecimalFormat("0.00").format(r)

    fun day(date: LocalDate): String = date.format(dayFormatter)

    fun epochDay(value: Double): String = day(LocalDate.ofEpochDay(value.roundToInt().toLong()))

    /**
     * Days since the epoch in local time, with the time of day as the fraction. Plotting time on
     * this scale puts round ticks on midnights.
     */
    fun localEpochDay(epochMillis: Long): Double =
        (epochMillis + TimeZone.getDefault().getOffset(epochMillis)) / MILLIS_PER_DAY

    /** Labels a [localEpochDay] value, with the time of day when [withTime]. */
    fun localDay(value: Double, withTime: Boolean): String {
        val date = LocalDate.ofEpochDay(floor(value).toLong())
        if (!withTime) return day(date)
        val minutes = ((value - floor(value)) * 24 * 60).roundToInt().toLong()
        return date.atStartOfDay().plusMinutes(minutes).format(timeFormatter)
    }

    /** A trait value: dates come back from day of year, everything else is a plain number. */
    fun traitValue(trait: InsightTrait, value: Double): String =
        if (trait.kind == InsightTraitKind.DATE) DateJsonUtil.formatDayOfYear(value.roundToInt())
        else number(value)
}
