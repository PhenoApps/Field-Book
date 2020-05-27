package com.fieldbook.tracker.activities;

import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.arch.core.util.Function;

import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.fieldbook.tracker.brapi.BrAPIService;
import com.fieldbook.tracker.brapi.BrapiListResponse;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.utilities.Utils;

import java.util.ArrayList;
import java.util.List;

import com.fieldbook.tracker.objects.TraitObject;

import io.swagger.client.ApiException;
import io.swagger.client.model.Metadata;

public class BrapiTraitActivity extends AppCompatActivity {

    private BrAPIService brAPIService;
    private List<TraitObject> selectedTraits;
    private Integer currentPage = 0;
    private Integer totalPages = 1;
    private Integer resultsPerPage = 15;
    private Button nextBtn;
    private Button prevBtn;

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the traits from breedbase if user is connected to the internet
        if (Utils.isConnected(this)) {
            if (brAPIService.hasValidBaseUrl(this)) {
                setContentView(R.layout.activity_traits_brapi);

                // Make our prev and next buttons invisible
                nextBtn = findViewById(R.id.next);
                prevBtn = findViewById(R.id.prev);

                // Initially make next and prev gone until we know there are more than 1 page.
                nextBtn.setVisibility(View.INVISIBLE);
                prevBtn.setVisibility(View.INVISIBLE);

                loadToolbar();
                // Get the setting information for our brapi integration
                String brapiBaseURL = BrAPIService.getBrapiUrl(this);
                brAPIService = new BrAPIService(brapiBaseURL, new DataHelper(this));

                // Make a clean list to track our selected traits
                selectedTraits = new ArrayList<>();

                // Set the url on our interface
                TextView baseURLText = findViewById(R.id.brapiBaseUrl);
                baseURLText.setText(brapiBaseURL);

                loadTraitsList(BrapiTraitActivity.this.currentPage, BrapiTraitActivity.this.resultsPerPage);
            } else {
                Toast.makeText(getApplicationContext(), R.string.brapi_must_configure_url, Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            // Check if the user is connected. If not, pull from cache
            Toast.makeText(getApplicationContext(), R.string.device_offline_warning, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // Load the toolbar with various elements
    private void loadToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(null);
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }
    }

    // Load the traits from breedbase
    public void loadTraitsList(final Integer page, Integer pageSize) {

        // Get our UI elements for the list of traits
        final ListView traitList = findViewById(R.id.brapiTraits);
        traitList.setVisibility(View.GONE);

        // Show our progress bar
        findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);

        TextView pageIndicator = findViewById(R.id.page_indicator);
        pageIndicator.setText(String.format("Page %d of %d", page + 1, BrapiTraitActivity.this.totalPages));

        // Determine our button visibility. Not necessary if we only have 1 page.
        determineBtnVisibility();

        // Call our API to get the data
        brAPIService.getOntology(BrAPIService.getBrapiToken(this), page, pageSize, new Function<BrapiListResponse<TraitObject>, Void>() {
            @Override
            public Void apply(final BrapiListResponse<TraitObject> input) {

                (BrapiTraitActivity.this).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Cancel processing if the page that was processed is not the page
                        // that we are currently on.
                        if (page == BrapiTraitActivity.this.currentPage) {

                            final List<TraitObject> traits = input.getData();

                            // Update the total pages
                            final Metadata metadata = input.getMetadata();
                            BrapiTraitActivity.this.totalPages = metadata.getPagination().getTotalPages();
                            TextView pageIndicator = findViewById(R.id.page_indicator);
                            pageIndicator.setText(String.format("Page %d of %d", BrapiTraitActivity.this.currentPage + 1,
                                    BrapiTraitActivity.this.totalPages));

                            determineBtnVisibility();

                            // Build our array adapter
                            traitList.setAdapter(BrapiTraitActivity.this.buildTraitsArrayAdapter(traits));

                            // Check to see if any of the traits are selected traits
                            for (Integer i = 0; i < traits.size(); i++) {

                                TraitObject trait = traits.get(i);

                                // Check to see if it is a selected trait
                                for (TraitObject selectedTrait : selectedTraits) {

                                    if (trait.getTrait().equals(selectedTrait.getTrait())) {
                                        traitList.setItemChecked(i, true);
                                        break;
                                    }
                                }
                            }

                            // Set our on click listener for each item
                            traitList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                                @Override
                                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                                    if (traitList.isItemChecked(position)) {
                                        // It is checked now, it wasn't before
                                        selectedTraits.add(traits.get(position));
                                    } else {
                                        // It was checked before, remove from selection
                                        TraitObject trait = traits.get(position);

                                        for (TraitObject selectedTrait : selectedTraits) {

                                            if (trait.getTrait().equals(selectedTrait.getTrait())) {
                                                selectedTraits.remove(selectedTrait);
                                                break;
                                            }
                                        }
                                    }
                                }
                            });

                            traitList.setVisibility(View.VISIBLE);

                            findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                        }
                    }
                });
                return null;
            }

        }, new Function<ApiException, Void>() {
            @Override
            public Void apply(final ApiException input) {
                (BrapiTraitActivity.this).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Display error message but don't finish the activity.
                        findViewById(R.id.loadingPanel).setVisibility(View.GONE);
                        Toast.makeText(getApplicationContext(), R.string.brapi_ontology_error, Toast.LENGTH_LONG).show();
                    }
                });

                return null;
            }
        });
    }

    // Transforms the trait data to display it on the screen.
    private ArrayAdapter buildTraitsArrayAdapter(List<TraitObject> traits) {

        ArrayList<String> itemDataList = new ArrayList<>();

        for (TraitObject trait : traits) {

            itemDataList.add(trait.getTrait());
        }

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, itemDataList);

        return arrayAdapter;
    }

    // Button event for load and save traits
    public void buttonClicked(View view) {
        switch (view.getId()) {
            case R.id.loadTraits:
                // Start from beginning
                nextBtn.setVisibility(View.INVISIBLE);
                prevBtn.setVisibility(View.INVISIBLE);

                BrapiTraitActivity.this.currentPage = 0;
                loadTraitsList(BrapiTraitActivity.this.currentPage, BrapiTraitActivity.this.resultsPerPage);
                break;

            case R.id.save:

                // Save the selected traits
                String saveMessage = saveTraits();

                // navigate back to our traits list page
                finish();
                Toast.makeText(this, saveMessage, Toast.LENGTH_LONG).show();
                break;
            case R.id.prev:

                // Query the previous page of traits
                Integer prevPage = BrapiTraitActivity.this.currentPage - 1;

                if (prevPage >= 0) {
                    // We are allowed to change pages. Update current page and start brapi call.
                    BrapiTraitActivity.this.currentPage = prevPage;
                    loadTraitsList(prevPage, BrapiTraitActivity.this.resultsPerPage);

                }

                break;

            case R.id.next:

                // Query the next page of traits
                Integer nextPage = BrapiTraitActivity.this.currentPage + 1;
                Integer totalPages = BrapiTraitActivity.this.totalPages;

                if (nextPage < totalPages) {
                    // We are allowed to change pages. Update current page and start brapi call.
                    BrapiTraitActivity.this.currentPage = nextPage;
                    loadTraitsList(nextPage, BrapiTraitActivity.this.resultsPerPage);

                }
                break;
        }
    }

    // Save our select traits
    public String saveTraits() {

        // Check if there are any traits selected
        if (selectedTraits.size() == 0) {
            return "No traits are selected";
        }

        Integer totalTraits = selectedTraits.size();
        Integer successfulSaves = 0;
        String secondaryMessage = "";
        // For now, only give the ability to create new variables
        // Determine later if the need to edit existing variables is needed.
        for (int i = 0; i < selectedTraits.size(); ++i) {

            TraitObject trait = selectedTraits.get(i);

            // Check if the trait already exists
            if (ConfigActivity.dt.hasTrait(trait.getTrait())) {
                secondaryMessage = getResources().getString(R.string.brapi_trait_already_exists, trait.getTrait());
                // Skip this one, continue on.
                continue;
            }

            // Insert our new trait
            long saveStatus = ConfigActivity.dt.insertTraits(trait);

            successfulSaves += saveStatus == -1 ? 0 : 1;

        }

        SharedPreferences ep = getSharedPreferences("Settings", 0);
        SharedPreferences.Editor ed = ep.edit();
        ed.putBoolean("CreateTraitFinished", true);
        ed.putBoolean("TraitsExported", false);
        ed.apply();

        CollectActivity.reloadData = true;

        // Check how successful we were at saving our traits.
        if (successfulSaves == 0) {
            return secondaryMessage != "" ? secondaryMessage : getResources().getString(R.string.brapi_error_saving_all_traits);
        } else if (successfulSaves < totalTraits) {
            return secondaryMessage != "" ? secondaryMessage : getResources().getString(R.string.brapi_error_saving_some_traits);
        }

        // Check if we had a secondary message to show.
        // Current this secondaryMessage will always be empty for the success message.
        // Putting selection for secondaryMessage in here now for potential future use with
        // more detailed success and failure messages. 
        return secondaryMessage != "" ? secondaryMessage : getResources().getString(R.string.brapi_traits_saved_success);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    public void determineBtnVisibility() {

        if (currentPage == 0) {
            prevBtn.setVisibility(View.INVISIBLE);
        } else {
            prevBtn.setVisibility(View.VISIBLE);
        }

        // Determine what buttons should be visible
        if (currentPage == (totalPages - 1)) {
            nextBtn.setVisibility(View.INVISIBLE);
        } else {
            nextBtn.setVisibility(View.VISIBLE);
        }

    }
}