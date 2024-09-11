package com.fieldbook.tracker.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.PreferencesActivity;
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
//    private ListPreference verificationInterval;
    private AlertDialog personDialog;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        setPreferencesFromResource(R.xml.preferences_profile, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.settings_profile));

        profilePerson = findPreference("pref_profile_person");
        profilePerson.setSummary(personSummary());
//        verificationInterval = findPreference("com.tracker.fieldbook.preference.require_user_interval");

        profilePerson.setOnPreferenceClickListener(preference -> {
            showPersonDialog();
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
        builder.setTitle(R.string.preferences_profile_person_dialog_title)
                .setCancelable(true)
                .setView(layout);

        builder.setPositiveButton(getString(R.string.dialog_save), null);
        builder.setNegativeButton(getString(R.string.dialog_cancel), (dialog, i) -> dialog.dismiss());
        builder.setNeutralButton(getString(R.string.dialog_clear), null);

        personDialog = builder.create();
        personDialog.setOnShowListener(dialog -> {
            AlertDialog alertDialog = (AlertDialog) dialog;
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String firstNameStr = firstName.getText().toString().trim();
                String lastNameStr = lastName.getText().toString().trim();
                if (firstNameStr.isEmpty() && lastNameStr.isEmpty()) {
                    // Display an error message
                    firstName.setError(getString(R.string.preferences_profile_name_error));
                    lastName.setError(getString(R.string.preferences_profile_name_error));
                } else {
                    // Save the names
                    SharedPreferences.Editor e = preferences.edit();
                    e.putString(GeneralKeys.FIRST_NAME, firstNameStr);
                    e.putString(GeneralKeys.LAST_NAME, lastNameStr);
                    e.apply();
                    profilePerson.setSummary(personSummary());
                    alertDialog.dismiss();
                }
            });
            // Set click listener for neutral button
            alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                firstName.setText("");
                lastName.setText("");
            });
        });

        personDialog.show();

        android.view.WindowManager.LayoutParams langParams = personDialog.getWindow().getAttributes();
        langParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
        personDialog.getWindow().setAttributes(langParams);
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
    }
}