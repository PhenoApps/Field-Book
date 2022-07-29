package com.fieldbook.tracker.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.fieldbook.tracker.R
import com.fieldbook.tracker.adapters.LanguageTextAdapter
import com.fieldbook.tracker.preferences.GeneralKeys
import com.fieldbook.tracker.utilities.AppLanguageUtil

class AppLanguageListFragment : Fragment(R.layout.fragment_app_language_list), LanguageTextAdapter.OnClickListItem {

    /**
     * This activity populates a recycler view with the possible available translations
     * using LanguageTextAdapter
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rv = view.findViewById<RecyclerView>(R.id.frag_language_chooser_rv)

        rv.adapter = LanguageTextAdapter(this, buildLanguages())

    }

    /**
     * Map of locale code to displayed text
     */
    private fun buildLanguages() = mapOf(
        "en" to (R.string.language_en combine R.string.locale_language_en),
        "am" to (R.string.language_am combine R.string.locale_language_am),
        "ar" to (R.string.language_ar combine R.string.locale_language_ar),
        "bn" to (R.string.language_bn combine R.string.locale_language_bn),
        "de" to (R.string.language_de combine R.string.locale_language_de),
        "es" to (R.string.language_es combine R.string.locale_language_es),
        "fr" to (R.string.language_fr combine R.string.locale_language_fr),
        "hi" to (R.string.language_hi combine R.string.locale_language_hi),
        "it" to (R.string.language_it combine R.string.locale_language_it),
        "ja" to (R.string.language_ja combine R.string.locale_language_ja),
        "om-ET" to (R.string.language_om_rET combine R.string.locale_language_om_rET),
        "pt-BR" to (R.string.language_pt_rBR combine R.string.locale_language_pt_rBR),
        "ru" to (R.string.language_ru combine R.string.locale_language_ru),
        "zh-CN" to (R.string.language_zh_rCN combine R.string.locale_language_zh_rCN),
        )

    /**
     * simple function to format string with non-translated / locale translation of a language
     */
    private infix fun Int.combine(locale: Int) = getString(this, getString(locale))

    /**
     * when an item is clicked, save the locale code and its summary
     */
    override fun onItemClicked(obj: Any) {

        (obj as? Pair<*,*>)?.let { pair ->

            (pair.first as? String)?.let { id ->

                (pair.second as? String)?.let { summary ->

                    PreferenceManager.getDefaultSharedPreferences(context).also { prefs ->

                        with (prefs.edit()) {
                            putString(GeneralKeys.LANGUAGE_LOCALE_SUMMARY, summary)
                            putString(GeneralKeys.LANGUAGE_LOCALE_ID, id)
                            apply()
                        }

                        AppLanguageUtil.refreshAppText(context)

                        parentFragmentManager.popBackStack()
                    }
                }
            }
        }
    }
}