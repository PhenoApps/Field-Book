package com.fieldbook.tracker.traits.composables

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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
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
    elapsedSeconds: MutableState<Int> = remember { mutableIntStateOf(0) },
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
        targetValue = (elapsedSeconds.value % 60) * (360f / 60f), //convert to remaining seconds, tick by every degree of the circle
        animationSpec = tween(durationMillis = 1000), //tick once per second
        label = "SweepAngle"
    )

    //starts asynchronous timer that updates every second
    LaunchedEffect(isRunning.value) {
        while (isRunning.value && elapsedSeconds.value < maxTimeSeconds) {
            delay(1000)
            elapsedSeconds.value++
        }
    }

    val formattedTime = remember(elapsedSeconds.value) {
        val s = elapsedSeconds.value % 60
        val m = (elapsedSeconds.value % 3600) / 60
        val h = elapsedSeconds.value / 3600
        "%02d:%02d:%02d".format(h, m, s)
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
            modifier = Modifier.size(240.dp)
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
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }

        //underneath the timer are the controls, start, pause, stop
        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                isRunning.value = false
                elapsedSeconds.value = 0
            }) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(color = iconBackgroundColor, shape= CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Replay,
                        contentDescription = resetLabel,
                        tint = Color.Black)
                }
            }
            IconButton(onClick = {
                isRunning.value = !isRunning.value
            }) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(color = iconBackgroundColor, shape= CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isRunning.value) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isRunning.value) pauseLabel else playLabel
                    )
                }
            }
            IconButton(onClick = {
                onSaveCallback(formattedTime)
                isRunning.value = false
            }) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(color = iconBackgroundColor, shape= CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.Save, contentDescription = saveLabel)
                }
            }
        }
    }
}

