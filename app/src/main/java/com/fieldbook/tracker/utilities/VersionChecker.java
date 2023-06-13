package com.fieldbook.tracker.utilities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.AsyncTask;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;


public class VersionChecker extends AsyncTask<Void, Void, String> {
    private static final String GITHUB_API_URL = "https://api.github.com/repos/{owner}/{repo}/releases/latest";
    private static final String TAG = "VersionChecker";
    private String currentVersion;
    private String owner;
    private String repo;
    private Context context; // Add context field

    public VersionChecker(Context context, String currentVersion, String owner, String repo) {
        this.context = context;
        this.currentVersion = currentVersion;
        this.owner = owner;
        this.repo = repo;
    }

    @Override
    protected String doInBackground(Void... params) {
        try {
            URL url = new URL(GITHUB_API_URL.replace("{owner}", owner).replace("{repo}", repo));
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");

            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                return response.toString();
            } else {
                Log.e(TAG, "HTTP request failed with response code: " + responseCode);
            }
        } catch (IOException e) {
            Log.e(TAG, "Error occurred while checking for updates: " + e.getMessage());
        }
        return null;
    }

    @Override
    protected void onPostExecute(String result) {
        if (result != null) {
            try {
                JSONObject json = new JSONObject(result);
                String latestVersion = json.getString("tag_name");
                // Show a dialog to the user indicating if an update is available
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                builder.setTitle("Update Check");
                if (isNewerVersion(latestVersion, currentVersion)) {
                    Log.i(TAG, "New version available: " + latestVersion);
                    builder.setMessage("New version available: " + latestVersion);
                    builder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            // Call a method to handle the update process
                            // updateApp();
                            dialog.dismiss();
                        }
                    });
                    builder.setNegativeButton("Dismiss", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                } else {
                    Log.i(TAG, "Field Book is up to date.");
                    builder.setMessage("Field Book is up to date.");
                    builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            dialog.dismiss();
                        }
                    });
                }
                builder.show(); // Display the dialog
            } catch (JSONException e) {
                Log.e(TAG, "Error parsing JSON response: " + e.getMessage());
            }
        } else {
            Log.e(TAG, "Failed to retrieve version information from GitHub");
        }
    }

    private boolean isNewerVersion(String currentVersion, String latestVersion) {
        // Split the version strings into their components
        String[] currentVersionComponents = currentVersion.split("\\.");
        String[] latestVersionComponents = latestVersion.split("\\.");

        // Ignore the last component (build number) if it exists
        int versionComponentsToCompare = Math.min(currentVersionComponents.length, latestVersionComponents.length);
        if (versionComponentsToCompare > 3) {
            versionComponentsToCompare--;  // Ignore the last component
        }

        // Compare each component of the version
        for (int i = 0; i < versionComponentsToCompare; i++) {
            int currentComponent = Integer.parseInt(currentVersionComponents[i]);
            int latestComponent = Integer.parseInt(latestVersionComponents[i]);

            if (currentComponent < latestComponent) {
                return true;  // latestVersion is newer
            } else if (currentComponent > latestComponent) {
                return false; // currentVersion is newer
            }
        }

        // All compared components are equal, consider them equal versions
        return false;
    }

}
