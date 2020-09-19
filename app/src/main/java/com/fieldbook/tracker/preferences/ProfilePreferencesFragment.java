package com.fieldbook.tracker.preferences;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.location.GPSTracker;
import com.fieldbook.tracker.utilities.DialogUtils;

import pub.devrel.easypermissions.AfterPermissionGranted;
import pub.devrel.easypermissions.EasyPermissions;

public class ProfilePreferencesFragment extends PreferenceFragmentCompat implements Preference.OnPreferenceChangeListener {

    PreferenceManager prefMgr;
    Context context;
    private Preference profilePerson;
    private Preference profileLocation;
    private Preference profileReset;
    SharedPreferences ep;
    private double lat;
    private double lng;
    private AlertDialog personDialog;
    private AlertDialog locationDialog;

    private final int PERMISSIONS_REQUEST_LOCATION = 9960;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        prefMgr = getPreferenceManager();
        prefMgr.setSharedPreferencesName("Settings");

        setPreferencesFromResource(R.xml.preferences_profile, rootKey);

        ((PreferencesActivity) this.getActivity()).getSupportActionBar().setTitle(getString(R.string.settings_profile));

        ep = getContext().getSharedPreferences("Settings", Context.MODE_MULTI_PROCESS);

        profilePerson = findPreference("pref_profile_person");
        profileLocation = findPreference("pref_profile_location");

        updateSummaries();

        profileReset = findPreference("pref_profile_reset");

        profilePerson.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                showPersonDialog();
                return true;
            }
        });

        profileLocation.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            public boolean onPreferenceClick(Preference preference) {
                locationDialogPermission();
                return true;
            }
        });

        profileReset.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                showClearSettingsDialog();
                return true;
            }
        });

    }

    private void showClearSettingsDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.AppAlertDialog);
        builder.setTitle(getString(R.string.profile_reset));
        builder.setMessage(getString(R.string.dialog_confirm));

        builder.setPositiveButton(getString(R.string.dialog_yes), new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();

                SharedPreferences ep = getContext().getSharedPreferences("Settings", Context.MODE_MULTI_PROCESS);
                SharedPreferences.Editor editor = ep.edit();

                SharedPreferences.Editor ed = ep.edit();
                ed.putString("FirstName", "");
                ed.putString("LastName", "");
                ed.putString("Location", "");
                ed.putString("Latitude", "");
                ed.putString("Longitude", "");
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

        firstName.setText(ep.getString("FirstName", ""));
        lastName.setText(ep.getString("LastName", ""));

        firstName.setSelectAllOnFocus(true);
        lastName.setSelectAllOnFocus(true);

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.AppAlertDialog);
        builder.setTitle(R.string.profile_person_title)
                .setCancelable(true)
                .setView(layout);

        builder.setPositiveButton(getString(R.string.dialog_save), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences.Editor e = ep.edit();

                e.putString("FirstName", firstName.getText().toString());
                e.putString("LastName", lastName.getText().toString());

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

    private void showLocationDialog() {
        LayoutInflater inflater = this.getLayoutInflater();
        View layout = inflater.inflate(R.layout.dialog_location, null);

        GPSTracker gps = new GPSTracker(getContext());
        if (gps.canGetLocation()) { //GPS enabled
            lat = gps.getLatitude(); // returns latitude
            lng = gps.getLongitude(); // returns longitude
        } else {
            Intent intent = new Intent(
                    Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

        final EditText longitude = layout.findViewById(R.id.longitude);
        final EditText latitude = layout.findViewById(R.id.latitude);

        longitude.setText(ep.getString("Longitude", ""));
        latitude.setText(ep.getString("Latitude", ""));

        AlertDialog.Builder builder = new AlertDialog.Builder(getContext(), R.style.AppAlertDialog);

        builder.setTitle(R.string.profile_location_title)
                .setCancelable(true)
                .setView(layout);

        builder.setPositiveButton(getString(R.string.dialog_save), new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                SharedPreferences.Editor e = ep.edit();
                if (latitude.getText().toString().length() > 0 && longitude.getText().toString().length() > 0) {
                    e.putString("Location", latitude.getText().toString() + " ; " + longitude.getText().toString());
                    e.putString("Latitude", latitude.getText().toString());
                    e.putString("Longitude", longitude.getText().toString());
                } else {
                    e.putString("Location", "null");
                }

                e.apply();

                locationDialog.dismiss();
                updateSummaries();
            }
        });

        builder.setNegativeButton(getString(R.string.dialog_cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int i) {
                dialog.dismiss();
            }
        });

        builder.setNeutralButton(getString(R.string.profile_location_get), null);

        locationDialog = builder.create();
        locationDialog.show();
        DialogUtils.styleDialogs(locationDialog);

        android.view.WindowManager.LayoutParams langParams = locationDialog.getWindow().getAttributes();
        langParams.width = LinearLayout.LayoutParams.MATCH_PARENT;
        locationDialog.getWindow().setAttributes(langParams);

        // Override neutral button so it doesnt automatically dismiss location dialog
        Button neutralButton = locationDialog.getButton(AlertDialog.BUTTON_NEUTRAL);
        neutralButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View arg0) {
                latitude.setText(truncateDecimalString(String.valueOf(lat)));
                longitude.setText(truncateDecimalString(String.valueOf(lng)));
            }
        });
    }

    private void updateSummaries() {
        profilePerson.setSummary(personSummary());
        profileLocation.setSummary(locationSummary());
    }

    private String personSummary() {
        String tagName = "";

        if (ep.getString("FirstName", "").length() > 0 | ep.getString("LastName", "").length() > 0) {
            tagName += ep.getString("FirstName", "") + " " + ep.getString("LastName", "");
        } else {
            tagName = "";
        }

        return tagName;
    }

    private String locationSummary() {
        String tagLocation = "";

        if (ep.getString("Location", "").length() > 0) {
            tagLocation += ep.getString("Location", "");
        } else {
            tagLocation = "";
        }

        return tagLocation;
    }

    // Only used for truncating lat long values
    public String truncateDecimalString(String v) {
        int count = 0;

        boolean found = false;

        StringBuilder truncated = new StringBuilder();

        for (int i = 0; i < v.length(); i++) {
            if (found) {
                count += 1;

                if (count == 5)
                    break;
            }

            if (v.charAt(i) == '.') {
                found = true;
            }

            truncated.append(v.charAt(i));
        }

        return truncated.toString();
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

    @AfterPermissionGranted(PERMISSIONS_REQUEST_LOCATION)
    private void locationDialogPermission() {
        String[] perms = {Manifest.permission.ACCESS_FINE_LOCATION};
        if (EasyPermissions.hasPermissions(getContext(), perms)) {
            showLocationDialog();
        } else {
            // Do not have permissions, request them now
            EasyPermissions.requestPermissions(this, getString(R.string.permission_rationale_location),
                    PERMISSIONS_REQUEST_LOCATION, perms);
        }
    }
}