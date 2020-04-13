package com.fieldbook.tracker.activities;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.arch.core.util.Function;

import com.fieldbook.tracker.brapi.BrAPIService;
import com.fieldbook.tracker.brapi.BrapiAuthDialog;
import com.fieldbook.tracker.brapi.BrapiControllerResponse;
import com.fieldbook.tracker.brapi.Observation;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.utilities.Utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import io.swagger.client.model.NewObservationDbIdsObservations;
import io.swagger.client.model.Image;

public class BrapiExportActivity extends AppCompatActivity {

    private BrAPIService brAPIService;
    private DataHelper dataHelper;
    private List<Observation> observationsNeedingSync;
    private List<com.fieldbook.tracker.brapi.Image> imagesNew;
    private List<com.fieldbook.tracker.brapi.Image> imagesEditedIncomplete;

    private int postImageMetaDataUpdatesCount;
    private int putImageContentUpdatesCount;
    private int putImageMetaDataUpdatesCount;

    private Boolean observationsComplete;

    private int numNewObservations;
    private int numSyncedObservations;
    private int numEditedObservations;
    private int numNewImages;
    private int numSyncedImages;
    private int numEditedImages;
    private int numIncompleteImages;
    private UploadError putObservationsError;
    private UploadError postImageMetaDataError;
    private UploadError putImageContentError;
    private UploadError putImageMetaDataError;

    public BrapiExportActivity() {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Utils.isConnected(this)) {
            if (BrAPIService.hasValidBaseUrl(this)) {

                requestWindowFeature(Window.FEATURE_NO_TITLE);
                setContentView(R.layout.dialog_brapi_export);

                String brapiBaseURL = BrAPIService.getBrapiUrl(this);

                this.dataHelper = new DataHelper(this);
                brAPIService = new BrAPIService(brapiBaseURL, this.dataHelper);

                putObservationsError = UploadError.NONE;
                postImageMetaDataError = UploadError.NONE;
                putImageContentError = UploadError.NONE;
                putImageMetaDataError = UploadError.NONE;

                postImageMetaDataUpdatesCount = 0;
                putImageContentUpdatesCount = 0;
                putImageMetaDataUpdatesCount = 0;

                observationsNeedingSync = new ArrayList<>();
                numNewObservations = 0;
                numSyncedObservations = 0;
                numEditedObservations = 0;
                imagesNew = new ArrayList<>();
                imagesEditedIncomplete = new ArrayList<>();
                numNewImages = 0;
                numSyncedImages = 0;
                numEditedImages = 0;
                numIncompleteImages = 0;
                observationsComplete = false;

                loadToolbar();
                loadStatistics();

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

    private void loadToolbar() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        if(getSupportActionBar() != null){
            getSupportActionBar().setTitle("BrAPI Export");
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Check out brapi auth
        BrapiControllerResponse brapiControllerResponse = BrAPIService.checkBrapiAuth(this);

        // Check whether our brapi auth response was exists or was successful
        processBrapiControllerMessage(brapiControllerResponse);

    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        // We will arrive at this function after our deep link when starting the auth
        // process from this page.

        // Set our intent on resume so we get the deep link info.
        setIntent(intent);

    }

    public void processBrapiControllerMessage(BrapiControllerResponse brapiControllerResponse) {

        if (brapiControllerResponse.status != null) {
            if (!brapiControllerResponse.status) {
                Toast.makeText(this, R.string.brapi_auth_error_starting, Toast.LENGTH_LONG).show();
            } else {
                Toast.makeText(this, R.string.brapi_auth_success, Toast.LENGTH_LONG).show();
            }
        }
    }

    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.brapi_export_btn:
                if (numNewObservations == 0 && numEditedObservations == 0 &&
                        numNewImages == 0 && numEditedImages == 0 && numIncompleteImages == 0) {
                    Toast.makeText(this, "Error: Nothing to sync", Toast.LENGTH_SHORT).show();
                } else {
                    showSaving();
                    sendData();
                }
                break;
            case R.id.brapi_cancel_btn:
                finish();
                break;
            default:
                break;
        }
    }

    private void sendData() {

        AsyncTask.execute(new Runnable() {
            @Override
            public void run() {
                if (numNewObservations > 0 || numEditedObservations > 0) {
                    putObservations();
                } else if (numNewObservations == 0 && numEditedObservations == 0) {
                    observationsComplete = true;
                }
                if (numNewImages > 0) {
                    loadNewImages();
                    postImages();
                }
                if (numEditedImages > 0 || numIncompleteImages > 0) {
                    loadEditedIncompleteImages();
                    putImages();
                }
            }
        });
    }

    private void loadNewImages() {
        for (com.fieldbook.tracker.brapi.Image image : imagesNew) {
            image.loadImage();
        }
    }

    private void loadEditedIncompleteImages() {
        for (com.fieldbook.tracker.brapi.Image image : imagesEditedIncomplete) {
            image.loadImage();
        }
    }

    private void showSaving() {
        // Disable the export button so that can't click it again
        Button exportBtn = this.findViewById(R.id.brapi_export_btn);
        exportBtn.setEnabled(false);
        // Show our saving wheel
        this.findViewById(R.id.saving_panel).setVisibility(View.VISIBLE);
    }

    private void hideSaving() {
        // Re-enable our login button
        // Disable the export button so that can't click it again
        Button exportBtn = this.findViewById(R.id.brapi_export_btn);
        exportBtn.setEnabled(true);
        this.findViewById(R.id.saving_panel).setVisibility(View.GONE);
    }

    private void putObservations() {

        brAPIService.putObservations(observationsNeedingSync,
                BrAPIService.getBrapiToken(this),
                new Function<List<NewObservationDbIdsObservations>, Void>() {
                    @Override
                    public Void apply(final List<NewObservationDbIdsObservations> observationDbIds) {

                        (BrapiExportActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                processPutObservationsResponse(observationDbIds);
                                observationsComplete = true;
                                uploadComplete();
                            }
                        });
                        return null;
                    }
                }, new Function<Integer, Void>() {

                    @Override
                    public Void apply(final Integer code) {

                        (BrapiExportActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                putObservationsError = processErrorCode(code);
                                observationsComplete = true;
                                uploadComplete();
                            }
                        });

                        return null;
                    }
                }
        );
    }

    private void postImages() {
        for (com.fieldbook.tracker.brapi.Image image : imagesNew) {
            postImage(image);
        }
    }

    private void postImage(final com.fieldbook.tracker.brapi.Image imageData) {

        brAPIService.postImageMetaData(imageData,
                BrAPIService.getBrapiToken(this),
                new Function<Image, Void>() {
                    @Override
                    public Void apply(final Image image) {

                        (BrapiExportActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String fieldBookId = imageData.getFieldBookDbId();
                                processPostImageResponse(image, fieldBookId);
                                postImageMetaDataUpdatesCount++;
                                imageData.setDbId(image.getImageDbId());
                                putImageContent(imageData, imagesNew);
                                uploadComplete();
                            }
                        });
                        return null;
                    }
                }, new Function<Integer, Void>() {

                    @Override
                    public Void apply(final Integer code) {

                        (BrapiExportActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                postImageMetaDataError = processErrorCode(code);
                                postImageMetaDataUpdatesCount++;
                                putImageContentUpdatesCount++; //since we aren't calling this
                                uploadComplete();
                            }
                        });

                        return null;
                    }
                }
        );
    }

    private void putImageContent(final com.fieldbook.tracker.brapi.Image image, final List<com.fieldbook.tracker.brapi.Image> uploads) {

        brAPIService.putImageContent(image, BrAPIService.getBrapiToken(this),
                new Function<Image, Void>() {
                    @Override
                    public Void apply(final Image responseImage) {

                        (BrapiExportActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String fieldBookId = image.getFieldBookDbId();
                                processPutImageContentResponse(responseImage, fieldBookId);
                                putImageContentUpdatesCount++;
                                uploadComplete();
                            }
                        });
                        return null;
                    }
                }, new Function<Integer, Void>() {

                    @Override
                    public Void apply(final Integer code) {

                        (BrapiExportActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                putImageContentError = processErrorCode(code);
                                putImageContentUpdatesCount++;
                                uploadComplete();
                            }
                        });

                        return null;
                    }
                }
        );
    }

    private void putImages() {
        for (com.fieldbook.tracker.brapi.Image image : imagesEditedIncomplete) {
            putImage(image);
        }
    }

    private void putImage(final com.fieldbook.tracker.brapi.Image imageData) {

        brAPIService.putImage(imageData,
                BrAPIService.getBrapiToken(this),
                new Function<Image, Void>() {
                    @Override
                    public Void apply(final Image image) {

                        (BrapiExportActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String fieldBookId = imageData.getFieldBookDbId();
                                processPutImageResponse(image, fieldBookId);
                                putImageMetaDataUpdatesCount++;
                                imageData.setDbId(image.getImageDbId());
                                putImageContent(imageData, imagesEditedIncomplete);
                                uploadComplete();
                            }
                        });
                        return null;
                    }
                }, new Function<Integer, Void>() {

                    @Override
                    public Void apply(final Integer code) {

                        (BrapiExportActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                putImageMetaDataError = processErrorCode(code);
                                putImageMetaDataUpdatesCount++;
                                putImageContentUpdatesCount++; // since we aren't calling this
                                uploadComplete();
                            }
                        });

                        return null;
                    }
                }
        );
    }

    private UploadError processErrorCode(Integer code) {
        UploadError retVal;

        switch (code) {
            case 403:
                // Warn that they do not have permissions to push traits
                retVal = UploadError.API_PERMISSION_ERROR;
                break;
            case 401:
                // Start the login process
                retVal = UploadError.API_UNAUTHORIZED_ERROR;
                break;
            case 400:
                // Bad request
                retVal = UploadError.API_CALLBACK_ERROR;
                break;
            case 404:
                // End point not supported
                retVal = UploadError.API_NOTSUPPORTED_ERROR;
                break;
            default:
                // Do our generic error handler.
                retVal = UploadError.API_CALLBACK_ERROR;
        }

        return retVal;
    }

    private String getMessageForErrorCode(UploadError error) {
        String message;

        switch (error) {
            case API_CALLBACK_ERROR:
                message = getString(R.string.brapi_export_failed);
                break;
            case API_PERMISSION_ERROR:
                message = getString(R.string.brapi_export_permission_deny);
                break;
            case API_NOTSUPPORTED_ERROR:
                message = getString(R.string.brapi_export_not_supported);
                break;
            case WRONG_NUM_OBSERVATIONS_RETURNED:
                message = getString(R.string.brapi_export_wrong_num_obs);
                break;
            case MISSING_OBSERVATION_IN_RESPONSE:
                message = getString(R.string.brapi_export_missing_obs);
                break;
            case MULTIPLE_OBSERVATIONS_PER_VARIABLE:
                message = getString(R.string.brapi_export_multiple_obs);
                break;
            case NONE:
                message = getString(R.string.brapi_export_successful);
                break;
            default:
                message = getString(R.string.brapi_export_unknown_error);
                break;
        }

        return message;
    }

    private void uploadComplete() {

        if (imagesComplete() && observationsComplete()) {
            hideSaving();

            // show upload status
            if (putObservationsError == UploadError.API_UNAUTHORIZED_ERROR ||
                    postImageMetaDataError == UploadError.API_UNAUTHORIZED_ERROR ||
                    putImageContentError == UploadError.API_UNAUTHORIZED_ERROR ||
                    putImageMetaDataError == UploadError.API_UNAUTHORIZED_ERROR) {
                reset();
                // Start the login process
                BrapiAuthDialog brapiAuth = new BrapiAuthDialog(BrapiExportActivity.this, BrAPIService.exportTarget);
                brapiAuth.show();
                return;
            } else {
                String message;
                if (putObservationsError != UploadError.NONE) {
                    message = getMessageForErrorCode(putObservationsError);
                } else if (postImageMetaDataError != UploadError.NONE) {
                    message = getMessageForErrorCode(postImageMetaDataError);
                } else if (putImageContentError != UploadError.NONE) {
                    message = getMessageForErrorCode(putImageContentError);
                } else if (putImageMetaDataError != UploadError.NONE) {
                    message = getMessageForErrorCode(putImageMetaDataError);
                } else {
                    message = getMessageForErrorCode(UploadError.NONE);
                }

                Toast.makeText(this.getApplicationContext(), message, Toast.LENGTH_LONG).show();
            }

            // refresh statistics
            loadStatistics();
            // reset
            reset();
        }
    }

    private Boolean imagesComplete() {
        return postImageMetaDataUpdatesCount == numNewImages &&
                putImageContentUpdatesCount == (numNewImages + numEditedImages + numIncompleteImages) &&
                putImageMetaDataUpdatesCount == (numEditedImages + numIncompleteImages);
    }

    private Boolean observationsComplete() {
        return observationsComplete;
    }

    private void reset() {
        putObservationsError = UploadError.NONE;
        postImageMetaDataError = UploadError.NONE;
        putImageContentError = UploadError.NONE;
        putImageMetaDataError = UploadError.NONE;
        observationsComplete = false;
        postImageMetaDataUpdatesCount = 0;
        putImageContentUpdatesCount = 0;
        putImageMetaDataUpdatesCount = 0;
    }

    private UploadError processResponse(List<NewObservationDbIdsObservations> observationDbIds, List<Observation> observationsNeedingSync) {
        UploadError retVal = UploadError.NONE;
        SimpleDateFormat timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZ",
                Locale.getDefault());
        String syncTime = timeStamp.format(Calendar.getInstance().getTime());

        if (observationDbIds.size() != observationsNeedingSync.size()) {
            retVal = UploadError.WRONG_NUM_OBSERVATIONS_RETURNED;
        } else {
            // TODO: update to work with multiple observations per variable
            // Also would be nice to have a cleaner 'find' mechanism
            // For now, just use observationUnitDbId and observationVariableId to key off
            // Won't work for multiple observations of the same variable which we want to support in the future

            for (NewObservationDbIdsObservations observationDbId : observationDbIds) {
                // find observation with matching keys and update observationDbId
                Observation converted = new Observation(observationDbId);
                int first_index = observationsNeedingSync.indexOf(converted);
                int last_index = observationsNeedingSync.lastIndexOf(converted);
                if (first_index == -1) {
                    retVal = UploadError.MISSING_OBSERVATION_IN_RESPONSE;
                } else if (first_index != last_index) {
                    retVal = UploadError.MULTIPLE_OBSERVATIONS_PER_VARIABLE;
                } else {
                    Observation update = observationsNeedingSync.get(first_index);
                    update.setDbId(converted.getDbId());
                    update.setLastSyncedTime(syncTime);
                    observationsNeedingSync.set(first_index, update);
                    // TODO: if image data part of observation then store images on BrAPI host
                    // a new BrAPI service using the images endpoints is needed
                }
            }

            if (retVal == UploadError.NONE) {
                dataHelper.updateObservations(observationsNeedingSync);
            }
        }

        return retVal;
    }

    private UploadError processImageResponse(Image response, String fieldBookId,
                                             Boolean writeSyncTime) {

        UploadError retVal = UploadError.NONE;
        // keep track of responses to be processed when finished?

        SimpleDateFormat timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ssZ",
                Locale.getDefault());
        String syncTime = timeStamp.format(Calendar.getInstance().getTime());

        // Check that our image id was passed back
        com.fieldbook.tracker.brapi.Image converted = new com.fieldbook.tracker.brapi.Image(response);
        converted.setLastSyncedTime(syncTime);
        converted.setFieldbookDbId(fieldBookId);

        if (converted.getDbId() != null && converted.getFieldBookDbId() != null) {
            dataHelper.updateImage(converted, writeSyncTime);
        } else {
            retVal = UploadError.MISSING_OBSERVATION_IN_RESPONSE;
        }

        return retVal;
    }

    private void processPutObservationsResponse(List<NewObservationDbIdsObservations> observationDbIds) {
        UploadError error = processResponse(observationDbIds, observationsNeedingSync);
        putObservationsError = error;
    }

    private void processPostImageResponse(Image response, String fieldBookId) {
        UploadError error = processImageResponse(response, fieldBookId, false);
        // latch error so that if one happens it is not cleared until all posts are done
        if (postImageMetaDataError == UploadError.NONE) {
            postImageMetaDataError = error;
        }

    }

    private void processPutImageContentResponse(Image response, String fieldBookId) {
        UploadError error = processImageResponse(response, fieldBookId, true);
        // latch error so that if one happens it is not cleared until all posts are done
        if (putImageContentError == UploadError.NONE) {
            putImageContentError = error;
        }
    }

    private void processPutImageResponse(Image response, String fieldBookId) {
        UploadError error = processImageResponse(response, fieldBookId, false);
        // latch error so that if one happens it is not cleared until all posts are done
        if (putImageMetaDataError == UploadError.NONE) {
            putImageMetaDataError = error;
        }
    }

    private void loadStatistics() {

        numNewObservations = 0;
        numSyncedObservations = 0;
        numEditedObservations = 0;
        numNewImages = 0;
        numEditedImages = 0;
        numSyncedImages = 0;
        numIncompleteImages = 0;

        String hostURL = BrAPIService.getHostUrl(BrAPIService.getBrapiUrl(this));
        List<Observation> observations = dataHelper.getObservations(hostURL);
        observationsNeedingSync.clear();
        List<Observation> userCreatedTraitObservations = dataHelper.getUserTraitObservations();
        List<Observation> wrongSourceObservations = dataHelper.getWrongSourceObservations(hostURL);

        List<com.fieldbook.tracker.brapi.Image> images = dataHelper.getImageObservations(hostURL);
        imagesNew.clear();
        imagesEditedIncomplete.clear();
        List<com.fieldbook.tracker.brapi.Image> userCreatedTraitImages = dataHelper.getUserTraitImageObservations();
        List<com.fieldbook.tracker.brapi.Image> wrongSourceImages = dataHelper.getWrongSourceImageObservations(hostURL);

        for (Observation observation : observations) {
            switch (observation.getStatus()) {
                case NEW:
                    numNewObservations++;
                    observationsNeedingSync.add(observation);
                    break;
                case SYNCED:
                    numSyncedObservations++;
                    break;
                case EDITED:
                    numEditedObservations++;
                    observationsNeedingSync.add(observation);
                    break;
            }
        }

        for (com.fieldbook.tracker.brapi.Image image : images) {
            switch (image.getStatus()) {
                case NEW:
                    numNewImages++;
                    imagesNew.add(image);
                    break;
                case SYNCED:
                    numSyncedImages++;
                    break;
                case EDITED:
                    numEditedImages++;
                    imagesEditedIncomplete.add(image);
                    break;
                case INCOMPLETE:
                    numIncompleteImages++;
                    imagesEditedIncomplete.add(image);
                    break;
            }
        }

        SharedPreferences ep = this.getSharedPreferences("Settings", 0);
        String field = ep.getString("FieldFile", "");

        ((TextView) findViewById(R.id.brapistudyValue)).setText(field);
        ((TextView) findViewById(R.id.brapiNumNewValue)).setText(String.valueOf(numNewObservations));
        ((TextView) findViewById(R.id.brapiNumSyncedValue)).setText(String.valueOf(numSyncedObservations));
        ((TextView) findViewById(R.id.brapiNumEditedValue)).setText(String.valueOf(numEditedObservations));
        ((TextView) findViewById(R.id.brapiUserCreatedValue)).setText(String.valueOf(userCreatedTraitObservations.size()));
        ((TextView) findViewById(R.id.brapiWrongSource)).setText(String.valueOf(wrongSourceObservations.size()));

        ((TextView) findViewById(R.id.brapiNumNewImagesValue)).setText(String.valueOf(numNewImages));
        ((TextView) findViewById(R.id.brapiNumSyncedImagesValue)).setText(String.valueOf(numSyncedImages));
        ((TextView) findViewById(R.id.brapiNumEditedImagesValue)).setText(String.valueOf(numEditedImages));
        ((TextView) findViewById(R.id.brapiNumIncompleteImagesValue)).setText(String.valueOf(numIncompleteImages));

        ((TextView) findViewById(R.id.brapiUserCreatedImagesValue)).setText(String.valueOf(userCreatedTraitImages.size()));
        ((TextView) findViewById(R.id.brapiWrongSourceImages)).setText(String.valueOf(wrongSourceImages.size()));

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    public enum UploadError {
        NONE,
        MISSING_OBSERVATION_IN_RESPONSE,
        MULTIPLE_OBSERVATIONS_PER_VARIABLE,
        WRONG_NUM_OBSERVATIONS_RETURNED,
        API_CALLBACK_ERROR,
        API_UNAUTHORIZED_ERROR,
        API_NOTSUPPORTED_ERROR,
        API_PERMISSION_ERROR
    }
}