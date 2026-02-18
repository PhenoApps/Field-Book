package com.fieldbook.tracker.preferences

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import com.fieldbook.tracker.utilities.GeoNavHelper

@AndroidEntryPoint
class ExperimentalPreferencesFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var prefs: SharedPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        setPreferencesFromResource(R.xml.preferences_experimental, rootKey)

        (this.activity as PreferencesActivity?)?.supportActionBar?.title =
            getString(R.string.preferences_experimental_title)

        hideEmptyPreferenceCategories()
        val pref = findPreference<CheckBoxPreference>(PreferenceKeys.REPEATED_VALUES_PREFERENCE_KEY)
        pref?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue == false) {

                if (isAdded) {
                    AlertDialog.Builder(context, R.style.AppAlertDialog)
                        .setTitle(getString(R.string.pref_experimental_repeated_values_disabled_title))
                        .setMessage(getString(R.string.pref_experimental_repeated_values_disabled_message))
                        .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            }

            true
        }

        val fieldAudio = findPreference<CheckBoxPreference>(PreferenceKeys.ENABLE_FIELD_AUDIO)
        fieldAudio?.setOnPreferenceChangeListener { _, newValue ->
            context?.let { ctx ->
                if (newValue as? Boolean == true) {
                    val prefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                    prefs.edit()
                        .putBoolean(PreferenceKeys.ENABLE_GEONAV, true)
                        .putString(
                            PreferenceKeys.GEONAV_LOGGING_MODE,
                            GeoNavHelper.GeoNavLoggingMode.LIMITED.value
                        ).apply()
                }
            }
            true
        }
    }

    private fun hideEmptyPreferenceCategories() {
        val preferenceScreen = preferenceScreen
        for (i in 0 until preferenceScreen.preferenceCount) {
            val preference = preferenceScreen.getPreference(i)
            if (preference is PreferenceCategory && preference.preferenceCount == 0) {
                preference.isVisible = false
            }
        }
    }
}