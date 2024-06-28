package com.fieldbook.tracker.activities

import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.RadioButtonAdapter
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.fragments.RadioButtonSlidePolicyFragment
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.Constants
import com.fieldbook.tracker.utilities.Utils
import com.github.appintro.AppIntro
import com.github.appintro.AppIntroFragment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.phenoapps.utils.BaseDocumentTreeUtil.Companion.getFile

class AppIntroActivity : AppIntro() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val context = applicationContext

        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        // Make sure you don't call setContentView!

        // Call addSlide passing your Fragments.
        // You can use AppIntroFragment to use a pre-built fragment

        val loadSampleDataRadioButtonItems = arrayListOf(
            RadioButtonAdapter.RadioButtonModel(
                context.getString(R.string.app_intro_load_sample_data_positive),
                {
                    prefs.edit().putBoolean(GeneralKeys.LOAD_SAMPLE_DATA, true).apply()
                }),
            RadioButtonAdapter.RadioButtonModel(
                context.getString(R.string.app_intro_load_sample_data_negative), {
                    prefs.edit().putBoolean(GeneralKeys.LOAD_SAMPLE_DATA, false).apply()
                }),
        )

        val tutorialRadioButtonItems = arrayListOf(
            RadioButtonAdapter.RadioButtonModel(
                context.getString(R.string.app_intro_tutorial_positive),
                {
                    prefs.edit().putBoolean(GeneralKeys.TIPS, true).apply()
                    prefs.edit().putBoolean(GeneralKeys.TIPS_CONFIGURED, true).apply()
                }),
            RadioButtonAdapter.RadioButtonModel(
                context.getString(R.string.app_intro_tutorial_negative), {
                    prefs.edit().putBoolean(GeneralKeys.TIPS, false).apply()
                    prefs.edit().putBoolean(GeneralKeys.TIPS_CONFIGURED, true).apply()
                }),
        )


        // field book info 1
        addSlide(
            AppIntroFragment.createInstance(
                context.getString(R.string.app_intro_intro_title_slide1),
                context.getString(R.string.app_intro_intro_summary_slide1),
                imageDrawable = R.drawable.other_ic_field_book,
                backgroundColorRes = R.color.main_primary
            )
        )

        // field book info 2
        addSlide(
            AppIntroFragment.createInstance(
                context.getString(R.string.app_intro_intro_title_slide2),
                context.getString(R.string.app_intro_intro_summary_slide2),
                imageDrawable = R.drawable.other_ic_field_book,
                backgroundColorRes = R.color.main_primary
            )
        )

        // permissions
        addSlide(
            AppIntroFragment.createInstance(
                context.getString(R.string.app_intro_request_permissions_title),
                if (isPermissionsGranted()) context.getString(R.string.app_intro_permissions_granted_summary) else context.getString(
                    R.string.app_intro_request_permissions_summary
                ),
                imageDrawable = R.drawable.other_ic_field_book,
                backgroundColorRes = R.color.main_primary
            )
        )

//        addSlide(RadioButtonSlidePolicyFragment)

        // load sample data
        addSlide(
            RadioButtonSlidePolicyFragment.newInstance(
                loadSampleDataRadioButtonItems,
                context.getString(R.string.app_intro_load_sample_data_title),
                context.getString(R.string.app_intro_load_sample_data_summary)
            )
        )

        // tutorial
        addSlide(
            RadioButtonSlidePolicyFragment.newInstance(
                tutorialRadioButtonItems,
                context.getString(R.string.app_intro_tutorial_title),
                context.getString(R.string.app_intro_tutorial_summary)
            )
        )

        // Here we ask for camera permission on slide 2
        if (!isPermissionsGranted())
            askForPermissions(Constants.permissionsTemp, 3, true)
    }

    private fun isPermissionsGranted(): Boolean {
        val permissions = Constants.permissionsTemp
        for (permission in permissions) {
            val res: Int = applicationContext.checkCallingOrSelfPermission(permission)
            if (res != PackageManager.PERMISSION_GRANTED)
                return false
        }
        return true
    }


    override fun onSkipPressed(currentFragment: Fragment?) {
        super.onSkipPressed(currentFragment)
        // Decide what to do when the user clicks on "Skip"
        finish()
    }

    override fun onDonePressed(currentFragment: Fragment?) {
        super.onDonePressed(currentFragment)
        // Decide what to do when the user clicks on "Done"
        finish()
    }
}