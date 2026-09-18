package com.fieldbook.tracker.preferences

import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.fieldbook.tracker.R
import com.fieldbook.tracker.activities.PreferencesActivity
import com.fieldbook.tracker.activities.ZplEditorActivity
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

    private val zplDefaultTemplateLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data ?: return@registerForActivityResult
            val name = data.getStringExtra(ZplEditorActivity.RESULT_TEMPLATE_NAME)
            if (!name.isNullOrBlank()) {
                val id = templateRepository.getAllTemplates().entries.find { it.value == name }?.key
                templateRepository.setDefaultTemplateId(id)
                updateDefaultTemplateSummary()
            }
        }
    }

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

        updateDefaultTemplateSummary()
        findPreference<Preference>("pref_key_default_zpl_template")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val defaultId = templateRepository.getDefaultTemplateId()
                val initialZpl = if (defaultId != null) templateRepository.getTemplate(defaultId) ?: "" else ""
                val templateName = if (defaultId != null) templateRepository.getTemplateName(defaultId) else null
                
                val intent = Intent(context, ZplEditorActivity::class.java).apply {
                    putExtra(ZplEditorActivity.EXTRA_INITIAL_ZPL, initialZpl)
                    putExtra(ZplEditorActivity.EXTRA_TEMPLATE_NAME, templateName)
                }
                zplDefaultTemplateLauncher.launch(intent)
                true
            }
    }

    private fun updateDefaultTemplateSummary() {
        findPreference<Preference>("pref_key_default_zpl_template")?.let { pref ->
            val defaultId = templateRepository.getDefaultTemplateId()
            val name = if (defaultId != null) templateRepository.getTemplateName(defaultId) else null
            pref.summary = name ?: getString(R.string.preferences_labels_default_template_summary)
        }
    }
}
