package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.CollectActivity;
import com.fieldbook.tracker.activities.PreferencesActivity;
import com.h6ah4i.android.preference.NumberPickerPreferenceCompat;
import com.h6ah4i.android.preference.NumberPickerPreferenceDialogFragmentCompat;

public class AppearancePreferencesFragment extends PreferenceFragmentCompat {

    private static final String DIALOG_FRAGMENT_TAG = "androidx.preference.PreferenceFragment.DIALOG";
    Context context;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean flag = prefs.getBoolean(GeneralKeys.THEME_FLAG, false);
        if (flag) {
            getParentFragmentManager().beginTransaction()
                    .replace(R.id.prefs_container, new ThemePreferencesFragment())
                    .addToBackStack("AppearanceFrag").commit();
            prefs.edit().putBoolean(GeneralKeys.THEME_FLAG, false).apply();
        }
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        setPreferencesFromResource(R.xml.preferences_appearance, rootKey);
        PreferenceManager.setDefaultValues(getActivity(), R.xml.preferences_appearance, true);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.preferences_appearance_title));

        Bundle arguments = getArguments();
        if (arguments == null || !arguments.getBoolean(GeneralKeys.INFOBAR_UPDATE, false)) {
            return;
        }

        Preference infobarPref = findPreference("INFOBAR_NUMBER");
        if (infobarPref != null) {
            onDisplayPreferenceDialog(infobarPref);

            infobarPref.setOnPreferenceChangeListener((preference, newValue) -> {

                if (getActivity().getIntent().hasExtra(GeneralKeys.INFOBAR_UPDATE)) {

                    Intent retIntent = new Intent(context, CollectActivity.class);

                    retIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                    getActivity().startActivity(retIntent);

                }
                return true;
            });

        }
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

        updateLanguageSummary();

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.preferences_appearance_title));

    }

    private void updateLanguageSummary() {
        Preference langPref = findPreference(PreferenceKeys.LANGUAGE_PREF);
        if (langPref != null) {
            String summary = PreferenceManager.getDefaultSharedPreferences(context)
                    .getString(PreferenceKeys.LANGUAGE_LOCALE_SUMMARY, getString(R.string.preference_language_default));
            langPref.setSummary(summary);
        }
    }
}