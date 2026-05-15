package com.fieldbook.tracker.ui.theme.enums

sealed class AppThemeType {
    object Default : AppThemeType()
    object HighContrast : AppThemeType()
    object Blue : AppThemeType()
    object SodaDark : AppThemeType()

    val isDark: Boolean
        get() = this is SodaDark
}