package com.fieldbook.tracker.traits.composables

import android.R.attr.label
import android.os.SystemClock
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Preview
@Composable
fun CircularTimer(
    modifier: Modifier = Modifier,
    elapsedMillis: MutableState<Long> = remember { mutableLongStateOf(0L) },
    isRunning: MutableState<Boolean> = remember { mutableStateOf(false) },
    maxTimeSeconds: Int = (99 * 60 * 60) + (59 * 60) + 59, //99:59:59 max time
    canvasColor: Color = Color.LightGray,
    progressArcColor: Color = Color.Blue,
    iconBackgroundColor: Color = Color.LightGray,
    resetLabel: String = "Reset",
    playLabel: String = "Play",
    pauseLabel: String = "Pause",
    saveLabel: String = "Save",
    onSaveCallback: (String) -> Unit = {},
) {

    //setup sweep animation for inner arc and transform seconds to time format
    val animatedSweepAngle by animateFloatAsState(
        targetValue = ((elapsedMillis.value / 1000) % 60) * (360f / 60f), //convert to remaining seconds, tick by every degree of the circle
        animationSpec = tween(durationMillis = 1000), //tick once per second
        label = "SweepAngle"
    )

    val baseMillis = remember { mutableLongStateOf(elapsedMillis.value) }
    val startUptime = remember { mutableLongStateOf(0L) }

    LaunchedEffect(isRunning.value) {
        if (isRunning.value) {

            startUptime.longValue = SystemClock.elapsedRealtime()
            baseMillis.longValue = elapsedMillis.value

            while (isRunning.value && elapsedMillis.value < maxTimeSeconds * 1000L) {
                val now = SystemClock.elapsedRealtime()
                val computed = baseMillis.longValue + (now - startUptime.longValue)
                elapsedMillis.value = computed
                delay(16)
            }
        } else {
            baseMillis.longValue = elapsedMillis.value
        }
    }

    LaunchedEffect(elapsedMillis.value) {
        if (!isRunning.value) {
            baseMillis.longValue = elapsedMillis.value
        }
    }

    val formattedTime = remember(elapsedMillis.value) {
        val now = elapsedMillis.value
        val millis = (now % 1000) / 10
        val s = (now / 1000) % 60
        val m = (now / 1000 % 3600) / 60
        val h = now / 1000 / 3600
        "%02d:%02d:%02d.%02d".format(h, m, s, millis)
    }

    //this column/box centers the timer in the trait layout
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(160.dp)
        ) {

            //this canvas draws the outer and inner circle progress
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = canvasColor,
                    style = Stroke(width = 8.dp.toPx())
                )
                drawArc(
                    color = progressArcColor,
                    startAngle = -90f,
                    sweepAngle = animatedSweepAngle,
                    useCenter = false,
                    style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            //text shows the elapsed time formatted above
            Text(
                text = formattedTime,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        //underneath the timer are the controls, start, pause, stop
        Spacer(modifier = Modifier.height(48.dp))

        Row(modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {

            TimerButton(
                icon = Icons.Default.Replay,
                label = resetLabel,
                iconBackgroundColor = iconBackgroundColor
            ) {
                isRunning.value = false
                elapsedMillis.value = 0
            }

            TimerButton(
                icon = if (isRunning.value) Icons.Default.Pause else Icons.Default.PlayArrow,
                label = if (isRunning.value) pauseLabel else playLabel,
                iconBackgroundColor = iconBackgroundColor
            ) {
                isRunning.value = !isRunning.value
            }

            TimerButton(Icons.Default.Save, saveLabel, iconBackgroundColor) {

                isRunning.value = false
                onSaveCallback(formattedTime)

                val parts = formattedTime.split(":")
                val hours = parts[0].toIntOrNull() ?: 0
                val minutes = parts[1].toIntOrNull() ?: 0
                val secAndMillis = parts[2].split(".")
                val seconds = secAndMillis.getOrNull(0)?.toIntOrNull() ?: 0
                val millis = secAndMillis.getOrNull(1)?.padEnd(3, '0')?.take(3)?.toIntOrNull() ?: 0
                val now = hours * 3600000L + minutes * 60000L + seconds * 1000 + millis

                //reset the ui to the saved time exactly
                elapsedMillis.value = now
            }
        }
    }
}

@Composable
fun TimerButton(icon: ImageVector, label: String, iconBackgroundColor: Color = Color.LightGray, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(72.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(color = iconBackgroundColor, shape = CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                modifier = Modifier.size(36.dp))
        }
    }
}