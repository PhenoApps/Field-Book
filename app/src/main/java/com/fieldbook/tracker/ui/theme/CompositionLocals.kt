package com.fieldbook.tracker.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.fieldbook.tracker.ui.theme.colors.DefaultColors
import com.fieldbook.tracker.ui.theme.typography.MediumTypography

val LocalAppColors = staticCompositionLocalOf {
    DefaultColors
}

val LocalAppTypography = staticCompositionLocalOf {
    MediumTypography.medium
}