package com.fieldbook.tracker.activities;


import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.danielstone.materialaboutlibrary.ConvenienceBuilder;
import com.danielstone.materialaboutlibrary.MaterialAboutActivity;
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem;
import com.danielstone.materialaboutlibrary.items.MaterialAboutItemOnClickAction;
import com.danielstone.materialaboutlibrary.items.MaterialAboutTitleItem;
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard;
import com.danielstone.materialaboutlibrary.model.MaterialAboutList;
import com.fieldbook.tracker.BuildConfig;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.michaelflisar.changelog.ChangelogBuilder;
import com.michaelflisar.changelog.classes.ImportanceChangelogSorter;
import com.michaelflisar.changelog.internal.ChangelogDialogFragment;
import com.mikepenz.aboutlibraries.LibsBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONException;
import org.json.JSONObject;

public class AboutActivity extends MaterialAboutActivity {
    //todo move to fragments so aboutactivity can extend base activity

    // Declaration of the "Updates" action item
    private MaterialAboutActionItem updateCheckItem;
    private CircularProgressDrawable circularProgressDrawable;
    private static final String TAG = AboutActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemedActivity.Companion.applyTheme(this);
        super.onCreate(savedInstanceState);
        checkForUpdate();
        circularProgressDrawable = new CircularProgressDrawable(this);
        circularProgressDrawable.setStyle(CircularProgressDrawable.DEFAULT);
        circularProgressDrawable.start();
    }

    private MaterialAboutActionItem.Builder updatesButtonBuilder;
    @Override
    @NonNull
    public MaterialAboutList getMaterialAboutList(@NonNull Context c) {

        MaterialAboutCard.Builder appCardBuilder = new MaterialAboutCard.Builder();
        appCardBuilder.addItem(new MaterialAboutTitleItem.Builder()
                .text(getString(R.string.field_book))
                .icon(R.mipmap.ic_launcher)
                .build());

        appCardBuilder.addItem(ConvenienceBuilder.createVersionActionItem(c,
                getResources().getDrawable(R.drawable.ic_about_info),
                getString(R.string.about_version_title),
                false));

        // Save a reference to the "Updates" action item
        updateCheckItem = new MaterialAboutActionItem.Builder()
                .text(getString(R.string.check_updates_title))
                .icon(circularProgressDrawable)
                .setOnClickAction(null) // Set initially to null, will be updated later
                .build();

        appCardBuilder.addItem(updateCheckItem);

        appCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text(getString(R.string.changelog_title))
                .icon(R.drawable.ic_about_changelog)
                .setOnClickAction(new MaterialAboutItemOnClickAction() {
                    @Override
                    public void onClick() {
                        showChangelog(false, false);
                    }
                })
                .build());

        appCardBuilder.addItem(ConvenienceBuilder.createRateActionItem(c,
                getResources().getDrawable(R.drawable.ic_about_rate),
                getString(R.string.about_rate),
                null
        ));

        appCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text(R.string.about_help_translate_title)
                .icon(R.drawable.ic_about_help_translate)
                .setOnClickAction(ConvenienceBuilder.createWebsiteOnClickAction(c, Uri.parse("https://osij6hx.oneskyapp.com/collaboration/project?id=28259")))
                .build());

        MaterialAboutCard.Builder authorCardBuilder = new MaterialAboutCard.Builder();
        authorCardBuilder.title(getString(R.string.about_project_lead_title));

        authorCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text(getString(R.string.about_developer_trife))
                .subText(getString(R.string.about_developer_trife_location))
                .icon(R.drawable.ic_pref_profile_person)
                .build());

        authorCardBuilder.addItem(ConvenienceBuilder.createEmailItem(c,
                getResources().getDrawable(R.drawable.ic_about_email),
                getString(R.string.about_email_title),
                true,
                getString(R.string.about_developer_trife_email),
                "Field Book Question"));

        authorCardBuilder.addItem(ConvenienceBuilder.createWebsiteActionItem(c,
                getResources().getDrawable(R.drawable.ic_about_website),
                "PhenoApps.org",
                false,
                Uri.parse("http://phenoapps.org/")));

        MaterialAboutCard.Builder contributorsCardBuilder = new MaterialAboutCard.Builder();
        contributorsCardBuilder.title(getString(R.string.about_contributors_title));

        contributorsCardBuilder.addItem(ConvenienceBuilder.createWebsiteActionItem(c,
                getResources().getDrawable(R.drawable.ic_about_contributors),
                getString(R.string.about_contributors_developers_title),
                false,
                Uri.parse("https://github.com/PhenoApps/Field-Book/graphs/contributors")));

        contributorsCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text(getString(R.string.about_translators_title))
                .subText(getString(R.string.about_translators_text))
                .icon(R.drawable.ic_about_translators)
                .build());

        contributorsCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text(getString(R.string.about_contributors_funding_title))
                .subText(getString(R.string.about_contributors_funding_text))
                .icon(R.drawable.ic_about_funding)
                .build());

        MaterialAboutCard.Builder technicalCardBuilder = new MaterialAboutCard.Builder();
        technicalCardBuilder.title(getString(R.string.about_technical_title));

        technicalCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text(R.string.about_github_title)
                .icon(R.drawable.ic_about_github)
                .setOnClickAction(ConvenienceBuilder.createWebsiteOnClickAction(c, Uri.parse("https://github.com/PhenoApps/Field-Book")))
                .build());

        String theme = PreferenceManager.getDefaultSharedPreferences(this)
                .getString(GeneralKeys.THEME, "0");

        int styleId = R.style.AboutLibrariesCustom;
        switch (theme) {
            case "2":
                styleId = R.style.AboutLibrariesCustom_Blue;
                break;
            case "1":
                styleId = R.style.AboutLibrariesCustom_HighContrast;
                break;
        }
        final int libStyleId = styleId;

        technicalCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text(R.string.libraries_title)
                .icon(R.drawable.ic_about_libraries)
                .setOnClickAction(new MaterialAboutItemOnClickAction() {
                    @Override
                    public void onClick() {
                        new LibsBuilder()
                                .withActivityTheme(libStyleId)
                                .withAutoDetect(true)
                                .withActivityTitle(getString(R.string.libraries_title))
                                .withLicenseShown(true)
                                .withVersionShown(true)
                                .start(getApplicationContext());
                    }
                })
                .build());

        MaterialAboutCard.Builder otherAppsCardBuilder = new MaterialAboutCard.Builder();
        otherAppsCardBuilder.title(getString(R.string.about_title_other_apps));

        otherAppsCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text("Coordinate")
                .icon(R.drawable.other_ic_coordinate)
                .setOnClickAction(openAppOrStore("org.wheatgenetics.coordinate", c))
                .build());

        otherAppsCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text("Inventory")
                .icon(R.drawable.other_ic_inventory)
                .setOnClickAction(openAppOrStore("org.wheatgenetics.inventory", c))
                .build());

        otherAppsCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text("Verify")
                .icon(R.drawable.other_ic_verify)
                .build());

        otherAppsCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text("Intercross")
                .icon(R.drawable.other_ic_intercross)
                .setOnClickAction(openAppOrStore("org.phenoapps.intercross", c))
                .build());

        return new MaterialAboutList(appCardBuilder.build(), authorCardBuilder.build(), contributorsCardBuilder.build(), otherAppsCardBuilder.build(), technicalCardBuilder.build());
    }

    private String getCurrentAppVersion() {
        return BuildConfig.VERSION_NAME;
    }

    private void checkForUpdate() {
        String currentVersion = getCurrentAppVersion();
        String owner = "PhenoApps";
        String repo = "Field-Book";
        String apiUrl = "https://api.github.com/repos/" + owner + "/" + repo + "/releases/latest";

        // Make the API call to check for updates
        new AsyncTask<Void, Void, String>() {
            @Override
            protected String doInBackground(Void... params) {
                try {
                    URL url = new URL(apiUrl);
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
                        String downloadUrl = json.getJSONArray("assets").getJSONObject(0).getString("browser_download_url");

                        // Compare the versions to check if a newer version is available
                        boolean isNewVersionAvailable = isNewerVersion(currentVersion, latestVersion);

                        // Update the UI based on the version check result
                        showVersionStatus(isNewVersionAvailable, latestVersion, downloadUrl);
                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing JSON response: " + e.getMessage());
                    }
                } else {
                    Log.e(TAG, "Failed to retrieve version information from GitHub");
                }
            }
        }.execute();
    }


    private void showVersionStatus(boolean isNewVersionAvailable, @Nullable String latestVersion, @Nullable String downloadUrl) {
        circularProgressDrawable.stop();
        if (updateCheckItem == null) {
            // The "Updates" action item was not added to the card, something went wrong
            return;
        }

        if (isNewVersionAvailable) {
            updateCheckItem.setText(getString(R.string.found_updates_title));
            updateCheckItem.setSubText(latestVersion);
            updateCheckItem.setIcon(getResources().getDrawable(R.drawable.ic_about_get_update));

            // Set the onClickAction to open the browser with the release URL
            updateCheckItem.setOnClickAction(new MaterialAboutItemOnClickAction() {
                @Override
                public void onClick() {
                    if (downloadUrl != null) {
                        Intent browserIntent = new Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl));
                        startActivity(browserIntent);
                    }
                }
            });
        } else {
            updateCheckItem.setText(getString(R.string.no_updates_title));
            updateCheckItem.setIcon(getResources().getDrawable(R.drawable.ic_about_up_to_date));
            updateCheckItem.setOnClickAction(null);
        }
        refreshMaterialAboutList();
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

    private void showChangelog(Boolean managedShow, Boolean rateButton) {
        ChangelogDialogFragment builder = new ChangelogBuilder()
                .withUseBulletList(true) // true if you want to show bullets before each changelog row, false otherwise
                .withManagedShowOnStart(managedShow)  // library will take care to show activity/dialog only if the changelog has new infos and will only show this new infos
                .withRateButton(rateButton) // enable this to show a "rate app" button in the dialog => clicking it will open the play store; the parent activity or target fragment can also implement IChangelogRateHandler to handle the button click
                .withSummary(false, true) // enable this to show a summary and a "show more" button, the second paramter describes if releases without summary items should be shown expanded or not
                .withTitle(getString(R.string.changelog_title)) // provide a custom title if desired, default one is "Changelog <VERSION>"
                .withOkButtonLabel(getString(android.R.string.ok)) // provide a custom ok button text if desired, default one is "OK"
                .withSorter(new ImportanceChangelogSorter())
                .buildAndShowDialog(this, false); // second parameter defines, if the dialog has a dark or light theme
    }

    @Override
    protected CharSequence getActivityTitle() {
        return getString(R.string.mal_title_about);
    }


    private MaterialAboutItemOnClickAction openAppOrStore(String packageName, Context c) {
        PackageManager packageManager = getBaseContext().getPackageManager();
        try {
            packageManager.getPackageInfo(packageName, 0);

            return () -> {
                Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
                startActivity(launchIntent);
            };
        } catch (PackageManager.NameNotFoundException e) {
            return ConvenienceBuilder.createWebsiteOnClickAction(c, Uri.parse("https://play.google.com/store/apps/details?id=" + packageName));
        }
    }
}
