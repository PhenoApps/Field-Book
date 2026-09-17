package com.fieldbook.tracker.preferences

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity
import com.fieldbook.tracker.activities.ZplEditorActivity
import com.fieldbook.tracker.printing.LabelPrintService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LabelsPreferencesFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var service: LabelPrintService

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences_labels, rootKey)

        (activity as? PreferencesActivity)?.supportActionBar?.title =
            getString(R.string.preferences_labels_title)

        findPreference<Preference>("pref_key_label_printer")?.let { pref ->
            pref.summary = service.getSavedPrinterName() ?: getString(R.string.preferences_labels_printer_summary)
            pref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
                activity?.let { act ->
                    if (service.checkBluetoothPermissions(act)) {
                        service.choosePrinter(act, object : com.fieldbook.tracker.utilities.BluetoothChooseCallback {
                            override fun onDeviceChosen(deviceName: String) {
                                pref.summary = deviceName
                            }
                        })
                    }
                }
                true
            }
        }

        findPreference<Preference>("pref_key_zpl_editor")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                startActivity(Intent(context, ZplEditorActivity::class.java))
                true
            }
    }
}
