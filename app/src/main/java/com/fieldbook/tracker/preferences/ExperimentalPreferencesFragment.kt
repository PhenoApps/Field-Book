package com.fieldbook.tracker.preferences

import android.app.AlertDialog
import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity
import com.fieldbook.tracker.utilities.GeoNavHelper
import com.fieldbook.tracker.utilities.SampleDataGenerator
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class ExperimentalPreferencesFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var sampleGenerator: SampleDataGenerator

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

        val generateSampleData = findPreference<Preference>(PreferenceKeys.GENERATE_SAMPLE_DATA)
        generateSampleData?.setOnPreferenceClickListener {
            context?.let { ctx ->
                AlertDialog.Builder(ctx, R.style.AppAlertDialog)
                    .setTitle(getString(R.string.pref_experimental_generate_sample_data_confirm_title))
                    .setMessage(getString(R.string.pref_experimental_generate_sample_data_confirm_message))
                    .setPositiveButton(android.R.string.ok) { dialog, _ ->
                        dialog.dismiss()
                        generateSampleField(ctx)
                    }
                    .setNegativeButton(android.R.string.cancel) { dialog, _ -> dialog.dismiss() }
                    .show()
            }
            true
        }
    }

    /**
     * Runs the sample generator off the main thread behind a progress dialog that can't be
     * dismissed, so the field can't be opened, or the button pressed again, while it is written.
     *
     * The generator keeps going if this screen is left: it is not cancellable part way, and its
     * own guard turns away a second run until it finishes.
     */
    private fun generateSampleField(ctx: Context) {

        val progress = AlertDialog.Builder(ctx, R.style.AppAlertDialog)
            .setTitle(getString(R.string.pref_experimental_generate_sample_data_title))
            .setMessage(getString(R.string.pref_experimental_generate_sample_data_running))
            .setCancelable(false)
            .show()

        lifecycleScope.launch {
            try {
                val studyId = withContext(Dispatchers.IO) {
                    sampleGenerator.generateSampleField()
                }
                val messageRes = when {
                    studyId == SampleDataGenerator.ALREADY_RUNNING ->
                        R.string.pref_experimental_generate_sample_data_running
                    studyId >= 0 -> R.string.pref_experimental_generate_sample_data_success
                    else -> R.string.pref_experimental_generate_sample_data_failure
                }
                Toast.makeText(ctx, messageRes, Toast.LENGTH_LONG).show()
            } finally {
                // The activity may already be gone, taking the dialog's window with it, and
                // dismissing then throws.
                runCatching { if (progress.isShowing) progress.dismiss() }
            }
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