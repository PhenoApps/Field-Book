package com.fieldbook.tracker.preferences

import android.app.AlertDialog
import android.os.Bundle
import androidx.fragment.app.DialogFragment
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity
import com.fieldbook.tracker.dialogs.UploadFileDialog


class BetaPreferencesFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        setPreferencesFromResource(R.xml.preferences_beta, rootKey)

        (this.activity as PreferencesActivity?)?.supportActionBar?.title =
            getString(R.string.preferences_beta_title)

        val pref = findPreference<CheckBoxPreference>(GeneralKeys.REPEATED_VALUES_PREFERENCE_KEY)
        pref?.setOnPreferenceChangeListener { _, newValue ->

            if (newValue == false) {

                if (isAdded) {
                    AlertDialog.Builder(context)
                        .setTitle(getString(R.string.pref_beta_repeated_values_disabled_title))
                        .setMessage(getString(R.string.pref_beta_repeated_values_disabled_message))
                        .setPositiveButton(android.R.string.ok) { dialog, _ -> dialog.dismiss() }
                        .show()
                }
            }

            true
        }

        val uploadPref = findPreference<Preference>("pref_beta_upload_files")
        uploadPref?.setOnPreferenceClickListener {
            val dialogFragment: DialogFragment = UploadFileDialog()
            dialogFragment.show(childFragmentManager, "uploadFile")
            true
        }
    }

}