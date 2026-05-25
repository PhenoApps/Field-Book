package com.fieldbook.tracker.views

import com.fieldbook.tracker.R
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.enums.FieldCreationStep
import com.google.android.material.color.MaterialColors

@Composable
fun FieldCreatorStepper(currentStep: FieldCreationStep, onStepClicked: (FieldCreationStep) -> Unit = {}) {
    val context = LocalContext.current
    fun themeColor(@androidx.annotation.AttrRes attr: Int): Color =
        Color(MaterialColors.getColor(context, attr, android.graphics.Color.WHITE))

    val stepperIconColor = themeColor(R.attr.stepper_icon_color)
    val stepperIconBgColor = themeColor(R.attr.stepper_icon_bg_color)
    val stepperIconOnDoneColor = themeColor(R.attr.stepper_icon_on_done_color)
    val stepperIconOnDoneBgColor = themeColor(R.attr.stepper_icon_on_done_bg_color)
    val stepperLineColor = themeColor(R.attr.stepper_line_color)
    val stepperLineOnDoneColor = themeColor(R.attr.stepper_line_on_done_color)

    val steps = FieldCreationStep.displayableEntries()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { index, step ->
            if (index > 0) {
                val previousDone = steps[index - 1].position < currentStep.position
                Box(
                    modifier = Modifier
                        .width(28.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(if (previousDone) stepperLineOnDoneColor else stepperLineColor),
                )
            }

            val state = when {
                step.position < currentStep.position -> StepVisualState.Done
                step.position == currentStep.position -> StepVisualState.Current
                else -> StepVisualState.Todo
            }

            FieldCreatorStepItem(
                step = step,
                state = state,
                stepperIconColor = stepperIconColor,
                stepperIconBgColor = stepperIconBgColor,
                stepperIconOnDoneColor = stepperIconOnDoneColor,
                stepperIconOnDoneBgColor = stepperIconOnDoneBgColor,
                onClick = { onStepClicked(step) },
            )
        }
    }
}

private enum class StepVisualState {
    Todo, Current, Done,
}

@Composable
private fun FieldCreatorStepItem(
    step: FieldCreationStep,
    state: StepVisualState,
    stepperIconColor: Color,
    stepperIconBgColor: Color,
    stepperIconOnDoneColor: Color,
    stepperIconOnDoneBgColor: Color,
    onClick: () -> Unit,
) {
    val stepSize = when (state) {
        StepVisualState.Current -> 60.dp
        StepVisualState.Todo, StepVisualState.Done -> 40.dp
    }
    val iconSize = when (state) {
        StepVisualState.Current -> 28.dp
        StepVisualState.Todo, StepVisualState.Done -> 22.dp
    }
    val backgroundColor = when (state) {
        StepVisualState.Done -> stepperIconOnDoneBgColor
        StepVisualState.Todo, StepVisualState.Current -> stepperIconBgColor
    }
    val iconTint = when (state) {
        StepVisualState.Done -> stepperIconOnDoneColor
        StepVisualState.Todo, StepVisualState.Current -> stepperIconColor
    }

    Box(
        modifier = Modifier
            .size(stepSize)
            .clip(CircleShape)
            .background(backgroundColor)
            .border(2.dp, stepperIconColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            StepVisualState.Done -> {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(iconSize),
                )
            }
            StepVisualState.Todo, StepVisualState.Current -> {
                step.icon?.let { iconRes ->
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(iconSize),
                    )
                }
            }
        }
    }
}
