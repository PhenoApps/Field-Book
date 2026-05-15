package com.fieldbook.tracker.activities

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.utilities.AppThemeResolver
import com.fieldbook.tracker.utilities.SharedPreferenceUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
open class ThemedActivity: AppCompatActivity() {

    companion object {

        val TAG = ThemedActivity::class.simpleName

        const val DEFAULT = 0
        const val HIGH_CONTRAST = 1
        const val BLUE = 2
        const val SODA_DARK = 3

        const val SMALL = 0
        const val MEDIUM = 1
        const val LARGE = 2
        const val EXTRA_LARGE = 3

        @JvmStatic
        fun resolveActivityThemeStyle(context: android.content.Context): Int =
            AppThemeResolver.activityThemeStyle(context)

        fun applyTheme(activity: Activity) {

            val prefs = PreferenceManager.getDefaultSharedPreferences(activity)
            val themeIndex = AppThemeResolver.themeIndex(prefs)
            var statusBarColor = ContextCompat.getColor(
                activity,
                AppThemeResolver.statusBarColorRes(themeIndex),
            )

            activity.runOnUiThread {

                activity.setTheme(AppThemeResolver.activityThemeStyle(prefs))

                Log.d(TAG, "Applying theme $themeIndex to ${activity::class.simpleName}")

                if (activity is AboutActivity) {
                    activity.setTheme(AppThemeResolver.malActivityThemeStyle(prefs))
                    statusBarColor = ContextCompat.getColor(
                        activity,
                        AppThemeResolver.statusBarColorRes(themeIndex),
                    )
                }

                //TODO this doesn't seem to be doing its job (must be set in manifest)
                if (activity is FileExploreActivity) {
                    activity.setTheme(AppThemeResolver.dialogThemeStyle(themeIndex))
                }

                if (activity is PreferencesActivity) {
                    activity.setTheme(R.style.PreferenceTheme)
                }
            }

            //status bar color based on colorPrimaryDark as of Lollipop 5.0 (API 21)
            //for some reason (android bug?) this doesn't change from setTheme() automatically and keeps the old color
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
                activity.window.statusBarColor = statusBarColor
            }
        }
    }

    @Inject
    lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
    }

    override fun onResume() {
        applyTheme(this)
        super.onResume()
    }

    override fun finishActivity(requestCode: Int) {
        super.finishActivity(requestCode)
        disableTransitionAnimations()
    }

    /**
     * Register this callback in activities where you would have called super.onBackPressed()
     * Do not register in activities which already have custom OnBackPressedCallback eg. Config, CollectActivity
     */
    protected fun standardBackCallback(): OnBackPressedCallback {
        return object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                finish()
            }
        }
    }

    /**
     * Use this for activities which have fragments and don't require special handling eg. Statistics, Preferences activities
     */
    protected fun fragmentBasedBackCallback(): OnBackPressedCallback {
        return object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (supportFragmentManager.backStackEntryCount > 0) {
                    supportFragmentManager.popBackStack()
                } else {
                    finish()
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        disableTransitionAnimations()
    }

    private fun disableTransitionAnimations() {
        if (SharedPreferenceUtils.isHighContrastTheme(prefs)) {
            if (Build.VERSION.SDK_INT >= 34) {
                overrideActivityTransition(OVERRIDE_TRANSITION_CLOSE, 0, 0)
                overrideActivityTransition(OVERRIDE_TRANSITION_OPEN, 0, 0)
            } else {
                overridePendingTransition(0, 0)
            }
        }
    }

    override fun startActivity(intent: Intent?) {
        if (SharedPreferenceUtils.isHighContrastTheme(prefs)) {
            intent?.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
        }
        super.startActivity(intent)
    }
}
