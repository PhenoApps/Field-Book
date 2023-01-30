package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.os.Bundle;
import android.util.TypedValue;

import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.PreferencesActivity;
import com.fieldbook.tracker.activities.ThemedActivity;

public class ThemePreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    PreferenceManager prefMgr;
    Context context;
    private Preference restoreTheme;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName(GeneralKeys.SHARED_PREF_FILE_NAME);

        setPreferencesFromResource(R.xml.preferences_theme, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.preferences_appearance_theme_title));

        restoreTheme = findPreference("RESTORE_DEFAULT_THEME");

        restoreTheme.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {

            public boolean onPreferenceClick(Preference preference) {

                TypedValue value = new TypedValue();
                getContext().getTheme().resolveAttribute(R.attr.fb_value_saved_color, value, true);

                getContext().getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putInt(GeneralKeys.SAVED_DATA_COLOR, value.data)
                    .putString(GeneralKeys.TEXT_THEME, String.valueOf(ThemedActivity.MEDIUM))
                    .putString(GeneralKeys.THEME, String.valueOf(ThemedActivity.DEFAULT))
                    .apply();

                PreferenceManager.getDefaultSharedPreferences(context)
                    .edit()
                    .putString(GeneralKeys.TEXT_THEME, String.valueOf(ThemedActivity.MEDIUM))
                    .putString(GeneralKeys.THEME, String.valueOf(ThemedActivity.DEFAULT))
                    .apply();

                refreshTheme();

                return true;
            }
        });

        /*
         * When theme is updated, save it to preferences.
         */
        Preference textPref = findPreference(GeneralKeys.TEXT_THEME);
        if (textPref != null) {
            textPref.setOnPreferenceChangeListener((pref, value) -> {

                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putString(GeneralKeys.TEXT_THEME, (String) value).apply();

                refreshTheme();

                return true;

            });
        }

        Preference themePref = findPreference(GeneralKeys.THEME);
        if (themePref != null) {
            themePref.setOnPreferenceChangeListener((pref, value) -> {

                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putString(GeneralKeys.THEME, (String) value).apply();

                refreshTheme();

                return true;

            });
        }
    }

    private void refreshTheme() {

        getParentFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(GeneralKeys.THEME_FLAG, true)
                .apply();

        //force recreate the activity so the theme applies
        getActivity().recreate();
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Occurs before the on create function. We get the context this way.
        ThemePreferencesFragment.this.context = context;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }
}