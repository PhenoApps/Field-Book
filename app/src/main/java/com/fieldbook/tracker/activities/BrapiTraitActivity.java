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
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.fieldbook.tracker.brapi.service.BrAPIService;
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory;
import com.fieldbook.tracker.brapi.service.BrapiPaginationManager;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.utilities.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import com.fieldbook.tracker.objects.TraitObject;

public class BrapiTraitActivity extends AppCompatActivity {

    private BrAPIService brAPIService;
    private List<TraitObject> selectedTraits;
    private BrapiPaginationManager paginationManager;

    @Override
    public void onDestroy() {
        super.onDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        brAPIService.authorizeClient();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Load the traits from breedbase if user is connected to the internet
        if (Utils.isConnected(this)) {
            if (BrAPIService.hasValidBaseUrl(this)) {
                setContentView(R.layout.activity_traits_brapi);
                paginationManager = new BrapiPaginationManager(this);

                loadToolbar();
                // Get the setting information for our brapi integration
                brAPIService = BrAPIServiceFactory.getBrAPIService(this);

                // Make a clean list to track our selected traits
                selectedTraits = new ArrayList<>();

                // Set the url on our interface
                String brapiBaseURL = BrAPIService.getBrapiUrl(this);
                TextView baseURLText = findViewById(R.id.brapiBaseUrl);
                baseURLText.setText(brapiBaseURL);

                loadTraitsList();
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

    // Load the traits from server
    public void loadTraitsList() {

        // Get our UI elements for the list of traits
        final ListView traitList = findViewById(R.id.brapiTraits);
        traitList.setVisibility(View.GONE);

        // Show our progress bar
        findViewById(R.id.loadingPanel).setVisibility(View.VISIBLE);

        //init page numbers
        paginationManager.refreshPageIndicator();

        // Call our API to get the data
        brAPIService.getOntology(paginationManager, new BiFunction<List<TraitObject>, Integer, Void>() {
            @Override
            public Void apply(final List<TraitObject> traits, Integer variablesMissingTrait) {

                (BrapiTraitActivity.this).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {

                            if (variablesMissingTrait > 0) {
                                Toast.makeText(getApplicationContext(),
                                        getString(R.string.brapi_skipped_traits, variablesMissingTrait),
                                        Toast.LENGTH_LONG).show();
                            }

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
                });
                return null;
            }

        }, new Function<Integer, Void>() {
            @Override
            public Void apply(final Integer code) {
                (BrapiTraitActivity.this).runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        // Show error message. We don't finish the activity intentionally.
                        if(BrAPIService.isConnectionError(code)){
                            BrAPIService.handleConnectionError(BrapiTraitActivity.this, code);
                        }else {
                            Toast.makeText(getApplicationContext(), getString(R.string.brapi_ontology_error), Toast.LENGTH_LONG).show();
                        }
                        findViewById(R.id.loadingPanel).setVisibility(View.GONE);
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
            if(trait.getTrait() != null)
                itemDataList.add(trait.getTrait());
            else
                itemDataList.add(trait.getId());
        }

        ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_multiple_choice, itemDataList);

        return arrayAdapter;
    }

    // Button event for load and save traits
    public void buttonClicked(View view) {
        switch (view.getId()) {
            case R.id.loadTraits:
                // Start from beginning
                paginationManager.reset();
                loadTraitsList();
                break;

            case R.id.save:
                // Save the selected traits
                String saveMessage = saveTraits();

                setResult(RESULT_OK);
                // navigate back to our traits list page
                finish();
                Toast.makeText(this, saveMessage, Toast.LENGTH_LONG).show();
                break;
            case R.id.prev:
            case R.id.next:
                // Update current page (if allowed) and start brapi call.
                paginationManager.setNewPage(view.getId());
                loadTraitsList();
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
        int pos = ConfigActivity.dt.getMaxPositionFromTraits() + 1;
        for (int i = 0; i < selectedTraits.size(); ++i) {

            TraitObject trait = selectedTraits.get(i);

            TraitObject existingTraitByName = ConfigActivity.dt.getTraitByName(trait.getTrait());
            TraitObject existingTraitByExId = ConfigActivity.dt.getTraitByExternalDbId(trait.getExternalDbId(), trait.getTraitDataSource());
            // Check if the trait already exists
            if (existingTraitByName != null) {
                secondaryMessage = getResources().getString(R.string.brapi_trait_already_exists, trait.getTrait());
                // Skip this one, continue on.
                continue;
            }else if (existingTraitByExId != null) {
                // Update existing trait
                trait.setId(existingTraitByExId.getId());
                long saveStatus = ConfigActivity.dt.updateTrait(trait);
                successfulSaves += saveStatus == -1 ? 0 : 1;
            }else{
                // Insert our new trait
                trait.setRealPosition(pos + i);
                long saveStatus = ConfigActivity.dt.insertTraits(trait);
                successfulSaves += saveStatus == -1 ? 0 : 1;
            }
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

}