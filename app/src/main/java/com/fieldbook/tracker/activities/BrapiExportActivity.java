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

import com.fieldbook.tracker.brapi.ApiErrorCode;
import com.fieldbook.tracker.brapi.service.BrAPIService;
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory;
import com.fieldbook.tracker.brapi.BrapiControllerResponse;
import com.fieldbook.tracker.brapi.model.FieldBookImage;
import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.R;
import com.fieldbook.tracker.utilities.Utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

public class BrapiExportActivity extends AppCompatActivity {

    private BrAPIService brAPIService;
    private DataHelper dataHelper;
    private List<Observation> newObservations;
    private List<Observation> editedObservations;
    private List<FieldBookImage> imagesNew;
    private List<FieldBookImage> imagesEditedIncomplete;

    private int postImageMetaDataUpdatesCount;
    private int putImageContentUpdatesCount;
    private int putImageMetaDataUpdatesCount;

    private Boolean createObservationsComplete;
    private Boolean updateObservationsComplete;

    private int numNewObservations;
    private int numSyncedObservations;
    private int numEditedObservations;
    private int numNewImages;
    private int numSyncedImages;
    private int numEditedImages;
    private int numIncompleteImages;
    private UploadError createObservationsError;
    private UploadError updateObservationsError;
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

                this.dataHelper = new DataHelper(this);

                brAPIService = BrAPIServiceFactory.getBrAPIService(this);

                createObservationsError = UploadError.NONE;
                updateObservationsError = UploadError.NONE;
                postImageMetaDataError = UploadError.NONE;
                putImageContentError = UploadError.NONE;
                putImageMetaDataError = UploadError.NONE;

                postImageMetaDataUpdatesCount = 0;
                putImageContentUpdatesCount = 0;
                putImageMetaDataUpdatesCount = 0;

                newObservations = new ArrayList<>();
                editedObservations = new ArrayList<>();

                numNewObservations = 0;
                numSyncedObservations = 0;
                numEditedObservations = 0;
                imagesNew = new ArrayList<>();
                imagesEditedIncomplete = new ArrayList<>();
                numNewImages = 0;
                numSyncedImages = 0;
                numEditedImages = 0;
                numIncompleteImages = 0;
                createObservationsComplete = false;
                updateObservationsComplete = false;

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
//        BrapiControllerResponse brapiControllerResponse = BrAPIService.checkBrapiAuth(this);

        // Check whether our brapi auth response was exists or was successful
    //    processBrapiControllerMessage(brapiControllerResponse);

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
                if (numNewObservations > 0) {
                    createObservations();
                }else{
                    createObservationsComplete = true;
                    uploadComplete();
                }

                if (numEditedObservations > 0) {
                    updateObservations();
                }else{
                    updateObservationsComplete = true;
                    uploadComplete();
                }

                if (numNewImages > 0) {
                    loadNewImages();
                    postImages(imagesNew);
                }
                if (numEditedImages > 0 || numIncompleteImages > 0) {
                    loadEditedIncompleteImages();
                    putImages();
                }
            }
        });
    }

    private void loadNewImages() {
        for (FieldBookImage image : imagesNew) {
            image.loadImage();
        }
    }

    private void loadEditedIncompleteImages() {
        for (FieldBookImage image : imagesEditedIncomplete) {
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

    private void createObservations() {
        brAPIService.createObservations(newObservations,
                new Function<List<Observation>, Void>() {
                    @Override
                    public Void apply(final List<Observation> observationDbIds) {

                        (BrapiExportActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                processCreateObservationsResponse(observationDbIds);
                                createObservationsComplete = true;
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
                                createObservationsError = processErrorCode(code);
                                createObservationsComplete = true;
                                uploadComplete();
                            }
                        });

                        return null;
                    }
                }
        );
    }

    private void updateObservations() {

        brAPIService.updateObservations(editedObservations,
                new Function<List<Observation>, Void>() {
                    @Override
                    public Void apply(final List<Observation> observationDbIds) {

                        (BrapiExportActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                processUpdateObservationsResponse(observationDbIds);
                                updateObservationsComplete = true;
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
                                updateObservationsError = processErrorCode(code);
                                updateObservationsComplete = true;
                                uploadComplete();
                            }
                        });

                        return null;
                    }
                }
        );
    }

    private void postImages(List<FieldBookImage> newImages) {
        for (FieldBookImage image : newImages) {
            postImage(image);
        }
    }

    private void postImage(final FieldBookImage imageData) {

        brAPIService.postImageMetaData(imageData,
                new Function<FieldBookImage, Void>() {
                    @Override
                    public Void apply(final FieldBookImage image) {

                        (BrapiExportActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String fieldBookId = imageData.getFieldBookDbId();
                                processPostImageResponse(image, fieldBookId);
                                postImageMetaDataUpdatesCount++;
                                imageData.setDbId(image.getDbId());
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

    private void putImageContent(final FieldBookImage image, final List<FieldBookImage> uploads) {

        brAPIService.putImageContent(image,
                new Function<FieldBookImage, Void>() {
                    @Override
                    public Void apply(final FieldBookImage responseImage) {

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
        for (FieldBookImage image : imagesEditedIncomplete) {
            putImage(image);
        }
    }

    private void putImage(final FieldBookImage imageData) {

        brAPIService.putImage(imageData,
                new Function<FieldBookImage, Void>() {
                    @Override
                    public Void apply(final FieldBookImage image) {

                        (BrapiExportActivity.this).runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                String fieldBookId = imageData.getFieldBookDbId();
                                processPutImageResponse(image, fieldBookId);
                                putImageMetaDataUpdatesCount++;
                                imageData.setDbId(image.getDbId());
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
        ApiErrorCode apiErrorCode = ApiErrorCode.processErrorCode(code);

        if (apiErrorCode == null) {
            return UploadError.API_CALLBACK_ERROR;
        }

        switch (apiErrorCode) {
            case FORBIDDEN:
                // Warn that they do not have permissions to push traits
                retVal = UploadError.API_PERMISSION_ERROR;
                break;
            case UNAUTHORIZED:
                // Start the login process
                retVal = UploadError.API_UNAUTHORIZED_ERROR;
                break;
            case NOT_FOUND:
                // End point not supported
                retVal = UploadError.API_NOTSUPPORTED_ERROR;
                break;
            case BAD_REQUEST:
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
            if (createObservationsError == UploadError.API_UNAUTHORIZED_ERROR ||
                    updateObservationsError == UploadError.API_UNAUTHORIZED_ERROR ||
                    postImageMetaDataError == UploadError.API_UNAUTHORIZED_ERROR ||
                    putImageContentError == UploadError.API_UNAUTHORIZED_ERROR ||
                    putImageMetaDataError == UploadError.API_UNAUTHORIZED_ERROR) {
                reset();
                BrAPIService.handleConnectionError(this, 401);
                return;
            } else {
                String message;
                if (createObservationsError != UploadError.NONE) {
                    message = getMessageForErrorCode(createObservationsError);
                } else if (updateObservationsError != UploadError.NONE) {
                    message = getMessageForErrorCode(updateObservationsError);
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
        return createObservationsComplete && updateObservationsComplete;
    }

    private void reset() {
        createObservationsError = UploadError.NONE;
        updateObservationsError = UploadError.NONE;
        postImageMetaDataError = UploadError.NONE;
        putImageContentError = UploadError.NONE;
        putImageMetaDataError = UploadError.NONE;
        createObservationsComplete = false;
        updateObservationsComplete = false;
        postImageMetaDataUpdatesCount = 0;
        putImageContentUpdatesCount = 0;
        putImageMetaDataUpdatesCount = 0;
    }

    private UploadError processResponse(List<Observation> observationDbIds, List<Observation> observationsNeedingSync) {
        UploadError retVal = UploadError.NONE;
        SimpleDateFormat timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZZZZZ",
                Locale.getDefault());
        String syncTime = timeStamp.format(Calendar.getInstance().getTime());

        if (observationDbIds.size() != observationsNeedingSync.size()) {
            retVal = UploadError.WRONG_NUM_OBSERVATIONS_RETURNED;
        } else {
            // TODO: update to work with multiple observations per variable
            // Also would be nice to have a cleaner 'find' mechanism
            // For now, just use observationUnitDbId and observationVariableId to key off
            // Won't work for multiple observations of the same variable which we want to support in the future

            for (Observation converted : observationDbIds) {
                // find observation with matching keys and update observationDbId
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

    private UploadError processImageResponse(FieldBookImage converted, String fieldBookId,
                                             Boolean writeSyncTime) {

        UploadError retVal = UploadError.NONE;
        // keep track of responses to be processed when finished?

        SimpleDateFormat timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZZZZZ",
                Locale.getDefault());
        String syncTime = timeStamp.format(Calendar.getInstance().getTime());

        // Check that our image id was passed back
        converted.setLastSyncedTime(syncTime);
        converted.setFieldbookDbId(fieldBookId);

        if (converted.getDbId() != null && converted.getFieldBookDbId() != null) {
            dataHelper.updateImage(converted, writeSyncTime);
        } else {
            retVal = UploadError.MISSING_OBSERVATION_IN_RESPONSE;
        }

        return retVal;
    }

    private void processCreateObservationsResponse(List<Observation> observationDbIds) {
        UploadError error = processResponse(observationDbIds, newObservations);
        updateObservationsError = error;
    }

    private void processUpdateObservationsResponse(List<Observation> observationDbIds) {
        UploadError error = processResponse(observationDbIds, editedObservations);
        updateObservationsError = error;
    }

    private void processPostImageResponse(FieldBookImage response, String fieldBookId) {
        UploadError error = processImageResponse(response, fieldBookId, false);
        // latch error so that if one happens it is not cleared until all posts are done
        if (postImageMetaDataError == UploadError.NONE) {
            postImageMetaDataError = error;
        }

    }

    private void processPutImageContentResponse(FieldBookImage response, String fieldBookId) {
        UploadError error = processImageResponse(response, fieldBookId, true);
        // latch error so that if one happens it is not cleared until all posts are done
        if (putImageContentError == UploadError.NONE) {
            putImageContentError = error;
        }
    }

    private void processPutImageResponse(FieldBookImage response, String fieldBookId) {
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

        String hostURL = BrAPIService.getHostUrl(this);
        List<Observation> observations = dataHelper.getObservations(hostURL);
        List<Observation> userCreatedTraitObservations = dataHelper.getUserTraitObservations();
        List<Observation> wrongSourceObservations = dataHelper.getWrongSourceObservations(hostURL);

        List<FieldBookImage> images = dataHelper.getImageObservations(hostURL);
        imagesNew.clear();
        imagesEditedIncomplete.clear();
        List<FieldBookImage> userCreatedTraitImages = dataHelper.getUserTraitImageObservations();
        List<FieldBookImage> wrongSourceImages = dataHelper.getWrongSourceImageObservations(hostURL);

        for (Observation observation : observations) {
            switch (observation.getStatus()) {
                case NEW:
                    numNewObservations++;
                    newObservations.add(observation);
                    break;
                case SYNCED:
                    numSyncedObservations++;
                    break;
                case EDITED:
                    numEditedObservations++;
                    editedObservations.add(observation);
                    break;
            }
        }

        for (FieldBookImage image : images) {
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