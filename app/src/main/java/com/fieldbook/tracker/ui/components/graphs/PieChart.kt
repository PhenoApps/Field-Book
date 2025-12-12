package com.fieldbook.tracker.ui.components.graphs

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.ui.theme.AppTheme

@Composable
fun PieChart(
    completeness: Float,
    modifier: Modifier = Modifier,
    size: Dp = 60.dp
) {
    val primaryColor = AppTheme.colors.primary
    val lightGray = AppTheme.colors.lightGray
    val background = AppTheme.colors.background

    Box(
        modifier = modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val canvasSize = this.size
            val arcRadius = canvasSize.minDimension / 2f * 0.9f
            val innerCircleRadius = arcRadius * 0.85f
            
            val center = Offset(canvasSize.width / 2f, canvasSize.height / 2f)

            val topLeftOffset = Offset(center.x - arcRadius, center.y - arcRadius)
            val arcSize = Size(arcRadius * 2, arcRadius * 2)

            // progress pie
            drawArc(
                color = primaryColor,
                startAngle = -90f,
                sweepAngle = completeness * 360f,
                useCenter = true,
                topLeft = topLeftOffset,
                size = arcSize,
            )

            // remaining pie
            drawArc(
                color = lightGray,
                startAngle = -90f + (completeness * 360f),
                sweepAngle = (1 - completeness) * 360f,
                useCenter = true,
                topLeft = topLeftOffset,
                size = arcSize,
            )

            // draw circle to make the previous pies resemble a circle border/arc
            drawCircle(
                color = background,
                radius = innerCircleRadius,
                center = center,
            )
        }

        // progress text
        Text(
            text = "${(completeness * 100).toInt()}%",
            style = AppTheme.typography.bodyStyle,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun PieChartPreview() {
    AppTheme {
        PieChart(completeness = 0.77f)
    }
}