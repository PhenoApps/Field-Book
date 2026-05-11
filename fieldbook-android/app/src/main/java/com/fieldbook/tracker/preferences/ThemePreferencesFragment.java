package com.fieldbook.tracker.preferences;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;

import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.PreferencesActivity;
import com.fieldbook.tracker.activities.ThemedActivity;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ThemePreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    @Inject
    SharedPreferences preferences;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        setPreferencesFromResource(R.xml.preferences_theme, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.preferences_appearance_theme_title));

        Preference restoreTheme = findPreference("RESTORE_DEFAULT_THEME");

        if (restoreTheme != null) {

            restoreTheme.setOnPreferenceClickListener(preference -> {

                if (getContext() != null) {

                    TypedValue value = new TypedValue();
                    getContext().getTheme().resolveAttribute(R.attr.fb_value_saved_color, value, true);

                    preferences.edit()
                            .putInt(PreferenceKeys.SAVED_DATA_COLOR, value.data)
                            .putString(PreferenceKeys.TEXT_THEME, String.valueOf(ThemedActivity.MEDIUM))
                            .putString(PreferenceKeys.THEME, String.valueOf(ThemedActivity.DEFAULT))
                            .apply();

                    refreshTheme();
                }

                return true;
            });
        }


        /*
         * When theme is updated, save it to preferences.
         */
        Preference textPref = findPreference(PreferenceKeys.TEXT_THEME);
        if (textPref != null) {
            textPref.setOnPreferenceChangeListener((pref, value) -> {

                refreshTheme();

                return true;

            });
        }

        Preference themePref = findPreference(PreferenceKeys.THEME);
        if (themePref != null) {
            themePref.setOnPreferenceChangeListener((pref, value) -> {

                refreshTheme();

                return true;

            });
        }
    }

    private void refreshTheme() {

        getParentFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        preferences.edit()
                .putBoolean(GeneralKeys.THEME_FLAG, true)
                .apply();

        //force recreate the activity so the theme applies
        if (getActivity() != null) getActivity().recreate();
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }
}