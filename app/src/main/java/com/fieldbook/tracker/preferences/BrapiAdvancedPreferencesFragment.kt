package com.fieldbook.tracker.preferences

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Sub-screen fragment for BrAPI advanced settings (page size, chunk size, timeout, etc.).
 * Navigated to from the main BrAPI preferences screen via the "Advanced Settings" preference.
 */
@AndroidEntryPoint
class BrapiAdvancedPreferencesFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_brapi_advanced, rootKey)
    }

    override fun onResume() {
        super.onResume()
        val act = activity
        if (act is PreferencesActivity) {
            act.supportActionBar?.title = getString(R.string.brapi_advanced_settings)
        }
    }
}
