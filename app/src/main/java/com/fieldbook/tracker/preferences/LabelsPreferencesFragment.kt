package com.fieldbook.tracker.preferences

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity
import com.fieldbook.tracker.activities.ZplEditorActivity
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class LabelsPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_labels, rootKey)

        (activity as? PreferencesActivity)?.supportActionBar?.title =
            getString(R.string.preferences_labels_title)

        findPreference<Preference>("pref_key_zpl_editor")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                startActivity(Intent(context, ZplEditorActivity::class.java))
                true
            }
    }
}
