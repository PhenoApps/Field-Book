package com.fieldbook.tracker.preferences

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class SystemPreferencesFragment : PreferenceFragmentCompat(),
    Preference.OnPreferenceChangeListener {

    @Inject
    lateinit var preferences: SharedPreferences

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_system, rootKey)

        (this.activity as PreferencesActivity?)!!.supportActionBar!!.title =
            getString(R.string.preferences_system_title)

        val importSourceDefaultPref = findPreference<ListPreference>("IMPORT_SOURCE_DEFAULT")
        val exportSourceDefaultPref = findPreference<ListPreference>("EXPORT_SOURCE_DEFAULT")


        if (importSourceDefaultPref != null) {
            importSourceDefaultPref.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any ->
                    validateBrapiEnabledBeforeSetting(
                        newValue.toString()
                    )
                }
        }

        if (exportSourceDefaultPref != null) {
            exportSourceDefaultPref.onPreferenceChangeListener =
                Preference.OnPreferenceChangeListener { preference: Preference?, newValue: Any ->
                    validateBrapiEnabledBeforeSetting(
                        newValue.toString()
                    )
                }
        }

    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        return true
    }

    override fun onResume() {
        super.onResume()
    }

    private fun validateBrapiEnabledBeforeSetting(newValue: String): Boolean {
        if ("brapi" == newValue && !preferences!!.getBoolean(GeneralKeys.BRAPI_ENABLED, false)) {
            showBrapiDisabledAlertDialog()
            return false
        }
        return true
    }

    private fun showBrapiDisabledAlertDialog() {
        AlertDialog.Builder(getContext(), R.style.AppAlertDialog)
            .setTitle(R.string.brapi_disabled_alert_title)
            .setMessage(R.string.brapi_disabled_alert_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

}