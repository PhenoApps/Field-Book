package com.fieldbook.tracker.preferences

import android.app.AlertDialog
import android.content.SharedPreferences
import android.os.Bundle
import android.util.Log
import androidx.preference.CheckBoxPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity
import com.google.firebase.crashlytics.FirebaseCrashlytics
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject

@AndroidEntryPoint
class SystemPreferencesFragment : PreferenceFragmentCompat(),
    Preference.OnPreferenceChangeListener {

    @Inject
    lateinit var preferences: SharedPreferences

    private val tag: String = SystemPreferencesFragment::class.java.simpleName

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_system, rootKey)

        (activity as? PreferencesActivity)?.supportActionBar?.title =
            getString(R.string.preferences_system_title)

        val importSourceDefaultPref = findPreference<ListPreference>("IMPORT_SOURCE_DEFAULT")
        val exportSourceDefaultPref = findPreference<ListPreference>("EXPORT_SOURCE_DEFAULT")


        importSourceDefaultPref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            validateBrapiEnabledBeforeSetting(newValue.toString())
        }
        exportSourceDefaultPref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
            validateBrapiEnabledBeforeSetting(newValue.toString())
        }

        val resetPref = findPreference<Preference>(GeneralKeys.RESET_PREFERENCES)
        resetPref?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            AlertDialog.Builder(context, R.style.AppAlertDialog)
                .setTitle(R.string.reset_preferences_title)
                .setMessage(R.string.reset_preferences_message)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    preferences.edit().clear().apply()
                    activity?.finish()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any): Boolean {
        return true
    }

    override fun onResume() {
        super.onResume()
        setupCrashlyticsPreference()
    }

    private fun validateBrapiEnabledBeforeSetting(newValue: String): Boolean {
        if ("brapi" == newValue && !preferences.getBoolean(PreferenceKeys.BRAPI_ENABLED, false)) {
            showBrapiDisabledAlertDialog()
            return false
        }
        return true
    }

    private fun showBrapiDisabledAlertDialog() {
        AlertDialog.Builder(context, R.style.AppAlertDialog)
            .setTitle(R.string.brapi_disabled_alert_title)
            .setMessage(R.string.brapi_disabled_alert_message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun refreshIdSummary(refresh: Preference): String {
        val newId = UUID.randomUUID().toString()

        preferences.edit().putString(GeneralKeys.CRASHLYTICS_ID, newId).apply()

        refresh.summary = newId

        val instance = FirebaseCrashlytics.getInstance()
        instance.setUserId(newId)
        instance.setCustomKey(GeneralKeys.CRASHLYTICS_KEY_USER_TOKEN, newId)

        return newId
    }

    private fun setupCrashlyticsPreference() {
        try {
            val enablePref = findPreference<CheckBoxPreference>(GeneralKeys.CRASHLYTICS_ID_ENABLED)
            val refreshPref = findPreference<Preference>(GeneralKeys.CRASHLYTICS_ID_REFRESH)

            enablePref?.let { enablePreference ->
                refreshPref?.let { refreshPreference ->
                    refreshPreference.isVisible = enablePreference.isChecked
                    refreshPreference.summary = preferences.getString(GeneralKeys.CRASHLYTICS_ID, null)

                    enablePreference.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                        val enabled = newValue as Boolean
                        refreshPreference.isVisible = enabled
                        if (enabled) {
                            if (preferences.getString(GeneralKeys.CRASHLYTICS_ID, null) == null) {
                                refreshPreference.summary = refreshIdSummary(refreshPreference)
                            }
                        } else {
                            FirebaseCrashlytics.getInstance().apply {
                                setUserId("")
                                setCustomKey(GeneralKeys.CRASHLYTICS_KEY_USER_TOKEN, "")
                            }
                        }
                        true
                    }

                    refreshPreference.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                        if (enablePreference.isChecked) {
                            refreshPreference.summary = refreshIdSummary(refreshPreference)
                        }
                        true
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Crashlytics setup failed.", e)
        }
    }

}