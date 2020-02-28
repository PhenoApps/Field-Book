package com.fieldbook.tracker;

import android.content.Context;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;

import com.danielstone.materialaboutlibrary.ConvenienceBuilder;
import com.danielstone.materialaboutlibrary.MaterialAboutActivity;
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem;
import com.danielstone.materialaboutlibrary.items.MaterialAboutItemOnClickAction;
import com.danielstone.materialaboutlibrary.items.MaterialAboutTitleItem;
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard;
import com.danielstone.materialaboutlibrary.model.MaterialAboutList;
import com.michaelflisar.changelog.ChangelogBuilder;
import com.michaelflisar.changelog.classes.ImportanceChangelogSorter;
import com.michaelflisar.changelog.internal.ChangelogDialogFragment;
import com.mikepenz.aboutlibraries.LibsBuilder;

public class AboutActivity extends MaterialAboutActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    @NonNull
    public MaterialAboutList getMaterialAboutList(@NonNull Context c) {
        MaterialAboutCard.Builder appCardBuilder = new MaterialAboutCard.Builder();

        // Add items to card
        int colorIcon = R.color.mal_color_icon_light_theme;

        appCardBuilder.addItem(new MaterialAboutTitleItem.Builder()
                .text(getString(R.string.field_book))
                .icon(R.mipmap.ic_launcher)
                .build());

        appCardBuilder.addItem(ConvenienceBuilder.createVersionActionItem(c,
                getResources().getDrawable(R.drawable.ic_about_info),
                getString(R.string.about_version_title),
                false));

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

        appCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text("GitHub")
                .icon(R.drawable.ic_about_github)
                .setOnClickAction(ConvenienceBuilder.createWebsiteOnClickAction(c, Uri.parse("https://github.com/PhenoApps/Field-Book")))
                .build());

        appCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text(R.string.libraries_title)
                .icon(R.drawable.ic_about_libraries)
                .setOnClickAction(new MaterialAboutItemOnClickAction() {
                    @Override
                    public void onClick() {
                        new LibsBuilder()
                                .withActivityTheme(R.style.AppTheme)
                                .withAutoDetect(true)
                                .withActivityTitle(getString(R.string.libraries_title))
                                .withLicenseShown(true)
                                .withVersionShown(true)
                                .start(getApplicationContext());
                    }
                })
                .build());

        appCardBuilder.addItem(ConvenienceBuilder.createRateActionItem(c,
                getResources().getDrawable(R.drawable.ic_about_rate),
                getString(R.string.about_rate),
                null
        ));

        MaterialAboutCard.Builder authorCardBuilder = new MaterialAboutCard.Builder();
        authorCardBuilder.title(getString(R.string.about_project_lead_title));

        authorCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text(getString(R.string.about_developer_trife))
                .subText(getString(R.string.about_developer_trife_location))
                .icon(R.drawable.ic_nav_drawer_person)
                .build());

        authorCardBuilder.addItem(ConvenienceBuilder.createEmailItem(c,
                getResources().getDrawable(R.drawable.ic_about_email),
                "Send an email",
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
                .subText(getString(R.string.about_translators_list))
                .icon(R.drawable.ic_about_translators)
                .build());


        MaterialAboutCard.Builder otherAppsCardBuilder = new MaterialAboutCard.Builder();
        otherAppsCardBuilder.title(getString(R.string.about_title_other_apps));

        otherAppsCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text("Coordinate")
                .icon(R.drawable.other_ic_coordinate)
                .setOnClickAction(ConvenienceBuilder.createWebsiteOnClickAction(c, Uri.parse("https://play.google.com/store/apps/details?id=org.wheatgenetics.coordinate")))
                .build());

        otherAppsCardBuilder.addItem(new MaterialAboutActionItem.Builder()
                .text("Inventory")
                .icon(R.drawable.other_ic_inventory)
                .setOnClickAction(ConvenienceBuilder.createWebsiteOnClickAction(c, Uri.parse("https://play.google.com/store/apps/details?id=org.wheatgenetics.inventory")))
                .build());

        return new MaterialAboutList(appCardBuilder.build(), authorCardBuilder.build(), contributorsCardBuilder.build(), otherAppsCardBuilder.build());

    }

    private void showChangelog(Boolean managedShow, Boolean rateButton) {
        ChangelogDialogFragment builder = new ChangelogBuilder()
                .withUseBulletList(true) // true if you want to show bullets before each changelog row, false otherwise
                .withManagedShowOnStart(managedShow)  // library will take care to show activity/dialog only if the changelog has new infos and will only show this new infos
                .withRateButton(rateButton) // enable this to show a "rate app" button in the dialog => clicking it will open the play store; the parent activity or target fragment can also implement IChangelogRateHandler to handle the button click
                .withSummary(false, true) // enable this to show a summary and a "show more" button, the second paramter describes if releases without summary items should be shown expanded or not
                .withTitle(getString(R.string.changelog_title)) // provide a custom title if desired, default one is "Changelog <VERSION>"
                .withOkButtonLabel("OK") // provide a custom ok button text if desired, default one is "OK"
                .withSorter(new ImportanceChangelogSorter())
                .buildAndShowDialog(this, false); // second parameter defines, if the dialog has a dark or light theme
    }

    @Override
    protected CharSequence getActivityTitle() {

        return getString(R.string.mal_title_about);
    }

}