package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.os.Bundle;

import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;

public class SoundsPreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    PreferenceManager prefMgr;
    Context context;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName(GeneralKeys.SHARED_PREF_FILE_NAME);

        setPreferencesFromResource(R.xml.preferences_sounds, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.preferences_sounds));
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Occurs before the on create function. We get the context this way.
        SoundsPreferencesFragment.this.context = context;

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        setupTtsPreference();

        updateTtsSummary();
    }

    private void setupTtsPreference() {

        CheckBoxPreference ttsEnabled = findPreference(GeneralKeys.TTS_LANGUAGE_ENABLED);
        if (ttsEnabled != null) {
            Preference ttsLanguage = findPreference(GeneralKeys.TTS_LANGUAGE);
            if (ttsLanguage != null) {
                ttsLanguage.setVisible(ttsEnabled.isChecked());

                ttsLanguage.setOnPreferenceChangeListener((p, v) -> {
                    updateTtsSummary();
                    return true;
                });
            }

            ttsEnabled.setOnPreferenceChangeListener((preference, newValue) -> {

                if (ttsLanguage != null) {
                    ttsLanguage.setVisible((Boolean) newValue);
                }
                return true;
            });
        }
    }

    private void updateTtsSummary() {

        Preference ttsLanguage = findPreference(GeneralKeys.TTS_LANGUAGE);

        String summary = PreferenceManager.getDefaultSharedPreferences(context)
                .getString(GeneralKeys.TTS_LANGUAGE_SUMMARY, "");

        if (ttsLanguage != null) {

            ttsLanguage.setSummary(summary);

        }
    }
}