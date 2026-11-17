package com.fieldbook.tracker.activities;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;

import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.bytehamster.lib.preferencesearch.SearchPreferenceResult;
import com.bytehamster.lib.preferencesearch.SearchPreferenceResultListener;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.preferences.AppearancePreferencesFragment;
import com.fieldbook.tracker.preferences.BehaviorPreferencesFragment;
import com.fieldbook.tracker.preferences.BrapiAdvancedPreferencesFragment;
import com.fieldbook.tracker.preferences.BrapiPreferencesFragment;
import com.fieldbook.tracker.preferences.ExperimentalPreferencesFragment;
import com.fieldbook.tracker.preferences.FeaturesPreferencesFragment;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.preferences.LocationPreferencesFragment;
import com.fieldbook.tracker.preferences.PreferencesFragment;
import com.fieldbook.tracker.preferences.ProfilePreferencesFragment;
import com.fieldbook.tracker.preferences.SoundsPreferencesFragment;
import com.fieldbook.tracker.preferences.StoragePreferencesFragment;
import com.fieldbook.tracker.preferences.SystemPreferencesFragment;
import com.fieldbook.tracker.preferences.ThemePreferencesFragment;
import com.fieldbook.tracker.utilities.InsetHandler;

public class PreferencesActivity extends ThemedActivity implements PreferenceFragmentCompat.OnPreferenceStartFragmentCallback, SearchPreferenceResultListener {

    private PreferencesFragment prefsFragment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preferences);

        Toolbar toolbar = findViewById(R.id.act_preferences_toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(getString(R.string.settings_advanced));
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }

        prefsFragment = new PreferencesFragment();
        getSupportFragmentManager().beginTransaction().replace(R.id.prefs_container, prefsFragment).commit();

        //parse passed bundle and check if specific preference should be updated.
        Bundle extras = getIntent().getExtras();
        if (extras != null && extras.getBoolean(GeneralKeys.PERSON_UPDATE, false)) {

            //starts profile fragment with bundle that tells the fragment to open the person setting
            Fragment profile = new ProfilePreferencesFragment();
            Bundle personUpdate = new Bundle();
            personUpdate.putBoolean(GeneralKeys.PERSON_UPDATE, true);
            profile.setArguments(personUpdate);
            getSupportFragmentManager().beginTransaction().replace(R.id.prefs_container, profile).commit();

        } else if (extras != null && extras.getBoolean(GeneralKeys.MODIFY_PROFILE_SETTINGS, false)) {

            //starts profile fragment without opening the person setting
            Fragment profile = new ProfilePreferencesFragment();
            getSupportFragmentManager().beginTransaction().replace(R.id.prefs_container, profile).commit();

        } else if (extras != null && extras.getBoolean(GeneralKeys.INFOBAR_UPDATE, false)) {

            //starts appearance fragment with bundle that tells the fragment to open the infobars setting
            Fragment appearance = new AppearancePreferencesFragment();
            Bundle infobarsUpdate = new Bundle();
            infobarsUpdate.putBoolean(GeneralKeys.INFOBAR_UPDATE, true);
            appearance.setArguments(infobarsUpdate);
            getSupportFragmentManager().beginTransaction().replace(R.id.prefs_container, appearance).commit();

        } else if (extras != null && extras.getBoolean(GeneralKeys.BARCODE_SCANNING_OPTIONS_EDIT, false)) {

            Fragment behavior = new BehaviorPreferencesFragment();
            Bundle barcodeUpdate = new Bundle();
            barcodeUpdate.putBoolean(GeneralKeys.BARCODE_SCANNING_OPTIONS_EDIT, true);
            behavior.setArguments(barcodeUpdate);
            getSupportFragmentManager().beginTransaction().replace(R.id.prefs_container, behavior).commit();
        }

        boolean flag = PreferenceManager.getDefaultSharedPreferences(this).getBoolean(GeneralKeys.THEME_FLAG, false);
        if (flag) {
            getSupportFragmentManager().beginTransaction().replace(R.id.prefs_container, new AppearancePreferencesFragment()).addToBackStack("PrefsFrag").commit();
        }

        View rootView = findViewById(R.id.prefs_container);
        InsetHandler.INSTANCE.setupPreferenceInsets(rootView, toolbar);

        getOnBackPressedDispatcher().addCallback(this, fragmentBasedBackCallback());
    }

    @Override
    public void onSearchResultClicked(SearchPreferenceResult result) {
        PreferenceFragmentCompat target = fragmentForSearchResult(result);

        if (target instanceof PreferencesFragment) {
            prefsFragment = (PreferencesFragment) target;
        }

        getSupportFragmentManager().beginTransaction().replace(R.id.prefs_container, target).addToBackStack("PrefsFragment").commit(); // Allow to navigate back to search

        new Handler(Looper.getMainLooper()).post(new Runnable() { // Allow fragment to get created
            @Override
            public void run() {
                if (target instanceof PreferencesFragment) {
                    ((PreferencesFragment) target).onSearchResultClicked(result);
                } else {
                    result.highlight(target);
                }
            }
        });
    }

    /**
     * Maps an indexed preference screen back to the fragment that owns it.
     *
     * Search results have to open the real fragment rather than have their XML inflated into
     * {@link PreferencesFragment}: most of these screens attach click and change listeners in
     * their own onCreatePreferences(), and a bare inflate silently drops all of that — leaving
     * preferences that look right but do nothing when tapped.
     *
     * Anything unmapped (including the root screen) falls back to PreferencesFragment, which
     * scrolls to the result on the root screen and inflates the raw XML otherwise.
     */
    private PreferenceFragmentCompat fragmentForSearchResult(SearchPreferenceResult result) {
        int file = result.getResourceFile();

        if (file == R.xml.preferences_profile) return new ProfilePreferencesFragment();
        if (file == R.xml.preferences_features) return new FeaturesPreferencesFragment();
        if (file == R.xml.preferences_appearance) return new AppearancePreferencesFragment();
        if (file == R.xml.preferences_theme) return new ThemePreferencesFragment();
        if (file == R.xml.preferences_behavior) return new BehaviorPreferencesFragment();
        if (file == R.xml.preferences_location) return new LocationPreferencesFragment();
        if (file == R.xml.preferences_sounds) return new SoundsPreferencesFragment();
        if (file == R.xml.preferences_brapi) return new BrapiPreferencesFragment();
        if (file == R.xml.preferences_brapi_advanced) return new BrapiAdvancedPreferencesFragment();
        if (file == R.xml.preferences_system) return new SystemPreferencesFragment();
        if (file == R.xml.preferences_storage) return new StoragePreferencesFragment();
        if (file == R.xml.preferences_experimental) return new ExperimentalPreferencesFragment();

        return new PreferencesFragment();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        if (item.getItemId() == android.R.id.home) {
            setResult(RESULT_OK);
            getOnBackPressedDispatcher().onBackPressed();
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Override
    public boolean onPreferenceStartFragment(PreferenceFragmentCompat caller, androidx.preference.Preference pref) {

        // Instantiate the new Fragment
        final Bundle args = pref.getExtras();
        final Fragment fragment = getSupportFragmentManager().getFragmentFactory().instantiate(getClassLoader(), pref.getFragment());
        fragment.setArguments(args);
        fragment.setTargetFragment(caller, 0);

        // Replace the existing Fragment with the new Fragment
        getSupportFragmentManager().beginTransaction().replace(R.id.prefs_container, fragment).addToBackStack(null).commit();
        return true;
    }

    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        for (Fragment fragment : getSupportFragmentManager().getFragments()) {
            fragment.onActivityResult(requestCode, resultCode, data);
        }
    }
}