package com.fieldbook.tracker.activities;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

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
import com.fieldbook.tracker.provider.GenericFileProvider;
import com.fieldbook.tracker.utilities.VersionChecker;
import com.fieldbook.tracker.utilities.VersionCheckerListener;
import com.michaelflisar.changelog.ChangelogBuilder;
import com.michaelflisar.changelog.classes.ImportanceChangelogSorter;
import com.michaelflisar.changelog.internal.ChangelogDialogFragment;
import com.mikepenz.aboutlibraries.LibsBuilder;

import java.io.File;
import javax.annotation.Nullable;

public class AboutActivity extends MaterialAboutActivity implements VersionCheckerListener {
    //todo move to fragments so aboutactivity can extend base activity

    private static final int REQUEST_CODE_OPEN_DIRECTORY = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ThemedActivity.Companion.applyTheme(this);
        super.onCreate(savedInstanceState);
    }

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

        appCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text(getString(R.string.updates_title))
                .icon(R.drawable.ic_about_get_update)
                .setOnClickAction(new MaterialAboutItemOnClickAction() {
                    @Override
                    public void onClick() {
                        checkForUpdate();
                    }
                })
                .build());

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
        VersionChecker versionChecker = new VersionChecker(AboutActivity.this, currentVersion, "PhenoApps", "Field-Book");
        versionChecker.setListener(this); // Register the listener
        versionChecker.execute();
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

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_OPEN_DIRECTORY && resultCode == RESULT_OK && data != null) {
            Uri treeUri = data.getData();
            // Handle the selected directory URI (treeUri) here
        }
    }

    @Override
    public void onApkDownloaded(File apkFile) {
        Log.d("onApkDownload", "callback triggered, handling apk in AboutActivity");

        AlertDialog.Builder builder = new AlertDialog.Builder(this, R.style.AlertDialogStyle);
        builder.setTitle("Download Completed");
        builder.setMessage("The update file has been successfully downloaded to the updates directory.\n\nOpen the directory and double-click the APK file to install it.");

        builder.setPositiveButton("Open Directory", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // Open the directory where the APK was saved
                openDownloadDirectory(apkFile);
                dialog.dismiss();
            }
        });

        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builder.show();
    }

    private void openDownloadDirectory(File apkFile) {
        Context context = this;
        if (context != null) {
            File parentDirectory = apkFile.getParentFile();
            if (parentDirectory != null) {
                Uri contentUri = GenericFileProvider.getUriForFile(context, "com.fieldbook.tracker.fileprovider", parentDirectory);
                Log.d("URI_DEBUG", "URI: " + contentUri.toString());
                Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
                intent.setDataAndType(contentUri, "*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                if (intent.resolveActivity(context.getPackageManager()) != null) {
                    context.startActivity(intent);
                }
            } else {
                Log.e("URI_DEBUG", "Parent directory is null");
            }
        }
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
