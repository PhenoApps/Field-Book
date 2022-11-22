package com.fieldbook.tracker.preferences

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.fieldbook.tracker.R

class BetaPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {

        setPreferencesFromResource(R.xml.preferences_beta, rootKey)

        (this.activity as PreferencesActivity?)?.supportActionBar?.title =
            getString(R.string.preferences_beta_title)
    }
}