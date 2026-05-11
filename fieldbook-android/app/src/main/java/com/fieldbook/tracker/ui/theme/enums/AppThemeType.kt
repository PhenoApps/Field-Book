package com.fieldbook.tracker.ui.theme.enums

sealed class AppThemeType {
    object Default : AppThemeType()
    object HighContrast : AppThemeType()
    object Blue : AppThemeType()
}