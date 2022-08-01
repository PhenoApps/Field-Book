package com.fieldbook.tracker.preferences

import android.content.res.Resources
import android.os.Bundle
import androidx.core.os.ConfigurationCompat
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.utilities.AppLanguageUtil

class LanguagePreferenceFragment : PreferenceFragmentCompat(), Preference.OnPreferenceClickListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        setPreferencesFromResource(R.xml.preferences_language, rootKey)

        (this.activity as PreferencesActivity?)?.supportActionBar?.title =
            getString(R.string.preferences_appearance_language)

        for (key in setOf("com.fieldbook.tracker.preference.language.default",
            "am", "ar", "bn", "de", "en", "es",
            "fr", "hi", "it", "ja", "om-ET", "pt-BR",
            "ru", "zh-CN")) {
            preferenceScreen.findPreference<Preference>(key)?.onPreferenceClickListener = this
        }
    }

    /**
     * When preference is clicked, save the language key (in xml it is the region ISO)
     * Also update the app language using app compat.
     */
    override fun onPreferenceClick(preference: Preference): Boolean {

        try {

            var id = preference.key

            with (PreferenceManager.getDefaultSharedPreferences(context)) {

                if (preference.key == "com.fieldbook.tracker.preference.language.default") {

                    id = ConfigurationCompat.getLocales(Resources.getSystem().configuration).get(0)?.language

                }

                edit().putString(GeneralKeys.LANGUAGE_LOCALE_ID, id).apply()

                AppLanguageUtil.refreshAppText(context)
            }

        } catch (e: Exception) {

            e.printStackTrace()

        }

        //need two
        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        return true
    }
}