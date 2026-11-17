package com.fieldbook.tracker.ui.theme.enums

sealed class AppThemeType(val index: Int) {
    object Default : AppThemeType(0)
    object HighContrast : AppThemeType(1)
    object Blue : AppThemeType(2)

    companion object {
        val DEFAULT: AppThemeType = Default

        fun fromIndex(index: Int): AppThemeType = when (index) {
            HighContrast.index -> HighContrast
            Blue.index -> Blue
            else -> DEFAULT
        }
    }
}