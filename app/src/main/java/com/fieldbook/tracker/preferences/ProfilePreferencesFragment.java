package com.fieldbook.tracker.preferences;

import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.CheckBoxPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.PreferencesActivity;
import com.fieldbook.tracker.utilities.DialogUtils;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProfilePreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    @Inject
    SharedPreferences preferences;

    private static final String TAG = ProfilePreferencesFragment.class.getSimpleName();

    Context context;
    private Preference profilePerson;
    private Preference profileReset;
    private AlertDialog personDialog;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        setPreferencesFromResource(R.xml.preferences_profile, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.settings_profile));

        profilePerson = findPreference("pref_profile_person");

        updateSummaries();

        profileReset = findPreference("pref_profile_reset");

        profilePerson.setOnPreferenceClickListener(preference -> {
            showPersonDialog();
            return true;
        });

        profileReset.setOnPreferenceClickListener(preference -> {
            showClearSettingsDialog();
            return true;
        });

        Bundle arguments = getArguments();

        if (arguments != null) {

            boolean updatePerson = arguments.getBoolean(GeneralKeys.PERSON_UPDATE, false);

            if (updatePerson) {

                showPersonDialog();

            }
        }

        preferences.edit().putLong(GeneralKeys.LAST_TIME_OPENED, System.nanoTime()).apply();

        Preference requirePersonPref = findPreference(GeneralKeys.REQUIRE_USER_TO_COLLECT);
        if (requirePersonPref != null) {
            requirePersonPref.setOnPreferenceChangeListener((pref, value) -> {
                setupPersonUpdateUi((Boolean) value);
                return true;
            });
        }

        setupPersonUpdateUi(null);
    }

    private void showClearSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.AppAlertDialog);
        builder.setTitle(getString(R.string.profile_reset));
        builder.setMessage(getString(R.string.dialog_confirm));

        builder.setPositiveButton(getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                SharedPreferences.Editor ed = preferences.edit();
                ed.putString(GeneralKeys.FIRST_NAME, "");
                ed.putString(GeneralKeys.LAST_NAME, "");
                ed.apply();

                updateSummaries();
            }
        });

        builder.setNegativeButton(getString(R.string.dialog_no), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        AlertDialog alert = builder.create();
        alert.show();
        DialogUtils.styleDialogs(alert);
    }

    private void showPersonDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_person, null);
        final EditText firstName = layout.findViewById(R.id.firstName);
        final EditText lastName = layout.findViewById(R.id.lastName);

        firstName.setText(preferences.getString(GeneralKeys.FIRST_NAME, ""));
        lastName.setText(preferences.getString(GeneralKeys.LAST_NAME, ""));

        firstName.setSelectAllOnFocus(true);
        lastName.setSelectAllOnFocus(true);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.AppAlertDialog);
        builder.setTitle(R.string.profile_person_title)
                .setCancelable(true)
                .setView(layout);

        builder.setPositiveButton(getString(R.string.dialog_save), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences.Editor e = preferences.edit();

                e.putString(GeneralKeys.FIRST_NAME, firstName.getText().toString());
                e.putString(GeneralKeys.LAST_NAME, lastName.getText().toString());

                e.apply();
                updateSummaries();
            }
        });

        builder.setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                dialog.dismiss();
            }
        });

        personDialog = builder.create();
        personDialog.show();
        DialogUtils.styleDialogs(personDialog);

        android.view.WindowManager.LayoutParams langParams = personDialog.getWindow().getAttributes();
        langParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
        personDialog.getWindow().setAttributes(langParams);
    }

    private void updateSummaries() {
        profilePerson.setSummary(personSummary());
    }

    private String personSummary() {
        String tagName = "";

        if (preferences.getString(GeneralKeys.FIRST_NAME, "").length() > 0 | preferences.getString(GeneralKeys.LAST_NAME, "").length() > 0) {
            tagName += preferences.getString(GeneralKeys.FIRST_NAME, "") + " " + preferences.getString(GeneralKeys.LAST_NAME, "");
        } else {
            tagName = "";
        }

        return tagName;
    }

    private String refreshIdSummary(Preference refresh) {

        String newId = UUID.randomUUID().toString();

        preferences.edit().putString(GeneralKeys.CRASHLYTICS_ID, newId).apply();

        refresh.setSummary(newId);

        FirebaseCrashlytics instance = FirebaseCrashlytics.getInstance();
        instance.setUserId(newId);
        instance.setCustomKey(GeneralKeys.CRASHLYTICS_KEY_USER_TOKEN, newId);

        return newId;
    }

    private void setupCrashlyticsPreference() {

        try {

            CheckBoxPreference enablePref = findPreference(GeneralKeys.CRASHLYTICS_ID_ENABLED);
            Preference refreshPref = findPreference(GeneralKeys.CRASHLYTICS_ID_REFRESH);

            //check both preferences are found
            if (enablePref != null && refreshPref != null) {

                //check box listener, setup refresh visibility / on click implementation
                enablePref.setOnPreferenceChangeListener((pref, newValue) -> {

                    //get current id, might be null
                    AtomicReference<String> id = new AtomicReference<>(preferences.getString(GeneralKeys.CRASHLYTICS_ID, null));

                    boolean enabled = (boolean) newValue;

                    refreshPref.setVisible(enabled);

                    //when refresh is clicked, update the unique id in the preferencs and update summary
                    refreshPref.setOnPreferenceClickListener((v) -> {

                        if (enabled) {

                            id.set(refreshIdSummary(refreshPref));

                        }

                        return true;
                    });

                    if (enabled && id.get() == null) {

                        id.set(refreshIdSummary(refreshPref));

                    } else if (!enabled) {

                        FirebaseCrashlytics instance = FirebaseCrashlytics.getInstance();
                        instance.setUserId("");
                        instance.setCustomKey(GeneralKeys.CRASHLYTICS_KEY_USER_TOKEN, "");
                    }

                    return true;
                });

                //get current id, might be null
                String id = preferences.getString(GeneralKeys.CRASHLYTICS_ID, null);

                //when checkbox is initialized, check if id is null and update refresh vis
                //update refresh summary as well
                refreshPref.setVisible(enablePref.isChecked());

                if (id != null) {

                    refreshPref.setSummary(id);

                    refreshPref.setOnPreferenceClickListener((v) -> {

                        refreshIdSummary(refreshPref);

                        return true;
                    });
                }
            }

        } catch (Exception e) {

            e.printStackTrace();

            Log.d(TAG, "Crashlytics setup failed.");
        }
    }

    private void setupPersonUpdateUi(@Nullable Boolean explicitUpdate) {

        Boolean updateFlag = explicitUpdate;

        //set visibility of update choices only if enabled
        if (explicitUpdate == null) {
            updateFlag = preferences.getBoolean(GeneralKeys.REQUIRE_USER_TO_COLLECT, false);
        }

        Preference updateInterval = findPreference(GeneralKeys.REQUIRE_USER_INTERVAL);

        if (updateInterval != null) {
            updateInterval.setVisible(updateFlag);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        // Occurs before the on create function. We get the context this way.
        ProfilePreferencesFragment.this.context = context;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return false;
    }

    @Override
    public void onResume() {
        super.onResume();

        setupCrashlyticsPreference();
        setupPersonUpdateUi(null);
    }
}