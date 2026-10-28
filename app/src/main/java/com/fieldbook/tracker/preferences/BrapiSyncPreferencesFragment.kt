package com.fieldbook.tracker.preferences

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.preference.CheckBoxPreference
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity
import dagger.hilt.android.AndroidEntryPoint

/**
 * Sub-screen fragment for automatic BrAPI sync (on field open, background, reconnect notification).
 * Navigated to from the main BrAPI preferences screen via the "Sync" preference.
 */
@AndroidEntryPoint
class BrapiSyncPreferencesFragment : PreferenceFragmentCompat() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        // Without the permission the reminder could never be shown, so leave the setting off.
        if (!granted) {
            findPreference<CheckBoxPreference>(PreferenceKeys.BRAPI_SYNC_RECONNECT_NOTIFICATION)?.isChecked = false
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_brapi_sync, rootKey)

        findPreference<CheckBoxPreference>(PreferenceKeys.BRAPI_SYNC_RECONNECT_NOTIFICATION)
            ?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, newValue ->
                if (newValue == true) requestNotificationPermission()
                true
            }
    }

    override fun onResume() {
        super.onResume()
        val act = activity
        if (act is PreferencesActivity) {
            act.supportActionBar?.title = getString(R.string.preferences_brapi_sync_title)
        }
    }

    private fun requestNotificationPermission() {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
