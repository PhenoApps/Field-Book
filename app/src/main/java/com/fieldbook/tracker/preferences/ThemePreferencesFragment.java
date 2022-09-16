package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.TypedValue;

import androidx.fragment.app.FragmentManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.PreferencesActivity;

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

                SharedPreferences.Editor ed = getContext().getSharedPreferences(GeneralKeys.SHARED_PREF_FILE_NAME, Context.MODE_MULTI_PROCESS).edit();
                TypedValue value = new TypedValue();
                getContext().getTheme().resolveAttribute(R.attr.fb_value_saved_color, value, true);

                ed.putInt(GeneralKeys.SAVED_DATA_COLOR, value.data);
                ed.apply();

                getParentFragmentManager()
                        .beginTransaction()
                        .detach(ThemePreferencesFragment.this)
                        .attach(ThemePreferencesFragment.this)
                        .commit();
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

                getParentFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putBoolean(GeneralKeys.THEME_FLAG, true)
                        .apply();

                //force recreate the activity so the theme applies
                getActivity().recreate();

                return true;

            });
        }

        Preference themePref = findPreference(GeneralKeys.THEME);
        if (themePref != null) {
            themePref.setOnPreferenceChangeListener((pref, value) -> {

                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putString(GeneralKeys.THEME, (String) value).apply();

                getParentFragmentManager().popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);

                PreferenceManager.getDefaultSharedPreferences(context).edit()
                        .putBoolean(GeneralKeys.THEME_FLAG, true)
                        .apply();

                //force recreate the activity so the theme applies
                getActivity().recreate();

                return true;

            });
        }
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