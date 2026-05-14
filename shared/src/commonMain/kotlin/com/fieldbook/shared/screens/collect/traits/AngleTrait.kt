package com.fieldbook.shared.screens.collect.traits

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.kmp.shots.k.sensor.KSensor
import org.kmp.shots.k.sensor.SensorData
import org.kmp.shots.k.sensor.SensorType
import org.kmp.shots.k.sensor.SensorUpdate
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun AngleTrait(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var currentAngle by remember { mutableStateOf(value.toFloatOrNull() ?: 0f) }
    var sensorError by remember { mutableStateOf<String?>(null) }
    val sensorTypes = remember { listOf(SensorType.ACCELEROMETER) }

    LaunchedEffect(Unit) {
        KSensor.registerSensors(
            types = sensorTypes,
            locationIntervalMillis = 100L,
        ).collect { update ->
            when (update) {
                is SensorUpdate.Data -> {
                    val accelerometer = update.data as? SensorData.Accelerometer ?: return@collect
                    currentAngle = lowPassFilter(
                        input = accelerometer.toRollAngle(),
                        output = currentAngle,
                    )
                    sensorError = null
                }

                is SensorUpdate.Error -> {
                    sensorError = update.exception.message ?: "Failed to read angle sensor"
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            KSensor.unregisterSensors(sensorTypes)
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AngleGauge(
            angle = currentAngle,
            modifier = Modifier.size(width = 280.dp, height = 180.dp),
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = { onValueChange(formatAngle(currentAngle)) }) {
            Text("Capture")
        }
        sensorError?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun AngleGauge(
    angle: Float,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = colorScheme.onSurface,
        fontSize = 12.sp,
        textAlign = TextAlign.Center,
    )
    Canvas(modifier = modifier) {
        val radius = size.minDimension * 0.42f
        val center = Offset(size.width / 2f, size.height * 0.78f)
        val arcTopLeft = Offset(center.x - radius, center.y - radius)
        val arcSize = androidx.compose.ui.geometry.Size(radius * 2f, radius * 2f)

        drawArc(
            color = colorScheme.outline,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = arcTopLeft,
            size = arcSize,
            style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Round),
        )

        listOf(-90, -45, 0, 45, 90).forEach { mark ->
            val radians = (180f - (mark + 90f)) * (PI.toFloat() / 180f)
            val tickOuter = Offset(
                x = center.x + radius * cos(radians),
                y = center.y - radius * sin(radians),
            )
            val tickInnerRadius = radius - 10.dp.toPx()
            val tickInner = Offset(
                x = center.x + tickInnerRadius * cos(radians),
                y = center.y - tickInnerRadius * sin(radians),
            )
            val labelRadius = radius + 18.dp.toPx()
            val labelCenter = Offset(
                x = center.x + labelRadius * cos(radians),
                y = center.y - labelRadius * sin(radians),
            )

            drawLine(
                color = colorScheme.outline,
                start = tickInner,
                end = tickOuter,
                strokeWidth = 2.dp.toPx(),
                cap = StrokeCap.Round,
            )

            val textLayoutResult = textMeasurer.measure(
                text = mark.toString(),
                style = labelStyle,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            drawText(
                textLayoutResult = textLayoutResult,
                topLeft = Offset(
                    x = labelCenter.x - textLayoutResult.size.width / 2f,
                    y = labelCenter.y - textLayoutResult.size.height / 2f,
                ),
            )
        }

        val clamped = angle.coerceIn(-90f, 90f)
        val radians = (180f - (clamped + 90f)) * (PI.toFloat() / 180f)
        val needleEnd = Offset(
            x = center.x + radius * cos(radians),
            y = center.y - radius * sin(radians),
        )

        drawLine(
            color = colorScheme.primary,
            start = center,
            end = needleEnd,
            strokeWidth = 5.dp.toPx(),
            cap = StrokeCap.Round,
        )

        drawCircle(
            color = colorScheme.primary,
            radius = 7.dp.toPx(),
            center = center,
        )
    }
}

private fun SensorData.Accelerometer.toRollAngle(): Float =
    (atan2(
        x.toDouble(),
        sqrt((y * y + z * z).toDouble()),
    ) * 180.0 / PI).toFloat()

/**
 * Smooths the live angle so small accelerometer fluctuations do not make the gauge jump.
 */
private fun lowPassFilter(input: Float, output: Float): Float =
    output + 0.5f * (input - output)

private fun formatAngle(angle: Float): String {
    val rounded = round(angle * 10f) / 10f
    return if (rounded % 1f == 0f) {
        "${rounded.toInt()}.0"
    } else {
        rounded.toString()
    }
}
