package com.fieldbook.tracker.preferences;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.PreferencesActivity;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class ProfilePreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    @Inject
    SharedPreferences preferences;

    Context context;
    private Preference profilePerson;
//    private ListPreference verificationInterval;
    private AlertDialog personDialog;

    private Preference profileDeviceName;

    private AlertDialog deviceNameDialog;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {

        setPreferencesFromResource(R.xml.preferences_profile, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.settings_profile));

        profilePerson = findPreference("pref_profile_person");
        if (profilePerson != null) {
            profilePerson.setSummary(personSummary());
        }
        //        verificationInterval = findPreference("com.tracker.fieldbook.preference.require_user_interval");

        profileDeviceName = findPreference("pref_profile_device_name");
        if (profileDeviceName != null) {
            profileDeviceName.setSummary(deviceNameSummary());
        }

        profilePerson.setOnPreferenceClickListener(preference -> {
            showPersonDialog();
            return true;
        });

        profileDeviceName.setOnPreferenceClickListener(preference -> {
            showDeviceNameDialog();
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


    private void showDeviceNameDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_device_name, null);
        final EditText deviceName = layout.findViewById(R.id.deviceName);
        // set name to default if not set
        deviceName.setText(preferences.getString(GeneralKeys.DEVICE_NAME, Build.MODEL));

        deviceName.setSelectAllOnFocus(true);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.AppAlertDialog);
        builder.setTitle(R.string.preferences_profile_device_name_dialog_title)
                .setCancelable(true)
                .setView(layout);

        builder.setPositiveButton(getString(R.string.dialog_save), null);
        builder.setNegativeButton(getString(R.string.dialog_cancel), (dialog, i) -> dialog.dismiss());
        builder.setNeutralButton(getString(R.string.dialog_clear), null);

        deviceNameDialog = builder.create();
        deviceNameDialog.setOnShowListener(dialog -> {
            AlertDialog alertDialog = (AlertDialog) dialog;
            alertDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String deviceNameStr = deviceName.getText().toString().trim();
                if (deviceNameStr.isEmpty()) {
                    // Display an error message
                    deviceName.setError(getString(R.string.preferences_profile_name_error));
                } else {
                    // Save the names
                    SharedPreferences.Editor e = preferences.edit();
                    e.putString(GeneralKeys.DEVICE_NAME, deviceNameStr);
                    e.apply();
                    profileDeviceName.setSummary(deviceNameSummary());
                    alertDialog.dismiss();
                }
            });
            // Set click listener for neutral button
            alertDialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v -> {
                deviceName.setText("");
            });
        });

        deviceNameDialog.show();

        android.view.WindowManager.LayoutParams langParams = deviceNameDialog.getWindow().getAttributes();
        langParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
        deviceNameDialog.getWindow().setAttributes(langParams);
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

    private String deviceNameSummary() {
        String deviceName = preferences.getString(GeneralKeys.DEVICE_NAME, "");
        String defaultDeviceName = getString(R.string.preferences_profile_device_name_default_format, Build.MODEL);

        return deviceName.isEmpty() ? defaultDeviceName : deviceName;
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
    }
}