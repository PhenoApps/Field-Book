package com.fieldbook.tracker.brapi;

import android.app.Activity;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.arch.core.util.Function;

import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.fieldbook.tracker.ConfigActivity;
import com.fieldbook.tracker.DataHelper;
import com.fieldbook.tracker.MainActivity;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.preferences.PreferencesActivity;
import com.fieldbook.tracker.traits.TraitEditorActivity;
import com.fieldbook.tracker.utilities.Utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.fieldbook.tracker.traits.TraitObject;

import io.swagger.client.ApiException;
import io.swagger.client.model.Metadata;


public class BrapiTraitActivity extends AppCompatActivity {

    private SharedPreferences preferences;
    private BrAPIService brAPIService;
    private List<TraitObject> selectedTraits;
    private Integer currentPage = 0;
    private Integer totalPages = 1;
    private Integer resultsPerPage = 3;

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
        if(Utils.isConnected(this)) {
            if (brAPIService.hasValidBaseUrl(this)) {
                setContentView(R.layout.activity_traits_brapi);

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
            }else{
                Toast.makeText(getApplicationContext(), R.string.brapi_must_configure_url, Toast.LENGTH_SHORT).show();
                finish();
            }
        }else{
            // Check if the user is connected. If not, pull from cache
            Toast.makeText(getApplicationContext(), R.string.device_offline_warning, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    // Load the toolbar with various elements
    private void loadToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        getSupportActionBar().setTitle(null);
        getSupportActionBar().getThemedContext();
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        getSupportActionBar().setHomeButtonEnabled(true);
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


        // Call our API to get the data
        brAPIService.getOntology(page, pageSize, new Function<BrapiListResponse<TraitObject>, Void>() {
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

                            // Clear our selected traits
                            selectedTraits = new ArrayList<>();

                            // Build our array adapter
                            traitList.setAdapter(BrapiTraitActivity.this.buildTraitsArrayAdapter(traits));

                            // Set our page numbers

                            // Set our on click listener for each item

                            traitList.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                                @Override
                                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {

                                    if (traitList.isItemChecked(position)) {
                                        // It is checked now, it wasn't before
                                        selectedTraits.add(traits.get(position));
                                    } else {
                                        // It was checked before, remove from selection
                                        selectedTraits.remove(traits.get(position));
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

        ArrayList<String> itemDataList = new ArrayList<>();;

        for(TraitObject trait: traits) {
            itemDataList.add(trait.getTrait());
        }

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter(this, android.R.layout.simple_list_item_multiple_choice, itemDataList);

        return arrayAdapter;
    }


    // Button event for load and save traits
    public void buttonClicked(View view) {
        switch(view.getId()) {
            case R.id.loadTraits:
                // Start from beginning
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
                BrapiTraitActivity.this.currentPage = prevPage;
                if (prevPage >= 0) { loadTraitsList(prevPage, BrapiTraitActivity.this.resultsPerPage); }
                break;

            case R.id.next:

                // Query the next page of traits
                Integer nextPage = BrapiTraitActivity.this.currentPage + 1;
                Integer totalPages = BrapiTraitActivity.this.totalPages;
                BrapiTraitActivity.this.currentPage = nextPage;
                if (nextPage < totalPages) { loadTraitsList(nextPage, BrapiTraitActivity.this.resultsPerPage); }
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
                secondaryMessage = "Trait already exists: " + trait.getTrait();
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

        MainActivity.reloadData = true;

        // Check how successful we were at saving our traits.
        if (successfulSaves == 0) {
            return "Error saving traits. No traits were saved";
        }
        else if (successfulSaves < totalTraits) {
            return "Error saving some traits. Some traits were not saved";
        }

        // Check if we had a secondary message to show
        return secondaryMessage != "" ? secondaryMessage : "Selected traits saved successfully.";

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

}
