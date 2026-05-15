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

        const val DEFAULT = 0
        const val HIGH_CONTRAST = 1
        const val BLUE = 2
        const val SODA_DARK = 3

        const val SMALL = 0
        const val MEDIUM = 1
        const val LARGE = 2
        const val EXTRA_LARGE = 3

        private data class ThemeStyleSet(
            val small: Int,
            val medium: Int,
            val large: Int,
            val extraLarge: Int,
            val statusBarColorRes: Int,
        )

        private data class MalThemeStyleSet(
            val small: Int,
            val medium: Int,
            val large: Int,
            val extraLarge: Int,
        )

        private val themeStyles = mapOf(
            DEFAULT to ThemeStyleSet(
                small = R.style.BaseAppTheme_SmallTextTheme,
                medium = R.style.BaseAppTheme_MediumTextTheme,
                large = R.style.BaseAppTheme_LargeTextTheme,
                extraLarge = R.style.BaseAppTheme_ExtraLargeTextTheme,
                statusBarColorRes = R.color.main_primary_dark,
            ),
            HIGH_CONTRAST to ThemeStyleSet(
                small = R.style.BaseAppTheme_HighContrast_SmallTextTheme,
                medium = R.style.BaseAppTheme_HighContrast_MediumTextTheme,
                large = R.style.BaseAppTheme_HighContrast_LargeTextTheme,
                extraLarge = R.style.BaseAppTheme_HighContrast_ExtraLargeTextTheme,
                statusBarColorRes = R.color.high_contrast_primary_dark,
            ),
            BLUE to ThemeStyleSet(
                small = R.style.BaseAppTheme_Blue_SmallTextTheme,
                medium = R.style.BaseAppTheme_Blue_MediumTextTheme,
                large = R.style.BaseAppTheme_Blue_LargeTextTheme,
                extraLarge = R.style.BaseAppTheme_Blue_ExtraLargeTextTheme,
                statusBarColorRes = R.color.blue_primary_dark,
            ),
            SODA_DARK to ThemeStyleSet(
                small = R.style.BaseAppTheme_SodaDark_SmallTextTheme,
                medium = R.style.BaseAppTheme_SodaDark_MediumTextTheme,
                large = R.style.BaseAppTheme_SodaDark_LargeTextTheme,
                extraLarge = R.style.BaseAppTheme_SodaDark_ExtraLargeTextTheme,
                statusBarColorRes = R.color.soda_dark_window,
            ),
        )

        private val malThemeStyles = mapOf(
            DEFAULT to MalThemeStyleSet(
                small = R.style.BaseAppTheme_Mal_SmallTextTheme,
                medium = R.style.BaseAppTheme_Mal_MediumTextTheme,
                large = R.style.BaseAppTheme_Mal_LargeTextTheme,
                extraLarge = R.style.BaseAppTheme_Mal_ExtraLargeTextTheme,
            ),
            HIGH_CONTRAST to MalThemeStyleSet(
                small = R.style.BaseAppTheme_Mal_HighContrast_SmallTextTheme,
                medium = R.style.BaseAppTheme_Mal_HighContrast_MediumTextTheme,
                large = R.style.BaseAppTheme_Mal_HighContrast_LargeTextTheme,
                extraLarge = R.style.BaseAppTheme_Mal_HighContrast_ExtraLargeTextTheme,
            ),
            BLUE to MalThemeStyleSet(
                small = R.style.BaseAppTheme_Mal_Blue_SmallTextTheme,
                medium = R.style.BaseAppTheme_Mal_Blue_MediumTextTheme,
                large = R.style.BaseAppTheme_Mal_Blue_LargeTextTheme,
                extraLarge = R.style.BaseAppTheme_Mal_Blue_ExtraLargeTextTheme,
            ),
            SODA_DARK to MalThemeStyleSet(
                small = R.style.BaseAppTheme_Mal_SodaDark_SmallTextTheme,
                medium = R.style.BaseAppTheme_Mal_SodaDark_MediumTextTheme,
                large = R.style.BaseAppTheme_Mal_SodaDark_LargeTextTheme,
                extraLarge = R.style.BaseAppTheme_Mal_SodaDark_ExtraLargeTextTheme,
            ),
        )

        private val dialogThemeStyles = mapOf(
            DEFAULT to R.style.ActivityDialog,
            HIGH_CONTRAST to R.style.ActivityDialog_HighContrast,
            BLUE to R.style.ActivityDialog_Blue,
            SODA_DARK to R.style.ActivityDialog_SodaDark,
        )

        private fun themeStyleFor(textIndex: Int, styles: ThemeStyleSet): Int = when (textIndex) {
            SMALL -> styles.small
            MEDIUM -> styles.medium
            LARGE -> styles.large
            EXTRA_LARGE -> styles.extraLarge
            else -> styles.medium
        }

        private fun malThemeStyleFor(textIndex: Int, styles: MalThemeStyleSet): Int = when (textIndex) {
            SMALL -> styles.small
            MEDIUM -> styles.medium
            LARGE -> styles.large
            EXTRA_LARGE -> styles.extraLarge
            else -> styles.medium
        }

        @JvmStatic
        fun resolveActivityThemeStyle(context: android.content.Context): Int {
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            val themeIndex = prefs.getString(PreferenceKeys.THEME, "0")?.toInt() ?: 0
            val textIndex = prefs.getString(PreferenceKeys.TEXT_THEME, "1")?.toInt() ?: 1
            val styles = themeStyles[themeIndex] ?: themeStyles.getValue(DEFAULT)
            return themeStyleFor(textIndex, styles)
        }

        fun applyTheme(activity: Activity) {

            val prefs = PreferenceManager.getDefaultSharedPreferences(activity)

            val themeIndex = prefs.getString(PreferenceKeys.THEME, "0")?.toInt() ?: 0
            val textIndex = prefs.getString(PreferenceKeys.TEXT_THEME, "1")?.toInt() ?: 1

            val styles = themeStyles[themeIndex] ?: themeStyles.getValue(DEFAULT)
            var statusBarColor = ContextCompat.getColor(activity, styles.statusBarColorRes)

            activity.runOnUiThread {

                activity.setTheme(themeStyleFor(textIndex, styles))

                Log.d(TAG, "Applying theme $themeIndex to ${activity::class.simpleName}")

                if (activity is AboutActivity) {
                    val malStyles = malThemeStyles[themeIndex] ?: malThemeStyles.getValue(DEFAULT)
                    activity.setTheme(malThemeStyleFor(textIndex, malStyles))
                    statusBarColor = ContextCompat.getColor(activity, styles.statusBarColorRes)
                }

                //TODO this doesn't seem to be doing its job (must be set in manifest)
                if (activity is FileExploreActivity) {
                    activity.setTheme(
                        dialogThemeStyles[themeIndex] ?: dialogThemeStyles.getValue(DEFAULT)
                    )
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
