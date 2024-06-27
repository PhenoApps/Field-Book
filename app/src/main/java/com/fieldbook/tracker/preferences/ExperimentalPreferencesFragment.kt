package com.fieldbook.tracker.preferences

import com.fieldbook.tracker.fragments.ImportDBFragment
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.AppIntroActivity
import com.fieldbook.tracker.activities.PreferencesActivity

class ExperimentalPreferencesFragment : PreferenceFragmentCompat() {

    companion object {
        private const val REQUEST_CODE_APP_INTRO = 1
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        setPreferencesFromResource(R.xml.preferences_experimental, rootKey)

        (this.activity as PreferencesActivity?)?.supportActionBar?.title =
            getString(R.string.preferences_experimental_title)

        hideEmptyPreferenceCategories()
        val pref = findPreference<CheckBoxPreference>(GeneralKeys.REPEATED_VALUES_PREFERENCE_KEY)
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

        val barcode = findPreference<CheckBoxPreference>(GeneralKeys.MLKIT_PREFERENCE_KEY)
        barcode?.setOnPreferenceChangeListener { _, newValue ->
            true
        }

        val appIntro = findPreference<Preference>("launch_app_intro")
        appIntro?.setOnPreferenceClickListener {
            val intent = Intent(activity, AppIntroActivity::class.java)
            startActivityForResult(intent, REQUEST_CODE_APP_INTRO)
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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_APP_INTRO) {

            val prefs = (this.activity as PreferencesActivity).let { PreferenceManager.getDefaultSharedPreferences(it) }

            val loadSampleData = prefs?.getBoolean(GeneralKeys.LOAD_SAMPLE_DATA, false)

            if (context != null && loadSampleData == true){
                val importDBFragment = ImportDBFragment()

                childFragmentManager.beginTransaction()
                    .add(importDBFragment, "com.fieldbook.tracker.fragments.ImportDBFragment")
                    .addToBackStack(null)
                    .commit()

            }


            val showTutorial = prefs?.getBoolean(GeneralKeys.SHOW_TUTORIAL, false)
        }
    }

}