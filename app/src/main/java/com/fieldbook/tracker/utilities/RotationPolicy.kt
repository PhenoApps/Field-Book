package com.fieldbook.tracker.utilities

import android.app.Activity
import android.content.pm.ActivityInfo

/**
 * Centralized orientation policy used by a few screens.
 *
 * - Phones: keep portrait-locked
 * - Tablets: respect the user's auto-rotate setting
 */
object RotationPolicy {
    // Tablets: allow rotation when system auto-rotate is enabled.
    const val TABLET_ROTATION_MIN_SW_DP = 800

    fun apply(activity: Activity) {
        val swDp = activity.resources.configuration.smallestScreenWidthDp
        activity.requestedOrientation =
            if (swDp >= TABLET_ROTATION_MIN_SW_DP) {
                ActivityInfo.SCREEN_ORIENTATION_USER
            } else {
                ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
    }
}

