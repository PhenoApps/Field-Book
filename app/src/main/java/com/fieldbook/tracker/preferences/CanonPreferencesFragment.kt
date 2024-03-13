package com.fieldbook.tracker.preferences

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity

class CanonPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        setPreferencesFromResource(R.xml.preferences_canon, rootKey)

        (this.activity as PreferencesActivity?)?.supportActionBar?.title =
            getString(R.string.preferences_canon_title)


        val helpPreference = findPreference<Preference>(GeneralKeys.CANON_HELP)

        helpPreference?.setOnPreferenceClickListener {

            launchCanonManual()

            true
        }
    }

    private fun launchCanonManual() {

        startActivity(Intent(Intent.ACTION_VIEW).also {
            it.data = Uri.parse("https://docs.fieldbook.phenoapps.org/en/latest/")
        })
    }
}