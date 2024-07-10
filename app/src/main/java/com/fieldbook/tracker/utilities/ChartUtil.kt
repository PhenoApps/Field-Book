package com.fieldbook.tracker.utilities

import android.content.Context
import android.content.SharedPreferences
import android.util.DisplayMetrics
import android.util.Log
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.preferences.GeneralKeys

class ChartUtil {

    companion object {
        private const val BAR_WIDTH_THRESHOLD = 150 // pixels
        private const val MAX_ROTATION_ANGLE = 90f // degrees
        private const val TAG = "ChartUtil"

        data class ChartConfig(val maxCharacters: Int, val screenWidth: Int, val textSize: Float)

        fun getChartConfig(context: Context): ChartConfig {
            val preferences: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
            val textSizeValue = preferences.getString(GeneralKeys.TEXT_THEME, "2")?.toInt() ?: 2

            val maxCharacters = when (textSizeValue) {
                1 -> 18
                2 -> 24
                3 -> 32
                4 -> 40
                else -> 24
            }

            val textSize = when (textSizeValue) {
                1 -> 12f
                2 -> 14f
                3 -> 16f
                4 -> 18f
                else -> 14f
            }

            val displayMetrics: DisplayMetrics = context.resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels

            return ChartConfig(maxCharacters, screenWidth, textSize)
        }

        fun getMaxBars(context: Context, referenceMaxBars: Int): Int {
            val (_, screenWidth, _) = getChartConfig(context)
            val referenceScreenWidth = 1080  // Default smartphone screen width in pixels

            // Detailed logging of arguments and intermediate calculations
            Log.d(TAG, "Context: $context")
            Log.d(TAG, "Reference Max Bars: $referenceMaxBars")
            Log.d(TAG, "Screen Width: $screenWidth")
            Log.d(TAG, "Reference Screen Width: $referenceScreenWidth")

            val maxBarsByScreen = (referenceMaxBars * screenWidth) / referenceScreenWidth.coerceAtLeast(1)
            Log.d(TAG, "Max Bars By Screen: $maxBarsByScreen")

            val finalMaxBars = maxBarsByScreen.coerceAtMost(referenceMaxBars)
            Log.d(TAG, "Final Max Bars: $finalMaxBars")

            return finalMaxBars
        }
    }
}
