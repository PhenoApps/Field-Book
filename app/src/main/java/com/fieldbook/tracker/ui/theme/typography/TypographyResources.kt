package com.fieldbook.tracker.ui.theme.typography

import androidx.compose.ui.unit.sp

/**
 * Text sizes for different devices sizes
 *      - Compact for smartphones
 *      - Medium for 600dp screen width
 *      - Expanded for 720dp screen width
 */

object CompactTypography {
    val small = ThemeTypography(
        titleSize = 18.sp,
        bodySize = 14.sp,
        subheadingSize = 10.sp,
    )

    val medium = ThemeTypography(
        titleSize = 20.sp,
        bodySize = 16.sp,
        subheadingSize = 14.sp,
    )

    val large = ThemeTypography(
        titleSize = 24.sp,
        bodySize = 20.sp,
        subheadingSize = 18.sp,
    )

    val extraLarge = ThemeTypography(
        titleSize = 28.sp,
        bodySize = 24.sp,
        subheadingSize = 22.sp,
    )

    val all = listOf(small, medium, large, extraLarge)
}

object MediumTypography {
    val small = ThemeTypography(
        titleSize = 22.sp,
        bodySize = 18.sp,
        subheadingSize = 12.sp,
    )

    val medium = ThemeTypography(
        titleSize = 30.sp,
        bodySize = 25.sp,
        subheadingSize = 10.sp,
    )

    val large = ThemeTypography(
        titleSize = 35.sp,
        bodySize = 30.sp,
        subheadingSize = 22.sp,
    )

    val extraLarge = ThemeTypography(
        titleSize = 40.sp,
        bodySize = 35.sp,
        subheadingSize = 26.sp,
    )

    val all = listOf(small, medium, large, extraLarge)
}

object ExpandedTypography {
    val small = ThemeTypography(
        titleSize = 26.sp,
        bodySize = 22.sp,
        subheadingSize = 16.sp,
    )

    val medium = ThemeTypography(
        titleSize = 40.sp,
        bodySize = 34.sp,
        subheadingSize = 24.sp,
    )

    val large = ThemeTypography(
        titleSize = 48.sp,
        bodySize = 40.sp,
        subheadingSize = 30.sp,
    )

    val extraLarge = ThemeTypography(
        titleSize = 56.sp,
        bodySize = 48.sp,
        subheadingSize = 36.sp,
    )

    val all = listOf(small, medium, large, extraLarge)
}