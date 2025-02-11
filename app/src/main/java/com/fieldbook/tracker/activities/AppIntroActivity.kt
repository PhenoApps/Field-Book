package com.fieldbook.tracker.activities

import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.OptionalSetupAdapter
import com.fieldbook.tracker.fragments.GallerySlideFragment
import com.fieldbook.tracker.fragments.OptionalSetupPolicyFragment
import com.fieldbook.tracker.fragments.RequiredSetupPolicyFragment
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.ManufacturerUtil
import com.fieldbook.tracker.utilities.SharedPreferenceUtils
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment.Companion.createInstance
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class AppIntroActivity : AppIntro() {

    @Inject
    lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // field book info 1
        addSlide(
            createInstance(
                getString(R.string.app_intro_intro_title_slide1),
                getString(R.string.app_intro_intro_summary_slide1),
                R.drawable.field_book_intro_icon,
                R.color.main_primary_transparent,
                R.color.main_color_text_dark,
                R.color.main_color_text_dark,
            )
        )

        // gallery slide
        addSlide(GallerySlideFragment.newInstance())

        // required setup slide
        addSlide(
            RequiredSetupPolicyFragment.newInstance(
                getString(R.string.app_intro_required_setup_title),
                getString(R.string.app_intro_required_setup_summary),
                R.color.main_primary_transparent
            )
        )

        // optional setup slide
        addSlide(
            OptionalSetupPolicyFragment.newInstance(
                optionalSetupModelArrayList(),
                getString(R.string.app_intro_required_optional_title),
                getString(R.string.app_intro_required_optional_summary)
            )
        )

        isSkipButtonEnabled = false

        setNextArrowColor(ContextCompat.getColor(this, R.color.main_primary_dark))
        setIndicatorColor(
            ContextCompat.getColor(this, R.color.main_primary_dark),
            ContextCompat.getColor(this, R.color.main_primary_transparent)
        )
        setColorDoneText(Color.BLACK)
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        setResult(RESULT_OK)
        finish()
    }

    private fun optionalSetupModelArrayList(): ArrayList<OptionalSetupAdapter.OptionalSetupModel> {
        val optionalSetupList = arrayListOf(
            OptionalSetupAdapter.OptionalSetupModel(
                applicationContext.getString(R.string.app_intro_load_sample_data_title),
                applicationContext.getString(R.string.app_intro_load_sample_data_summary),
                {
                    prefs.edit()?.putBoolean(GeneralKeys.LOAD_SAMPLE_DATA, true)?.apply()
                }, {
                    prefs.edit()?.putBoolean(GeneralKeys.LOAD_SAMPLE_DATA, false)?.apply()
                }),
            OptionalSetupAdapter.OptionalSetupModel(
                applicationContext.getString(R.string.app_intro_tutorial_title),
                applicationContext.getString(R.string.app_intro_tutorial_summary),
                {
                    prefs.edit()?.putBoolean(GeneralKeys.TIPS, true)?.apply()
                }, {
                    prefs.edit()?.putBoolean(GeneralKeys.TIPS, false)?.apply()
                })
        )

        // add high contrast setting for boox devices
        if (ManufacturerUtil.isEInk()) {
            if (ManufacturerUtil.isOnyx()) {
                ManufacturerUtil.transferHighContrastIcon(resources)
            }
            if (!SharedPreferenceUtils.isHighContrastTheme(prefs)) {
                optionalSetupList.add(OptionalSetupAdapter.OptionalSetupModel(
                    getString(R.string.app_intro_high_contrast_title),
                    getString(R.string.app_intro_high_contrast_summary),
                    {
                        prefs.edit().putBoolean(GeneralKeys.HIGH_CONTRAST_THEME_ENABLED, true).apply()
                    }, {
                        prefs.edit().putBoolean(GeneralKeys.HIGH_CONTRAST_THEME_ENABLED, false).apply()
                    },
                    true
                ))
            }
        }

        return optionalSetupList
    }
}