package com.fieldbook.tracker.preferences

import android.os.Bundle
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity
import org.phenoapps.utils.TextToSpeechHelper

class TtsLanguagePreferenceFragment : PreferenceFragmentCompat(), Preference.OnPreferenceClickListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        setPreferencesFromResource(R.xml.preferences_tts_language, rootKey)

        (this.activity as PreferencesActivity?)?.supportActionBar?.title =
            getString(R.string.preferences_appearance_language)

        for (key in TextToSpeechHelper.availableLocales.map { it.language }) {
            findPreference<Preference>(key)?.onPreferenceClickListener = this
        }
    }

    /**
     * When preference is clicked, save the language key (in xml it is the region ISO)
     * Also update the app language using app compat.
     */
    override fun onPreferenceClick(preference: Preference): Boolean {

        try {

            context?.let { ctx ->

                val id = preference.key

                with (PreferenceManager.getDefaultSharedPreferences(ctx)) {

                    edit().putString(PreferenceKeys.TTS_LANGUAGE, id).apply()
                    edit().putString(PreferenceKeys.TTS_LANGUAGE_SUMMARY, preference.title.toString()).apply()

                }
            }


        } catch (e: Exception) {

            e.printStackTrace()

        }

        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)

        return true
    }
}