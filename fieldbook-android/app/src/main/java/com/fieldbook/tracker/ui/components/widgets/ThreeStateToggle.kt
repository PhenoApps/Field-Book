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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fieldbook.tracker.ui.theme.AppTheme
import com.fieldbook.tracker.ui.theme.colors.ToggleColors
import kotlinx.coroutines.launch
import kotlin.math.abs

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

    // Track measured width in px
    var trackPxWidth by remember { mutableStateOf(0f) }
    // Track left in window coordinates (px)
    var trackLeftWindowPx by remember { mutableStateOf(0f) }

    // Animatable for indicator X offset (px) relative to track left
    val indicatorX = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // Measured icon centers in window coordinates
    val iconCentersWindow = remember { mutableStateListOf<Float>().apply { repeat(slotCount) { add(0f) } } }

    // Helper to compute centers relative to track left (px)
    fun centersRelativeToTrack(): List<Float> {
        if (trackLeftWindowPx == 0f) return emptyList()
        return iconCentersWindow.map { it - trackLeftWindowPx }
    }

    // Convert indicator size to px
    val indicatorSizePx: Float = with(density) { indicatorSize.toPx() }

    // When selection changes or when track measurement changes, animate indicator to the selected center
    LaunchedEffect(selectedIndex, iconCentersWindow.toList(), trackLeftWindowPx, trackPxWidth) {
        val centers = centersRelativeToTrack()
        if (centers.size == slotCount && trackPxWidth > 0f) {
            val centerPx = centers.getOrNull(selectedIndex) ?: centers.first()
            val targetOffset = (centerPx - indicatorSizePx / 2f).coerceIn(0f, trackPxWidth - indicatorSizePx)
            indicatorX.animateTo(
                targetOffset,
                // softer spring
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
                    trackLeftWindowPx = layoutCoordinates.positionInWindow().x
                     // If indicator not initialized yet, snap to initial selected center
                    val centers = centersRelativeToTrack()
                    if (indicatorX.value == 0f && centers.size == slotCount && trackPxWidth > 0f) {
                        scope.launch {
                            val centerPx = centers.getOrNull(selectedIndex) ?: centers.first()
                            val initial = (centerPx - indicatorSizePx / 2f).coerceIn(0f, trackPxWidth - indicatorSizePx)
                            indicatorX.snapTo(initial)
                        }
                    }
                 }
                 .pointerInput(iconCentersWindow.toList(), trackLeftWindowPx) {
                    detectTapGestures { offset ->
                        val centers = centersRelativeToTrack()
                         if (centers.size == slotCount) {
                             val tapX = offset.x
                             var bestIndex = 0
                             var bestDist = Float.MAX_VALUE
                             centers.forEachIndexed { idx, center ->
                                 val d = abs(center - tapX)
                                 if (d < bestDist) {
                                     bestDist = d
                                     bestIndex = idx
                                 }
                             }
                            // Ignore taps on disabled slots
                            val slotEnabled = enabledStates?.getOrNull(bestIndex) ?: enabled
                            if (!slotEnabled) return@detectTapGestures
                             onSelected(bestIndex)
                             // animate indicator to feedback
                             val targetOffsetImmediate = (centers[bestIndex] - indicatorSizePx / 2f).coerceIn(0f, trackPxWidth - indicatorSizePx)
                             scope.launch {
                                 indicatorX.animateTo(
                                     targetOffsetImmediate,
                                     animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow)
                                 )
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

            // Icons row inside the track so icons are visually within the oval container.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // small vertical padding so icons sit slightly above the indicator center
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0 until slotCount) {
                    val desc = contentDescriptions?.getOrNull(i) ?: "State ${i + 1}"
                    val targetTint = if (i == selectedIndex) finalIconTint else finalUnselectedIconTint
                    val tint by animateColorAsState(targetValue = targetTint)

                    val slotEnabled = enabledStates?.getOrNull(i) ?: enabled

                    Box(
                        modifier = Modifier
                            .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                            .clickable(enabled = slotEnabled) { if (slotEnabled) onSelected(i) }
                             .onGloballyPositioned { coords ->
                                 // record center X in window coordinates
                                 val center = coords.positionInWindow().x + coords.size.width / 2f
                                 iconCentersWindow[i] = center
                             }
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
