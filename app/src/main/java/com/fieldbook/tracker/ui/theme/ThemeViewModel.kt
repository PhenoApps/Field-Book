package com.fieldbook.tracker.ui.theme

import android.content.SharedPreferences
import androidx.lifecycle.ViewModel
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.ui.theme.enums.AppTextType
import com.fieldbook.tracker.ui.theme.enums.AppThemeType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

/**
 * Provides reactive state theming updates (colors and text sizes) based on user preferences.
 */
@HiltViewModel
class ThemeViewModel @Inject constructor(
    private val prefs: SharedPreferences,
) : ViewModel() {

    private val _themeType = MutableStateFlow(getThemeType())
    val themeType: StateFlow<AppThemeType> = _themeType.asStateFlow()

    private val _textType = MutableStateFlow(getTextType())
    val textType: StateFlow<AppTextType> = _textType.asStateFlow()

    private fun getThemeType(): AppThemeType {
        val themeIndex = prefs.getString(PreferenceKeys.THEME, "0")?.toInt() ?: 0
        return when (themeIndex) {
            0 -> AppThemeType.Default
            1 -> AppThemeType.HighContrast
            2 -> AppThemeType.Blue
            else -> AppThemeType.Default
        }
    }

    private fun getTextType(): AppTextType {
        val textIndex = prefs.getString(PreferenceKeys.TEXT_THEME, "1")?.toInt() ?: 1
        return AppTextType.entries.find { it.index == textIndex } ?: AppTextType.MEDIUM
    }
}