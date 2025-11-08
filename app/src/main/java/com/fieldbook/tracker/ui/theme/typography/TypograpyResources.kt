package com.fieldbook.tracker.ui.theme.typography

import androidx.activity.compose.LocalActivity
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.sp

/**
 * Define text sizes for compact devices (smartphones)
 */
val SmallTypography = ThemeTypography(
    titleSize = 18.sp,
    bodySize = 14.sp,
    subheadingSize = 10.sp,
)

val MediumTypography = ThemeTypography(
    titleSize = 20.sp,
    bodySize = 16.sp,
    subheadingSize = 14.sp,
)

val LargeTypography = ThemeTypography(
    titleSize = 24.sp,
    bodySize = 20.sp,
    subheadingSize = 18.sp,
)

val ExtraLargeTypography = ThemeTypography(
    titleSize = 28.sp,
    bodySize = 24.sp,
    subheadingSize = 22.sp,
)

/**
 * Adjusts text sizes for bigger devices (tablets)
 */
@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun getResponsiveTypography(
    baseTypography: ThemeTypography
): ThemeTypography {
    val activity = LocalActivity.current

    if (activity == null) return baseTypography

    val windowSizeClass = calculateWindowSizeClass(activity = activity)

    return when (windowSizeClass.widthSizeClass) {
        WindowWidthSizeClass.Expanded -> baseTypography.copy(
            titleSize = baseTypography.titleSize * 1.4f,
            bodySize = baseTypography.bodySize * 1.5f,
            subheadingSize = baseTypography.subheadingSize * 1.4f
        )
        WindowWidthSizeClass.Medium -> baseTypography.copy(
            titleSize = baseTypography.titleSize * 1.25f,
            bodySize = baseTypography.bodySize * 1.3f,
            subheadingSize = baseTypography.subheadingSize * 1.25f
        )
        else -> baseTypography
    }
}