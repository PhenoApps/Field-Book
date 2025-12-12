package com.fieldbook.tracker.ui.components.graphs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.R
import com.fieldbook.tracker.charts.HistogramChartHelper
import com.fieldbook.tracker.ui.theme.AppTheme
import kotlin.math.ceil

val barHeights = 150.dp
val graphPadding = 16.dp
val yAxisLabelWidth = 30.dp
val axesLineWidth = 1.dp
val barBorderWidth = 0.5.dp

@Composable
fun HistogramChart(
    observations: List<String>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val numeric = observations.mapNotNull { it.toBigDecimalOrNull() }

    if (numeric.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(200.dp),
            contentAlignment = Alignment.Center
        ) { Text(stringResource(R.string.chart_no_data)) }
        return
    }

    val data = HistogramChartHelper.computeHistogramData(
        context = context,
        observations = numeric
    )

    val labels = data.binLabels
    val binCounts = data.binCounts
    val maxCount = data.maxCount

    // to avoid clutter on x-axis
    val granularity = if (maxCount > 6) ceil(maxCount / 6f).toInt() else 1
    val axisMaximum = ceil(maxCount.toFloat() / granularity) * granularity

    val axisColor = AppTheme.colors.lightGray
    val primary = AppTheme.colors.primary
    val labelTextStyle = MaterialTheme.typography.labelSmall

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(graphPadding),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeights)
        ) {
            Column(
                modifier = Modifier
                    .width(yAxisLabelWidth)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                for (y in axisMaximum.toInt() downTo 0 step granularity) {
                    Text(text = y.toString(), style = labelTextStyle)
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {

                // y-axis line
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(axesLineWidth)
                        .background(axisColor)
                        .align(Alignment.CenterStart)
                )

                Row(
                    modifier = Modifier
                        .fillMaxSize(),
                    verticalAlignment = Alignment.Bottom
                ) {
                    // bars
                    binCounts.forEach { count ->
                        val heightRatio = count.toFloat() / maxCount

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(heightRatio)
                                    .background(primary)
                                    .border(barBorderWidth, AppTheme.colors.surface.border)
                            )
                        }
                    }
                }

                // x-axis line
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(axesLineWidth)
                        .background(axisColor)
                        .align(Alignment.BottomCenter)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(),
        ) {

            // leave some space till origin
            Spacer(modifier = Modifier.width(yAxisLabelWidth))

            Row(modifier = Modifier.weight(1f)) {
                Spacer(modifier = Modifier.weight(1f))

                // x-axis labels
                labels.forEachIndexed { index, label ->
                    // skip the last label if its corresponding bin count is 0
                    val isUnusedLabel = index == labels.lastIndex && binCounts.last() == 0

                    if (isUnusedLabel) return@forEachIndexed

                    Box(modifier = Modifier.weight(1f)) {
                        Text(text = label, style = labelTextStyle)
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun HistogramChartPreview() {
    AppTheme {
        Column {
            HistogramChart(
                observations = listOf(),
            )
            HistogramChart(
                observations = listOf("1", "2"),
            )
            HistogramChart(
                observations = listOf("0.5", "1", "1.4", "2", "7", "5", "3", "2", "8", "1", "2"),
            )
            HistogramChart(
                observations = listOf("1", "1000", "1000", "1065", "1023"),
            )
        }
    }
}