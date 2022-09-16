package com.fieldbook.tracker.activities

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys

open class ThemedActivity: AppCompatActivity() {

    companion object {

        val TAG = ThemedActivity::class.simpleName

        private data class ThemePair(val color: Int, val size: Int)

        const val DEFAULT = 0
        const val HIGH_CONTRAST = 1
        const val BLUE = 2

        const val EXTRA_SMALL = 0
        const val SMALL = 1
        const val MEDIUM = 2
        const val LARGE = 3
        const val EXTRA_LARGE = 4
        const val EXTRA_EXTRA_LARGE = 5

        fun applyTheme(activity: Activity) {

            //set the theme
            val (themeIndex, textIndex) = with (PreferenceManager.getDefaultSharedPreferences(activity)) {

                (getString(GeneralKeys.THEME, "0")?.toInt() ?: 0) to (getString(GeneralKeys.TEXT_THEME, "0")?.toInt() ?: 0)

            }

            var statusBarColor = activity.getColor(R.color.main_primary_dark)

            activity.runOnUiThread {

                when (ThemePair(themeIndex, textIndex)) {

                    //extra small text themes
                    ThemePair(DEFAULT, EXTRA_SMALL) -> {
                        activity.setTheme(R.style.BaseAppTheme_ExtraSmallTextTheme)
                    }
                    ThemePair(HIGH_CONTRAST, EXTRA_SMALL) -> {
                        activity.setTheme(R.style.BaseAppTheme_HighContrast_ExtraSmallTextTheme)
                        statusBarColor = activity.getColor(R.color.high_contrast_primary_dark)
                    }
                    ThemePair(BLUE, EXTRA_SMALL) -> {
                        activity.setTheme(R.style.BaseAppTheme_Blue_ExtraSmallTextTheme)
                        statusBarColor = activity.getColor(R.color.blue_primary_dark)
                    }

                    //small text themes
                    ThemePair(DEFAULT, SMALL) -> {
                        activity.setTheme(R.style.BaseAppTheme_SmallTextTheme)
                    }
                    ThemePair(HIGH_CONTRAST, SMALL) -> {
                        activity.setTheme(R.style.BaseAppTheme_HighContrast_SmallTextTheme)
                        statusBarColor = activity.getColor(R.color.high_contrast_primary_dark)
                    }
                    ThemePair(BLUE, SMALL) -> {
                        activity.setTheme(R.style.BaseAppTheme_Blue_SmallTextTheme)
                        statusBarColor = activity.getColor(R.color.blue_primary_dark)
                    }

                    //medium text themes
                    ThemePair(DEFAULT, MEDIUM) -> {
                        activity.setTheme(R.style.BaseAppTheme_MediumTextTheme)
                    }
                    ThemePair(HIGH_CONTRAST, MEDIUM) -> {
                        activity.setTheme(R.style.BaseAppTheme_HighContrast_MediumTextTheme)
                        statusBarColor = activity.getColor(R.color.high_contrast_primary_dark)
                    }
                    ThemePair(BLUE, MEDIUM) -> {
                        activity.setTheme(R.style.BaseAppTheme_Blue_MediumTextTheme)
                        statusBarColor = activity.getColor(R.color.blue_primary_dark)
                    }

                    //large text themes
                    ThemePair(DEFAULT, LARGE) -> {
                        activity.setTheme(R.style.BaseAppTheme_LargeTextTheme)
                    }
                    ThemePair(HIGH_CONTRAST, LARGE) -> {
                        activity.setTheme(R.style.BaseAppTheme_HighContrast_LargeTextTheme)
                        statusBarColor = activity.getColor(R.color.high_contrast_primary_dark)
                    }
                    ThemePair(BLUE, LARGE) -> {
                        activity.setTheme(R.style.BaseAppTheme_Blue_LargeTextTheme)
                        statusBarColor = activity.getColor(R.color.blue_primary_dark)
                    }

                    //extra large text themes
                    ThemePair(DEFAULT, EXTRA_LARGE) -> {
                        activity.setTheme(R.style.BaseAppTheme_ExtraLargeTextTheme)
                    }
                    ThemePair(HIGH_CONTRAST, EXTRA_LARGE) -> {
                        activity.setTheme(R.style.BaseAppTheme_HighContrast_ExtraLargeTextTheme)
                        statusBarColor = activity.getColor(R.color.high_contrast_primary_dark)
                    }
                    ThemePair(BLUE, EXTRA_LARGE) -> {
                        activity.setTheme(R.style.BaseAppTheme_Blue_ExtraLargeTextTheme)
                        statusBarColor = activity.getColor(R.color.blue_primary_dark)
                    }

                    //extra extra large text themes
                    ThemePair(DEFAULT, EXTRA_EXTRA_LARGE) -> {
                        activity.setTheme(R.style.BaseAppTheme_ExtraExtraLargeTextTheme)
                    }
                    ThemePair(HIGH_CONTRAST, EXTRA_EXTRA_LARGE) -> {
                        activity.setTheme(R.style.BaseAppTheme_HighContrast_ExtraExtraLargeTextTheme)
                        statusBarColor = activity.getColor(R.color.high_contrast_primary_dark)
                    }
                    ThemePair(BLUE, EXTRA_EXTRA_LARGE) -> {
                        activity.setTheme(R.style.BaseAppTheme_Blue_ExtraExtraLargeTextTheme)
                        statusBarColor = activity.getColor(R.color.blue_primary_dark)
                    }
                }

                Log.d(TAG, "Applying theme $themeIndex to ${activity::class.simpleName}")

                if (activity is AboutActivity) {

                    when (ThemePair(themeIndex, textIndex)) {

                        //extra small text themes
                        ThemePair(DEFAULT, EXTRA_SMALL) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_ExtraSmallTextTheme)
                        }
                        ThemePair(HIGH_CONTRAST, EXTRA_SMALL) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_HighContrast_ExtraSmallTextTheme)
                            statusBarColor = activity.getColor(R.color.high_contrast_primary_dark)
                        }
                        ThemePair(BLUE, EXTRA_SMALL) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_Blue_ExtraSmallTextTheme)
                            statusBarColor = activity.getColor(R.color.blue_primary_dark)
                        }

                        //small text themes
                        ThemePair(DEFAULT, SMALL) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_SmallTextTheme)
                        }
                        ThemePair(HIGH_CONTRAST, SMALL) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_HighContrast_SmallTextTheme)
                            statusBarColor = activity.getColor(R.color.high_contrast_primary_dark)
                        }
                        ThemePair(BLUE, SMALL) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_Blue_SmallTextTheme)
                            statusBarColor = activity.getColor(R.color.blue_primary_dark)
                        }

                        //medium text themes
                        ThemePair(DEFAULT, MEDIUM) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_MediumTextTheme)
                        }
                        ThemePair(HIGH_CONTRAST, MEDIUM) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_HighContrast_MediumTextTheme)
                            statusBarColor = activity.getColor(R.color.high_contrast_primary_dark)
                        }
                        ThemePair(BLUE, MEDIUM) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_Blue_MediumTextTheme)
                            statusBarColor = activity.getColor(R.color.blue_primary_dark)
                        }

                        //large text themes
                        ThemePair(DEFAULT, LARGE) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_LargeTextTheme)
                        }
                        ThemePair(HIGH_CONTRAST, LARGE) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_HighContrast_LargeTextTheme)
                            statusBarColor = activity.getColor(R.color.high_contrast_primary_dark)
                        }
                        ThemePair(BLUE, LARGE) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_Blue_LargeTextTheme)
                            statusBarColor = activity.getColor(R.color.blue_primary_dark)
                        }

                        //extra large text themes
                        ThemePair(DEFAULT, EXTRA_LARGE) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_ExtraLargeTextTheme)
                        }
                        ThemePair(HIGH_CONTRAST, EXTRA_LARGE) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_HighContrast_ExtraLargeTextTheme)
                            statusBarColor = activity.getColor(R.color.high_contrast_primary_dark)
                        }
                        ThemePair(BLUE, EXTRA_LARGE) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_Blue_ExtraLargeTextTheme)
                            statusBarColor = activity.getColor(R.color.blue_primary_dark)
                        }

                        //extra extra large text themes
                        ThemePair(DEFAULT, EXTRA_EXTRA_LARGE) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_ExtraExtraLargeTextTheme)
                        }
                        ThemePair(HIGH_CONTRAST, EXTRA_EXTRA_LARGE) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_HighContrast_ExtraExtraLargeTextTheme)
                            statusBarColor = activity.getColor(R.color.high_contrast_primary_dark)
                        }
                        ThemePair(BLUE, EXTRA_EXTRA_LARGE) -> {
                            activity.setTheme(R.style.BaseAppTheme_Mal_Blue_ExtraExtraLargeTextTheme)
                            statusBarColor = activity.getColor(R.color.blue_primary_dark)
                        }
                    }
                }

                //TODO this doesn't seem to be doing its job (must be set in manifest)
                if ((activity is SearchActivity) || (activity is FileExploreActivity)) {

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
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
                activity.window.statusBarColor = statusBarColor
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        applyTheme(this)
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        applyTheme(this)
        super.onResume()
    }
}