package com.fieldbook.tracker.preferences

import android.content.Intent
import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.LabelTemplatesActivity
import com.fieldbook.tracker.activities.PreferencesActivity
import com.fieldbook.tracker.printing.LabelPrintService
import com.fieldbook.tracker.zpl.TemplateRepository
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class LabelsPreferencesFragment : PreferenceFragmentCompat() {

    @Inject
    lateinit var service: LabelPrintService

    @Inject
    lateinit var templateRepository: TemplateRepository

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

        findPreference<Preference>(KEY_LABEL_TEMPLATES)?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                startActivity(Intent(context, LabelTemplatesActivity::class.java))
                true
            }
    }

    override fun onResume() {
        super.onResume()
        // the default can change in the templates screen
        updateTemplatesSummary()
    }

    private fun updateTemplatesSummary() {
        findPreference<Preference>(KEY_LABEL_TEMPLATES)?.let { pref ->
            templateRepository.ensureDefaultTemplate()
            val defaultName = templateRepository.getDefaultTemplateId()
                ?.let { templateRepository.getTemplateName(it) }
            pref.summary = if (defaultName != null) {
                getString(R.string.preferences_labels_templates_summary, defaultName)
            } else {
                getString(R.string.preferences_labels_templates_description)
            }
        }
    }

    companion object {
        private const val KEY_LABEL_TEMPLATES = "pref_key_label_templates"
    }
}
