package com.fieldbook.tracker.preferences

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity
import com.fieldbook.tracker.database.DataHelper
import com.fieldbook.tracker.utilities.NearbyShareUtil
import com.fieldbook.tracker.utilities.Utils
import com.fieldbook.tracker.utilities.ZipUtil
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.io.ObjectInputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import javax.inject.Inject

@AndroidEntryPoint
class PreferencesPreferencesFragment : PreferenceFragmentCompat(), Preference.OnPreferenceChangeListener,
    NearbyShareUtil.FileCallback,
    NearbyShareUtil.PermissionCallback {

    @Inject
    lateinit var preferences: SharedPreferences

    @Inject
    lateinit var database: DataHelper

    @Inject
    lateinit var nearbyShareUtil: NearbyShareUtil

    private val scope = CoroutineScope(Dispatchers.Main)

    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { perms ->
        if (perms.values.all { it }) {
            nearbyShareUtil.handlePermissionResult(
                NearbyShareUtil.REQUEST_PERMISSIONS_CODE,
                IntArray(perms.size) { PackageManager.PERMISSION_GRANTED }
            )
        } else {
            Utils.makeToast(context, getString(R.string.nearby_share_permissions_required))
        }
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_preferences, rootKey)

        (requireActivity() as PreferencesActivity).supportActionBar?.title = getString(R.string.preferences_preferences_title)

        val preferencesExport = findPreference<Preference>("pref_preferences_export")
        val preferencesImport = findPreference<Preference>("pref_preferences_import")

        preferencesExport?.setOnPreferenceClickListener {
            scope.launch {
                nearbyShareUtil.startSharing(
                    this@PreferencesPreferencesFragment,
                    this@PreferencesPreferencesFragment
                )
            }
            return@setOnPreferenceClickListener true
        }

        preferencesImport?.setOnPreferenceClickListener {
            nearbyShareUtil.startReceiving(this, this)
            return@setOnPreferenceClickListener true
        }
    }

    override fun onPreferenceChange(preference: Preference, newValue: Any?): Boolean {
        return true
    }

    override fun prepareFileForTransfer(): DocumentFile {
        return runBlocking {
            withContext(Dispatchers.IO) {
                val timeStamp = SimpleDateFormat("yyyy-MM-dd-hh-mm-ss", Locale.getDefault())
                val filename = "preferences_${timeStamp.format(Calendar.getInstance().time)}"

                database.exportPreferences(requireContext(), filename)
            }
        }
    }

    override fun onFileReceived(receivedFile: DocumentFile) {
        try {
            context?.let {
                it.contentResolver.openInputStream(receivedFile.uri)?.use { inputStream ->
                    ObjectInputStream(inputStream).use { objectStream ->
                        val prefMap = objectStream.readObject() as Map<*, *>
                        ZipUtil.updatePreferences(it, prefMap)
                        Utils.makeToast(context, "Preferences imported successfully")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("PreferencesFragment", "Failed to import preferences", e)
            Utils.makeToast(context, getString(R.string.nearby_share_failed_preferences_import))
        }
    }

    override fun getSaveFileDirectory(): Int {
        return R.string.dir_preferences
    }

    override fun onPermissionRequest(permissions: Array<String>, requestCode: Int) {
        permissionLauncher.launch(permissions)
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        nearbyShareUtil.stopNearbyShare()
    }
}