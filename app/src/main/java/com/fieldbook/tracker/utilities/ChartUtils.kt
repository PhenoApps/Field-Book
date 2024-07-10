package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.SharedPreferences
import android.util.DisplayMetrics
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.preferences.GeneralKeys

class ChartUtils {

    fun getMaxBars(context: Context, referenceMaxBars: Int): Int {
        val displayMetrics: DisplayMetrics = context.resources.displayMetrics
        val screenWidth = displayMetrics.widthPixels
        val referenceScreenWidth = 1080  // Default smartphone screen width in pixels

        return (referenceMaxBars * screenWidth) / referenceScreenWidth.coerceAtLeast(1)
    }

    fun getLabelRotation(context: Context, labels: List<String>): Float {
        val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
        val textSizeValue = preferences.getString(GeneralKeys.TEXT_THEME, "2")?.toInt() ?: 2

        val maxCharacters = when (textSizeValue) {
            1 -> 6
            2 -> 8
            3 -> 10
            4 -> 12
            else -> 8
        }

        return if (labels.any { it.length > maxCharacters }) 45f else 0f
    }
}
