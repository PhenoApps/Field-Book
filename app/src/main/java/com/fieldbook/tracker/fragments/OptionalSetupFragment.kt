package com.fieldbook.tracker.fragments

import android.content.SharedPreferences
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.preferences.PreferenceKeys
import com.fieldbook.tracker.utilities.ManufacturerUtil
import com.fieldbook.tracker.utilities.SharedPreferenceUtils
import com.fieldbook.tracker.views.OptionalSetupItem

class OptionalSetupFragment : Fragment(){
    private var slideTitle: String? = null
    private var slideSummary: String? = null
    private var slideBackgroundColor: Int? = null

    private var loadSampleData: OptionalSetupItem? = null
    private var enableTutorial: OptionalSetupItem? = null
    private var highContrastTheme: OptionalSetupItem? = null

    private var prefs: SharedPreferences? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? = inflater.inflate(R.layout.app_intro_optional_setup_slide, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = context?.let { PreferenceManager.getDefaultSharedPreferences(it) }

        val slideTitle = view.findViewById<TextView>(R.id.slide_title)
        val slideSummary = view.findViewById<TextView>(R.id.slide_summary)

        slideTitle?.text = this.slideTitle
        slideSummary?.text = this.slideSummary

        slideBackgroundColor?.let { view.setBackgroundResource(it) }

        loadSampleData = view.findViewById(R.id.load_sample_data)

        enableTutorial = view.findViewById(R.id.enable_tutorial)

        highContrastTheme = view.findViewById(R.id.high_contrast_theme)

        initSetupItems()
    }

    private fun initSetupItems() {
        loadSampleData?.apply {
            setTitle(getString(R.string.app_intro_load_sample_data_title))
            setSummary(getString(R.string.app_intro_load_sample_data_summary))
            setOnClickListener {
                toggleOptionalSetting(this, GeneralKeys.LOAD_SAMPLE_DATA)
            }
            setTitleTextSize(24f)
        }

        enableTutorial?.apply {
            setTitle(getString(R.string.app_intro_tutorial_title))
            setSummary(getString(R.string.app_intro_tutorial_summary))
            setOnClickListener {
                toggleOptionalSetting(this, PreferenceKeys.TIPS)
            }
            setTitleTextSize(24f)
        }

        if (ManufacturerUtil.isEInk()) {
            if (ManufacturerUtil.isOnyx()) {
                ManufacturerUtil.transferHighContrastIcon(resources)
            }
            prefs?.let {
                if (!SharedPreferenceUtils.isHighContrastTheme(it)) {
                    highContrastTheme?.apply {
                        setTitle(getString(R.string.app_intro_high_contrast_title))
                        setSummary(getString(R.string.app_intro_high_contrast_summary))
                        setOnClickListener {
                            toggleOptionalSetting(this, GeneralKeys.HIGH_CONTRAST_THEME_ENABLED)
                        }
                        // initially, set the setting as enabled
                        toggleOptionalSetting(this, GeneralKeys.HIGH_CONTRAST_THEME_ENABLED)
                    }
                } else {
                    highContrastTheme?.visibility = View.GONE
                }
            }
        } else {
            highContrastTheme?.visibility = View.GONE
        }
    }

    private fun toggleOptionalSetting(optionalSetupItemView: OptionalSetupItem, prefKey: String) {
        optionalSetupItemView.let {
            it.setCheckbox(!it.isChecked())

            prefs?.edit()?.putBoolean(prefKey, it.isChecked())?.apply()
        }
    }

    companion object {
        fun newInstance(
            slideTitle: String,
            slideSummary: String,
            slideBackgroundColor: Int
        ): OptionalSetupFragment {
            val fragment = OptionalSetupFragment()
            fragment.slideTitle = slideTitle
            fragment.slideSummary = slideSummary
            fragment.slideBackgroundColor = slideBackgroundColor
            return fragment
        }
    }
}