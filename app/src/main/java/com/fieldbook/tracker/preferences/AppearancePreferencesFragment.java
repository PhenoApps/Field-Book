package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.os.Bundle;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.h6ah4i.android.preference.NumberPickerPreferenceCompat;
import com.h6ah4i.android.preference.NumberPickerPreferenceDialogFragmentCompat;

public class AppearancePreferencesFragment extends PreferenceFragmentCompat {

    private static final String DIALOG_FRAGMENT_TAG = "androidx.preference.PreferenceFragment.DIALOG";
    PreferenceManager prefMgr;
    Context context;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName(GeneralKeys.SHARED_PREF_FILE_NAME);

        setPreferencesFromResource(R.xml.preferences_appearance, rootKey);
        PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences_appearance, true);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.preferences_appearance_title));
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Occurs before the on create function. We get the context this way.
        AppearancePreferencesFragment.this.context = context;
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        // check if dialog is already showing
        if (getFragmentManager().findFragmentByTag(DIALOG_FRAGMENT_TAG) != null) {
            return;
        }

        final DialogFragment f;

        if (preference instanceof NumberPickerPreferenceCompat) {
            f = NumberPickerPreferenceDialogFragmentCompat.newInstance(preference.getKey());
        } else {
            f = null;
        }

        if (f != null) {
            f.setTargetFragment(this, 0);
            f.show(getFragmentManager(), DIALOG_FRAGMENT_TAG);
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.preferences_appearance_title));

        updateLanguageSummary();

        ActionBar ab = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (ab != null) {
            ab.setTitle(R.string.preferences_appearance_title);
        }
    }

    private void updateLanguageSummary() {
        Preference langPref = findPreference(GeneralKeys.LANGUAGE_PREF);
        if (langPref != null) {
            String summary = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(GeneralKeys.LANGUAGE_LOCALE_SUMMARY, getString(R.string.preference_language_default));
            langPref.setSummary(summary);
        }
    }
}