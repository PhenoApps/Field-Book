package com.fieldbook.tracker.ui.components.graphs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.ui.utils.getMaxTextWidth
import com.fieldbook.tracker.utilities.TraitDetailUtil
import kotlin.math.ceil

private const val LABEL_SPACE_PERCENT = 0.4f
private val chartPadding = 8.dp
private val heightPerBar = 40.dp

/**
 * The labels will take minimum of 40% of screen width, and max length from the list of labels
 */
@Composable
fun BarChart(
    modifier: Modifier = Modifier,
    observations: List<String>,
    categories: String = "",
) {
    val labels = observations.groupingBy { it }.eachCount()

    val parsedCategories = TraitDetailUtil.parseCategories(categories)

    val sortedCategories = parsedCategories
        .filter { labels.containsKey(it) } // filter categories which exist in labels
        .reversed()
        .ifEmpty { labels.keys.sorted() }

    val maxCount = labels.values.maxOrNull() ?: 0

    // to avoid clutter on x-axis
    val granularity = if (maxCount > 6) ceil(maxCount / 6f).toInt() else 1
    val axisMaximum = ceil(maxCount.toFloat() / granularity) * granularity

    val primaryColor = AppTheme.colors.primary

    val labelTextStyle = AppTheme.typography.bodyStyle

    val screenWidthDp = LocalWindowInfo.current.containerDpSize.width
    val maxTextWidth = screenWidthDp * LABEL_SPACE_PERCENT

    val measuredWidth = getMaxTextWidth(
        texts = sortedCategories,
        textStyle = labelTextStyle,
        padding = chartPadding
    )
    val maxLabelWidth = minOf(maxTextWidth, measuredWidth)

    Column {
        Column(
            modifier = modifier
                .padding(chartPadding)
                .fillMaxWidth(),
        ) {
            sortedCategories.forEach { category ->
                val count = labels[category] ?: 0
                val barWidth = if (axisMaximum > 0) count.toFloat() / axisMaximum else 0f

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(heightPerBar),
                    verticalAlignment = Alignment.CenterVertically
                ) {

                    Text(
                        text = category,
                        modifier = Modifier.width(maxLabelWidth),
                        style = labelTextStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.End // right align towards the bars
                    )

                    Spacer(modifier = Modifier.width(chartPadding))

                    Box(
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(barWidth)
                                .fillMaxHeight()
                                .background(color = primaryColor)
                                .border(
                                    width = 0.5.dp,
                                    color = AppTheme.colors.surface.border,
                                )
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(chartPadding)
        ) {
            Spacer(modifier = Modifier.width(maxLabelWidth + (chartPadding / 2)))

            Box(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val markers = (0..axisMaximum.toInt() step granularity).toList()

                    markers.forEach { marker ->
                        Text(
                            text = marker.toString(),
                            style = labelTextStyle,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun BarChartPreview() {
    AppTheme {
        Column {
            BarChart(
                observations = listOf(
                    "red",
                    "red",
                    "red",
                    "red",
                    "red",
                    "red",
                    "red",
                    "someVeryLongCategory",
                    "someVeryLongCategory",
                    "someVeryLongCategory",
                ),
                categories = "[" +
                        "{\"label\":\"red\",\"value\":\"red\"}," +
                        "{\"label\":\"someVeryLongCategory\",\"value\":\"someVeryLongCategory\"}" +
                        "]",
            )
        }
    }
}