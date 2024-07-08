package com.fieldbook.tracker.preferences

import android.app.AlertDialog
import android.content.res.Resources
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.util.Log
import androidx.core.os.ConfigurationCompat
import androidx.core.os.LocaleListCompat
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity
import com.fieldbook.tracker.utilities.AppLanguageUtil
import java.util.Locale

class LanguagePreferenceFragment : PreferenceFragmentCompat(), Preference.OnPreferenceClickListener {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        setPreferencesFromResource(R.xml.preferences_language, rootKey)

        (this.activity as PreferencesActivity?)?.supportActionBar?.title =
            getString(R.string.preferences_appearance_language)

        for (key in setOf("com.fieldbook.tracker.preference.language.default",
            "am-ET", "ar-SA", "bn-BD", "de-DE", "en-US", "es-MX",
            "fr-FR", "hi-IN", "it-IT", "ja-JP", "om-ET", "pt-BR",
            "ru-RU", "sv-SE", "vi-VN", "zh-CN")) {
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
                val currentPrefTag = PreferenceManager.getDefaultSharedPreferences(ctx)
                    .getString(GeneralKeys.LANGUAGE_LOCALE_ID, "en-US")

                var id = preference.key
                var languageSummary = preference.title.toString()
                if (preference.key == "com.fieldbook.tracker.preference.language.default") {
                    //set id to the default language
                    val defaultLocales = LocaleListCompat.getAdjustedDefault()
                    val defaultLocale = defaultLocales[0]
                    id = if (defaultLocales.size() > 1 && defaultLocale?.toLanguageTag() == currentPrefTag) {
                        val secondDefault = defaultLocales[1]
                        languageSummary = secondDefault?.getDisplayLanguage(secondDefault) ?: "English"
                        secondDefault
                    } else {
                        languageSummary = defaultLocale?.getDisplayLanguage(defaultLocale) ?: "English"
                        defaultLocale
                    }?.toLanguageTag() ?: currentPrefTag
                }
                Log.d("LanguagePrefFragment", "Switching language to: $id")
                with (PreferenceManager.getDefaultSharedPreferences(ctx)) {
                    edit().putString(GeneralKeys.LANGUAGE_LOCALE_ID, id).apply()
                    edit().putString(GeneralKeys.LANGUAGE_LOCALE_SUMMARY, languageSummary).apply()
                }

                AlertDialog.Builder(ctx, R.style.AppAlertDialog).apply {
                    setTitle(context.getString(R.string.dialog_warning))
                    setMessage(context.getString(R.string.preference_language_warning))
                    setPositiveButton(context.getString(android.R.string.ok)) { dialog, _ ->
                        AppLanguageUtil.refreshAppText(ctx)
                        dialog.dismiss()
                        parentFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                    }
                    setCancelable(false)
                    show()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e("LanguagePreference", "Error in onPreferenceClick: ${e.message}")
        }

        return true
    }
}