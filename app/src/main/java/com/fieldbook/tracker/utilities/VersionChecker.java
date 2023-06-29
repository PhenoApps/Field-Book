package com.fieldbook.tracker.utilities;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.util.Log;

import androidx.documentfile.provider.DocumentFile;

import com.fieldbook.tracker.R;

import org.json.JSONException;
import org.json.JSONObject;
import org.phenoapps.utils.BaseDocumentTreeUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.annotation.Nullable;


public class VersionChecker extends AsyncTask<Void, Void, String> {
    private static final String GITHUB_API_URL = "https://api.github.com/repos/{owner}/{repo}/releases/latest";
    private static final String TAG = "VersionChecker";
    private final String currentVersion;
    private final String owner;
    private final String repo;
    private final WeakReference<Context> context; // Add context field

    @Nullable
    protected Context getContext() {
        return context.get();
    }

    //should be updated to kotlin coroutines or work manager
    public VersionChecker(Context context, String currentVersion, String owner, String repo) {
        this.context = new WeakReference<>(context);
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

            Context ctx = getContext();

            if (ctx != null) {

                try {
                    JSONObject json = new JSONObject(result);
                    String latestVersion = json.getString("tag_name");
                    String downloadUrl = json.getJSONArray("assets").getJSONObject(0).getString("browser_download_url");

                    // Show a dialog to the user indicating if an update is available
                    AlertDialog.Builder builder = new AlertDialog.Builder(ctx, R.style.AlertDialogStyle);
                    builder.setTitle("Update Check");

                    if (isNewerVersion(currentVersion, latestVersion)) {
                        Log.i(TAG, "New version available: " + latestVersion);
                        builder.setMessage("New version available: " + latestVersion);
                        builder.setPositiveButton("Update", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                // Call a method to handle the update process
                                updateApp(downloadUrl);
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
            }

        } else {
            Log.e(TAG, "Failed to retrieve version information from GitHub");
        }
    }

    protected void updateApp(String downloadURL) {
        new UpdateTask().execute(downloadURL);
    }

    private class UpdateTask extends AsyncTask<String, Void, DocumentFile> {
        private ProgressDialog progressDialog;

        @Override
        protected void onPreExecute() {
            super.onPreExecute();

            Context ctx = getContext();

            if (ctx != null) {

                String progressMessage = ctx.getString(R.string.util_version_checker_progress_message);
                // Show progress dialog
                progressDialog = new ProgressDialog(ctx);
                progressDialog.setIndeterminate(true);
                progressDialog.setCancelable(false);
                progressDialog.setMessage(progressMessage);
                progressDialog.show();
            }
        }

        @Override
        protected DocumentFile doInBackground(String... params) {

            Context ctx = getContext();

            if (ctx != null) {

                String downloadURL = params[0];
                // Specify the download directory
                DocumentFile downloadDir = BaseDocumentTreeUtil.Companion.getDirectory(ctx, R.string.dir_updates);
                if (downloadDir != null && downloadDir.exists()) {
                    // Create the file for the APK download
                    DocumentFile apkFile = downloadDir.createFile("application/vnd.android.package-archive", "fieldbook_update.apk");

                    if (apkFile != null && apkFile.exists()) {
                        try {
                            // Open an OutputStream to write the APK content
                            OutputStream apkOutputStream = ctx.getContentResolver().openOutputStream(apkFile.getUri());

                            // Download the APK file and write it to the OutputStream
                            URL url = new URL(downloadURL);
                            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                            InputStream inputStream = connection.getInputStream();

                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = inputStream.read(buffer)) != -1) {
                                apkOutputStream.write(buffer, 0, bytesRead);
                            }

                            // Close the streams
                            apkOutputStream.close();
                            inputStream.close();

                            return apkFile;
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

            return null;
        }

        @Override
        protected void onPostExecute(DocumentFile apkFile) {
            super.onPostExecute(apkFile);

            Context context = getContext();

            if (context != null) {

                // Dismiss the progress dialog
                progressDialog.dismiss();

                if (apkFile != null) {
                    // Install the APK
                    Uri apkUri = apkFile.getUri();
                    Intent installIntent = new Intent(Intent.ACTION_VIEW);
                    installIntent.setDataAndType(apkUri, "application/vnd.android.package-archive");
                    installIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    installIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    context.startActivity(installIntent);
                }
            }
        }
    }

    private boolean isNewerVersion(String currentVersion, String latestVersion) {
        // Split the version strings into their components
        String[] currentVersionComponents = currentVersion.split("\\.");
        String[] latestVersionComponents = latestVersion.split("\\.");
        Log.i(TAG, "Comparing currentVersion " + currentVersion + " to latestVersion " + latestVersion);
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
