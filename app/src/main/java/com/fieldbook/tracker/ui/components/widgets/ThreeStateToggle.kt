package com.fieldbook.tracker.ui.components.widgets

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.ui.theme.LocalAppColors
import com.fieldbook.tracker.ui.theme.colors.SodaDarkAppColors
import com.fieldbook.tracker.ui.theme.colors.ToggleColors
import kotlinx.coroutines.launch

/**
 * A three-state toggle implemented in Jetpack Compose.
 */
@Composable
fun ThreeStateToggle(
    states: List<Painter>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trackWidth: Dp = 180.dp,
    trackHeight: Dp = 40.dp,
    indicatorSize: Dp = 32.dp,
    iconSize: Dp = 20.dp,
    trackColor: Color? = null,
    indicatorColor: Color? = null,
    iconTint: Color? = null,
    unselectedIconTint: Color? = null,
    contentDescriptions: List<String>? = null,
    enabled: Boolean = true,
    enabledStates: List<Boolean>? = null // per-slot enabled flags; if null fallback to `enabled`
) {
    require(states.size == 3) { "ThreeStateToggle requires exactly 3 states" }

    // resolve colors from theme if null
    val themeToggle: ToggleColors = AppTheme.colors.toggle
    val finalTrackColor = trackColor ?: themeToggle.track
    val finalIndicatorColor = indicatorColor ?: themeToggle.indicator
    val finalIconTint = iconTint ?: themeToggle.icon
    val finalUnselectedIconTint = unselectedIconTint ?: themeToggle.iconUnselected

    val density = LocalDensity.current

    val slotCount = states.size
    val slotEnabledList = remember(enabledStates, enabled) {
        List(slotCount) { i -> enabledStates?.getOrNull(i) ?: enabled }
    }

    // Track measured width in px (used for stable fractional positioning of 3 equal slots)
    var trackPxWidth by remember { mutableStateOf(0f) }

    // Animatable for indicator X offset (px)
    val indicatorX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Convert indicator size to px
    val indicatorSizePx: Float = with(density) { indicatorSize.toPx() }

    // Stable indicator positioning based on selected index and track width.
    // Slots are always 3, laid out equally -> centers at (i+0.5)/3 of width.
    // This avoids layout races from child onGloballyPositioned + window coords.
    LaunchedEffect(selectedIndex, trackPxWidth) {
        if (trackPxWidth > 0f) {
            val center = (selectedIndex + 0.5f) * (trackPxWidth / 3f)
            val targetOffset = (center - indicatorSizePx / 2f).coerceIn(0f, trackPxWidth - indicatorSizePx)
            indicatorX.animateTo(
                targetOffset,
                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
            )
        }
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Track and indicator -- icons are rendered inside the track so they align exactly with the indicator
        Box(
            modifier = Modifier
                .width(trackWidth)
                .height(trackHeight)
                .onGloballyPositioned { layoutCoordinates ->
                    trackPxWidth = layoutCoordinates.size.width.toFloat()
                    // Snap initial indicator using stable fractional position (no child centers needed)
                    if (indicatorX.value == 0f && trackPxWidth > 0f) {
                        scope.launch {
                            val center = (selectedIndex + 0.5f) * (trackPxWidth / 3f)
                            val initial = (center - indicatorSizePx / 2f).coerceIn(0f, trackPxWidth - indicatorSizePx)
                            indicatorX.snapTo(initial)
                        }
                    }
                 }
                 .pointerInput(trackPxWidth) {
                    detectTapGestures { offset ->
                        if (trackPxWidth > 0f) {
                            // Divide track into 3 equal zones for stable selection (works with weights)
                            val zone = (offset.x / trackPxWidth * 3).toInt().coerceIn(0, 2)
                            if (slotEnabledList[zone]) {
                                onSelected(zone)
                                val center = (zone + 0.5f) * (trackPxWidth / 3f)
                                val target = (center - indicatorSizePx / 2f).coerceIn(0f, trackPxWidth - indicatorSizePx)
                                scope.launch {
                                    indicatorX.animateTo(
                                        target,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                                    )
                                }
                            }
                        }
                    }
                 }
                 .background(finalTrackColor, RoundedCornerShape(trackHeight / 2)),
              contentAlignment = Alignment.CenterStart
          ) {

            // Indicator
            if (trackPxWidth > 0f) {
                val x = indicatorX.value.coerceIn(0f, trackPxWidth - indicatorSizePx)
                val xDp = with(density) { x.toDp() }

                Box(
                    modifier = Modifier
                        .offset(x = xDp)
                        .size(indicatorSize)
                        .clip(CircleShape)
                        .background(finalIndicatorColor)
                )
            }

            // Icons row inside the track. Use equal weights so the 3 slots (including disabled)
            // always divide the space evenly -> stable indicator math and no layout-dependent centers.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // small vertical padding so icons sit slightly above the indicator center
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until slotCount) {
                    val desc = contentDescriptions?.getOrNull(i) ?: "State ${i + 1}"
                    val targetTint = if (i == selectedIndex) finalIconTint else finalUnselectedIconTint
                    val tint by animateColorAsState(targetValue = targetTint)

                    val slotEnabled = slotEnabledList[i]

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(enabled = slotEnabled) { if (slotEnabled) onSelected(i) }
                            .semantics(mergeDescendants = false) { this.contentDescription = desc },
                        contentAlignment = Alignment.Center
                    ) {
                        Image(
                            painter = states[i],
                            contentDescription = desc,
                            modifier = Modifier.size(iconSize),
                            colorFilter = ColorFilter.tint(tint)
                        )
                    }
                }
            }
          }
      }
 }

 @Preview(showBackground = true, backgroundColor = 0x000000)
 @Composable
 fun ThreeStateTogglePreview() {
     AppTheme {
         var selected by remember { mutableStateOf(1) }
         val painters = listOf(
             rememberVectorPainter(Icons.Filled.Home),
             rememberVectorPainter(Icons.Filled.Favorite),
             rememberVectorPainter(Icons.Filled.Settings)
         )

         Column(
             modifier = Modifier.padding(16.dp),
             horizontalAlignment = Alignment.CenterHorizontally
         ) {
             ThreeStateToggle(
                 states = painters,
                 selectedIndex = selected,
                 onSelected = { selected = it },
                 trackWidth = 200.dp,
                 trackHeight = 50.dp,
                 indicatorSize = 40.dp,
                 iconSize = 24.dp,
                 contentDescriptions = listOf("Home", "Favorites", "Settings"),
                 enabled = true,
                 enabledStates = listOf(true, true, true)
             )
         }
     }
 }

 @Preview(showBackground = true, backgroundColor = 0xFF000000, name = "Photo+Audio Soda Dark")
 @Composable
 fun ThreeStateTogglePhotoAudioSodaDarkPreview() {
     CompositionLocalProvider(LocalAppColors provides SodaDarkAppColors) {
         var selected by remember { mutableStateOf(0) }
         val painters = listOf(
             rememberVectorPainter(Icons.Filled.Home),
             rememberVectorPainter(Icons.Filled.Favorite),
             rememberVectorPainter(Icons.Filled.Settings)
         )

         Column(
             modifier = Modifier.padding(16.dp),
             horizontalAlignment = Alignment.CenterHorizontally
         ) {
             ThreeStateToggle(
                 states = painters,
                 selectedIndex = selected,
                 onSelected = { selected = it },
                 contentDescriptions = listOf("Photo", "Video", "Audio"),
                 enabledStates = listOf(true, false, true)
             )
         }
     }
 }
