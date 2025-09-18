package com.fieldbook.tracker.views

import com.fieldbook.tracker.R
import android.util.TypedValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.binayshaw7777.kotstep.v3.KotStep
import com.binayshaw7777.kotstep.v3.model.step.StepLayoutStyle
import com.binayshaw7777.kotstep.v3.model.style.BorderStyle
import com.binayshaw7777.kotstep.v3.model.style.IconStyle
import com.binayshaw7777.kotstep.v3.model.style.KotStepStyle
import com.binayshaw7777.kotstep.v3.model.style.LineStyle
import com.binayshaw7777.kotstep.v3.model.style.LineStyles
import com.binayshaw7777.kotstep.v3.model.style.StepStyle
import com.binayshaw7777.kotstep.v3.model.style.StepStyles
import com.binayshaw7777.kotstep.v3.util.ExperimentalKotStep
import com.fieldbook.tracker.enums.FieldCreationStep

@OptIn(ExperimentalKotStep::class)
@Composable
fun FieldCreatorStepper(currentStep: FieldCreationStep, onStepClicked: (FieldCreationStep) -> Unit = {}) {
    val context = LocalContext.current
    val theme = context.theme
    val typedValue = TypedValue()

    theme.resolveAttribute(R.attr.stepper_icon_color, typedValue, true)
    val stepperIconColor = Color(typedValue.data)

    theme.resolveAttribute(R.attr.stepper_icon_bg_color, typedValue, true)
    val stepperIconBgColor = Color(typedValue.data)

    theme.resolveAttribute(R.attr.stepper_icon_on_done_color, typedValue, true)
    val stepperIconOnDoneColor = Color(typedValue.data)

    theme.resolveAttribute(R.attr.stepper_icon_on_done_bg_color, typedValue, true)
    val stepperIconOnDoneBgColor = Color(typedValue.data)

    theme.resolveAttribute(R.attr.stepper_line_color, typedValue, true)
    val stepperLineColor = Color(typedValue.data)

    theme.resolveAttribute(R.attr.stepper_line_on_done_color, typedValue, true)
    val stepperLineOnDoneColor = Color(typedValue.data)

    val kotStepStyle = KotStepStyle(
        stepLayoutStyle = StepLayoutStyle.Horizontal,
        stepStyle = StepStyles(
            onTodo = StepStyle(
                stepColor = stepperIconBgColor,
                stepSize = 40.dp,
                iconStyle = IconStyle(iconSize = 24.dp),
                borderStyle = BorderStyle(color = stepperIconColor)
            ),
            onCurrent = StepStyle(
                stepColor = stepperIconBgColor,
                stepSize = 60.dp,
                iconStyle = IconStyle(iconSize = 44.dp),
                // textStyle = TextStyle(color = Color.Black, fontSize = 18.sp),
                borderStyle = BorderStyle(color = stepperIconColor)
            ),
            onDone = StepStyle(
                stepColor = stepperIconOnDoneBgColor,
                stepSize = 40.dp,
                iconStyle = IconStyle(iconSize = 24.dp, iconTint = stepperIconOnDoneColor),
                borderStyle = BorderStyle(color = stepperIconColor)
            )
        ),
        lineStyle = LineStyles(
            onTodo = LineStyle(lineColor = stepperLineColor, progressColor = stepperLineColor),
            onCurrent = LineStyle(lineColor = stepperLineColor, progressColor = stepperLineColor),
            onDone = LineStyle(lineColor = stepperLineColor, progressColor = stepperLineOnDoneColor, lineThickness = 4.dp)
        )
    )

    val steps = FieldCreationStep.displayableEntries()
    val icons: Map<FieldCreationStep, ImageVector?> = steps.associateWith { step ->
        step.icon?.let { ImageVector.vectorResource(id = it) }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(100.dp)
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        KotStep(
            currentStep = { currentStep.position.toFloat() },
            style = kotStepStyle
        ) {
            steps.forEach { step ->
                icons[step]?.let { icon ->
                    step(
                        icon = icon,
                        onClick = {
                            onStepClicked(step)
                        }
                    )
                }
            }
        }
    }
}

