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
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.SharedPreferenceUtils
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
open class ThemedActivity: AppCompatActivity() {

    companion object {

        val TAG = ThemedActivity::class.simpleName

        private data class ThemePair(val color: Int, val size: Int)

        const val DEFAULT = 0
        const val HIGH_CONTRAST = 1
        const val BLUE = 2

        const val SMALL = 0
        const val MEDIUM = 1
        const val LARGE = 2
        const val EXTRA_LARGE = 3

        fun applyTheme(activity: Activity) {

            val prefs = PreferenceManager.getDefaultSharedPreferences(activity)

            //set the theme
            val (themeIndex, textIndex) = with(prefs) {

                (getString(PreferenceKeys.THEME, "0")?.toInt()
                    ?: 0) to (getString(PreferenceKeys.TEXT_THEME, "1")?.toInt() ?: 1)

            }

            var statusBarColor = ContextCompat.getColor(activity, R.color.main_primary_dark)

            activity.runOnUiThread {

                when (ThemePair(themeIndex, textIndex)) {

                    //small text themes
                    ThemePair(DEFAULT, SMALL) -> {
                        activity.setTheme(R.style.BaseAppTheme_SmallTextTheme)
                    }
                    ThemePair(HIGH_CONTRAST, SMALL) -> {
                        activity.setTheme(R.style.BaseAppTheme_HighContrast_SmallTextTheme)
                        statusBarColor = ContextCompat.getColor(activity, R.color.high_contrast_primary_dark)
                    }
                    ThemePair(BLUE, SMALL) -> {
                        activity.setTheme(R.style.BaseAppTheme_Blue_SmallTextTheme)
                        statusBarColor = ContextCompat.getColor(activity, R.color.blue_primary_dark)
                    }

                    //medium text themes
                    ThemePair(DEFAULT, MEDIUM) -> {
                        activity.setTheme(R.style.BaseAppTheme_MediumTextTheme)
                    }
                    ThemePair(HIGH_CONTRAST, MEDIUM) -> {
                        activity.setTheme(R.style.BaseAppTheme_HighContrast_MediumTextTheme)
                        statusBarColor = ContextCompat.getColor(activity, R.color.high_contrast_primary_dark)
                    }
                    ThemePair(BLUE, MEDIUM) -> {
                        activity.setTheme(R.style.BaseAppTheme_Blue_MediumTextTheme)
                        statusBarColor = ContextCompat.getColor(activity, R.color.blue_primary_dark)
                    }

                    //large text themes
                    ThemePair(DEFAULT, LARGE) -> {
                        activity.setTheme(R.style.BaseAppTheme_LargeTextTheme)
                    }
                    ThemePair(HIGH_CONTRAST, LARGE) -> {
                        activity.setTheme(R.style.BaseAppTheme_HighContrast_LargeTextTheme)
                        statusBarColor = ContextCompat.getColor(activity, R.color.high_contrast_primary_dark)
                    }
                    ThemePair(BLUE, LARGE) -> {
                        activity.setTheme(R.style.BaseAppTheme_Blue_LargeTextTheme)
                        statusBarColor = ContextCompat.getColor(activity, R.color.blue_primary_dark)
                    }

                    //extra large text themes
                    ThemePair(DEFAULT, EXTRA_LARGE) -> {
                        activity.setTheme(R.style.BaseAppTheme_ExtraLargeTextTheme)
                    }
                    ThemePair(HIGH_CONTRAST, EXTRA_LARGE) -> {
                        activity.setTheme(R.style.BaseAppTheme_HighContrast_ExtraLargeTextTheme)
                        statusBarColor = ContextCompat.getColor(activity, R.color.high_contrast_primary_dark)
                    }
                    ThemePair(BLUE, EXTRA_LARGE) -> {
                        activity.setTheme(R.style.BaseAppTheme_Blue_ExtraLargeTextTheme)
                        statusBarColor = ContextCompat.getColor(activity, R.color.blue_primary_dark)
                    }
                }

                Log.d(TAG, "Applying theme $themeIndex to ${activity::class.simpleName}")

                if (activity is AboutActivity) {

                    when (ThemePair(themeIndex, textIndex)) {

                        //small text themes
                        ThemePair(DEFAULT, SMALL) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_SmallTextTheme)
                        }
                        ThemePair(HIGH_CONTRAST, SMALL) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_HighContrast_SmallTextTheme)
                            statusBarColor = ContextCompat.getColor(activity, R.color.high_contrast_primary_dark)
                        }
                        ThemePair(BLUE, SMALL) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_Blue_SmallTextTheme)
                            statusBarColor = ContextCompat.getColor(activity, R.color.blue_primary_dark)
                        }

                        //medium text themes
                        ThemePair(DEFAULT, MEDIUM) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_MediumTextTheme)
                        }
                        ThemePair(HIGH_CONTRAST, MEDIUM) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_HighContrast_MediumTextTheme)
                            statusBarColor = ContextCompat.getColor(activity, R.color.high_contrast_primary_dark)
                        }
                        ThemePair(BLUE, MEDIUM) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_Blue_MediumTextTheme)
                            statusBarColor = ContextCompat.getColor(activity, R.color.blue_primary_dark)
                        }

                        //large text themes
                        ThemePair(DEFAULT, LARGE) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_LargeTextTheme)
                        }
                        ThemePair(HIGH_CONTRAST, LARGE) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_HighContrast_LargeTextTheme)
                            statusBarColor = ContextCompat.getColor(activity, R.color.high_contrast_primary_dark)
                        }
                        ThemePair(BLUE, LARGE) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_Blue_LargeTextTheme)
                            statusBarColor = ContextCompat.getColor(activity, R.color.blue_primary_dark)
                        }

                        //extra large text themes
                        ThemePair(DEFAULT, EXTRA_LARGE) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_ExtraLargeTextTheme)
                        }
                        ThemePair(HIGH_CONTRAST, EXTRA_LARGE) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_HighContrast_ExtraLargeTextTheme)
                            statusBarColor = ContextCompat.getColor(activity, R.color.high_contrast_primary_dark)
                        }
                        ThemePair(BLUE, EXTRA_LARGE) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_Blue_ExtraLargeTextTheme)
                            statusBarColor = ContextCompat.getColor(activity, R.color.blue_primary_dark)
                        }
                    }
                }

                //TODO this doesn't seem to be doing its job (must be set in manifest)
                if (activity is FileExploreActivity) {

                    when (themeIndex) {
                        0 -> {
                            activity.setTheme(R.style.ActivityDialog)
                        }
                        1 -> {
                            activity.setTheme(R.style.ActivityDialog_HighContrast)
                        }
                        2 -> {
                            activity.setTheme(R.style.ActivityDialog_Blue)
                        }
                    }
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