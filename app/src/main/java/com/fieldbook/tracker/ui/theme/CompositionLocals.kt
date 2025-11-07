package com.fieldbook.tracker.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.fieldbook.tracker.ui.theme.colors.DefaultColors
import com.fieldbook.tracker.ui.theme.typography.MediumTypography
import com.fieldbook.tracker.ui.theme.typography.SmallTypography

val LocalAppColors = staticCompositionLocalOf {
    DefaultColors
}

val LocalAppTypography = staticCompositionLocalOf {
    MediumTypography
}