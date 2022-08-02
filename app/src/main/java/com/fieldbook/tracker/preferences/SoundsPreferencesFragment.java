package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.LocaleChoiceActivity;

import org.phenoapps.utils.KeyUtil;
import org.phenoapps.utils.TextToSpeechHelper;

import java.util.Locale;
import java.util.Set;

public class SoundsPreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    PreferenceManager prefMgr;
    Context context;
    private KeyUtil mKeys;

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

        mKeys = new KeyUtil(context);

    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        setupTtsPreference();

        int code = PreferenceManager.getDefaultSharedPreferences(context).getInt(mKeys.getArgTtsLocale(), -1);
        updateTtsSummary(code);
    }

    private void setupTtsPreference() {

        CheckBoxPreference ttsEnabled = findPreference(GeneralKeys.TTS_LANGUAGE_ENABLED);
        if (ttsEnabled != null) {
            Preference ttsLanguage = findPreference(GeneralKeys.TTS_LANGUAGE);
            if (ttsLanguage != null) {
                ttsLanguage.setVisible(ttsEnabled.isChecked());

                ttsLanguage.setOnPreferenceClickListener((v) -> {
                    startActivity(new Intent(getContext(), LocaleChoiceActivity.class));
                    return true;
                });

                ttsLanguage.setOnPreferenceChangeListener((p, v) -> {
                    int code = (int) v;
                    updateTtsSummary(code);
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

    private void updateTtsSummary(int code) {

        Preference ttsLanguage = findPreference(GeneralKeys.TTS_LANGUAGE);

        if (ttsLanguage != null) {

            Set<Locale> locales = TextToSpeechHelper.Companion.getAvailableLocales();
            for (Locale l : locales) {
                if (l.hashCode() == code) {

                    ttsLanguage.setSummary(l.getDisplayName());

                    break;
                }
            }
        }
    }
}