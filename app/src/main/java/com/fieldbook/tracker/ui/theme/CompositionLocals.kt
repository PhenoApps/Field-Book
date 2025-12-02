package com.fieldbook.tracker.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import com.fieldbook.tracker.ui.theme.colors.DefaultAppColors
import com.fieldbook.tracker.ui.theme.typography.CompactTypography

val LocalAppColors = staticCompositionLocalOf {
    DefaultAppColors
}

val LocalAppTypography = staticCompositionLocalOf {
    CompactTypography.medium
}