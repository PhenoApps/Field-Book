package com.fieldbook.tracker.activities.brapi;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.arch.core.util.Function;

import com.fieldbook.tracker.R;
import com.fieldbook.tracker.activities.ThemedActivity;
import com.fieldbook.tracker.brapi.ApiErrorCode;
import com.fieldbook.tracker.brapi.BrapiAuthDialogFragment;
import com.fieldbook.tracker.brapi.BrapiControllerResponse;
import com.fieldbook.tracker.brapi.model.FieldBookImage;
import com.fieldbook.tracker.brapi.model.Observation;
import com.fieldbook.tracker.brapi.service.BrAPIService;
import com.fieldbook.tracker.brapi.service.BrAPIServiceFactory;
import com.fieldbook.tracker.database.DataHelper;
import com.fieldbook.tracker.objects.FieldObject;
import com.fieldbook.tracker.utilities.BrapiExportUtil;
import com.fieldbook.tracker.utilities.Utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import javax.inject.Inject;

import dagger.hilt.android.AndroidEntryPoint;

@AndroidEntryPoint
public class BrapiExportActivity extends ThemedActivity {
    private static final String TAG = BrapiExportActivity.class.getName();
    public static final String FIELD_IDS = "FIELD_ID";

    @Inject
    SharedPreferences preferences;
    @Inject
    DataHelper dataHelper;

    //keep track of total count now since queries are broken into chunks
    private int newObservationsExportedCount = 0;
    private int editedObservationsExportedCount = 0;

    private BrAPIService brAPIService;
    private List<Observation> newObservations;
    private List<Observation> editedObservations;
    private List<FieldBookImage> imagesNew;
    private List<FieldBookImage> imagesEditedIncomplete;
    private List<Integer> fieldIds;
    private int fieldId;
    private int currentFieldIndex;
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

    private final BrapiAuthDialogFragment brapiAuth = new BrapiAuthDialogFragment().newInstance();

    public BrapiExportActivity() {

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Utils.isConnected(this)) {
            if (BrAPIService.hasValidBaseUrl(this)) {

                requestWindowFeature(Window.FEATURE_NO_TITLE);
                setContentView(R.layout.dialog_brapi_export);

                // Extract the fieldIds from the intent
                Intent intent = getIntent();
                if (intent != null && intent.hasExtra(FIELD_IDS)) {
                    fieldIds = intent.getIntegerArrayListExtra(FIELD_IDS);
                    currentFieldIndex = 0;
                    if (fieldIds != null && !fieldIds.isEmpty()) {
                        fieldId = fieldIds.get(currentFieldIndex);
                    }
                }

                brAPIService = BrAPIServiceFactory.getBrAPIService(this);

                createObservationsError = UploadError.NONE;
                updateObservationsError = UploadError.NONE;
                postImageMetaDataError = UploadError.NONE;
                putImageContentError = UploadError.NONE;
                putImageMetaDataError = UploadError.NONE;

                postImageMetaDataUpdatesCount = 0;
                putImageContentUpdatesCount = 0;
                putImageMetaDataUpdatesCount = 0;

                newObservationsExportedCount = 0;
                editedObservationsExportedCount = 0;

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

                Button nextFieldButton = findViewById(R.id.next_field_btn);
                nextFieldButton.setOnClickListener(v -> moveToNextField());

                Button closeButton = findViewById(R.id.close_btn);
                closeButton.setOnClickListener(v -> finish());

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
            getSupportActionBar().setTitle(R.string.act_brapi_export_title);
            getSupportActionBar().getThemedContext();
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setHomeButtonEnabled(true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        brAPIService.authorizeClient();

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
        int id = v.getId();
        if (id == R.id.brapi_export_btn) {
            if (numNewObservations == 0 && numEditedObservations == 0 &&
                    numNewImages == 0 && numEditedImages == 0 && numIncompleteImages == 0) {
                Toast.makeText(this, "Error: Nothing to sync", Toast.LENGTH_SHORT).show();
            } else {
                showSaving();
                sendData();
            }
        } else if (id == R.id.close_btn) {
            finish();
        }
    }

    private void sendData() {

        AsyncTask.execute(() -> {
            if (numNewObservations > 0) {
                try {
                    createObservations();
                } catch (InterruptedException e) {
                    Log.w(TAG, "Saving observations was interrupted", e);
                }
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
        });
    }

    private void loadNewImages() {
        for (FieldBookImage image : imagesNew) {
            loadImage(image);
        }
    }

    private void loadEditedIncompleteImages() {
        for (FieldBookImage image : imagesEditedIncomplete) {
            loadImage(image);
        }
    }

    private void loadImage(FieldBookImage image) {

        try {

            image.loadImage(this);

        } catch (Exception e) {

            e.printStackTrace();

            Utils.makeToast(this, getString(R.string.act_brapi_export_image_load_failed));
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
        runOnUiThread(() -> {
            Button exportBtn = this.findViewById(R.id.brapi_export_btn);
            exportBtn.setEnabled(true);
            this.findViewById(R.id.saving_panel).setVisibility(View.GONE);
        });
    }

    private void createObservations() throws InterruptedException {
        int chunkSize = BrAPIService.getChunkSize(this);
        brAPIService.createObservationsChunked(chunkSize, newObservations, (responseObservationsChunk, completedChunkNum, inputObservationsChunk, done) -> {

            if (!responseObservationsChunk.isEmpty()) {

                newObservationsExportedCount += responseObservationsChunk.size();

                processCreateObservationsResponse(responseObservationsChunk, done);

                numNewObservations -= responseObservationsChunk.size();
                numSyncedObservations += responseObservationsChunk.size();

                (BrapiExportActivity.this).runOnUiThread(() -> {

                    ((TextView) findViewById(R.id.brapiNumNewValue)).setText(String.valueOf(numNewObservations));
                    ((TextView) findViewById(R.id.brapiNumSyncedValue)).setText(String.valueOf(numSyncedObservations));

                    if (done) {
                        createObservationsComplete = true;
                        uploadComplete();
                    }
                });
            }
        }, failureInput -> {
            createObservationsError = createObservationsError == null ? processErrorCode(failureInput) : createObservationsError;
            createObservationsError = processErrorCode(failureInput);
            createObservationsComplete = true;
            uploadComplete();
            return null;
        });
    }

    private void updateObservations() {

        int chunkSize = BrAPIService.getChunkSize(this);
        brAPIService.updateObservationsChunked(chunkSize, editedObservations, (responseChunk, completedChunkNum, inputChunk, done) -> {

            editedObservationsExportedCount += responseChunk.size();

            processUpdateObservationsResponse(responseChunk, done);

            numEditedObservations -= responseChunk.size();
            numSyncedObservations += responseChunk.size();

            (BrapiExportActivity.this).runOnUiThread(() -> {
                ((TextView) findViewById(R.id.brapiNumEditedValue)).setText(String.valueOf(numEditedObservations));
                ((TextView) findViewById(R.id.brapiNumSyncedValue)).setText(String.valueOf(numSyncedObservations));

                if(done) {
                    updateObservationsComplete = true;
                    uploadComplete();
                }
            });
        }, code -> {
            (BrapiExportActivity.this).runOnUiThread(() -> {
                updateObservationsError = processErrorCode(code);
                updateObservationsComplete = true;
                uploadComplete();
            });

            return null;
        });
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
                }, code -> {

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
                boolean showDialog = BrAPIService.handleConnectionError(this, 401);
                if (showDialog) {
                    try {
                        runOnUiThread(() -> {
                            if (!brapiAuth.isVisible()) {
                                brapiAuth.show(getSupportFragmentManager(), "BrapiAuthDialogFragment");
                            }
                        });
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
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

                runOnUiThread(() -> {
                    Toast.makeText(this.getApplicationContext(), message, Toast.LENGTH_LONG).show();
                });
            }

            if (currentFieldIndex < fieldIds.size() - 1) {
                showNextFieldButton();
            } else {
                loadStatistics();
                reset();
                showCloseButton();
            }
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

        newObservations.clear();
        editedObservations.clear();
        imagesNew.clear();
        imagesEditedIncomplete.clear();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (brapiAuth != null && brapiAuth.isAdded() && brapiAuth.isVisible()) {
            brapiAuth.dismiss();
        }
    }

    /**
     * Process the response from the BrAPI service for observations.
     *
     * @param observations        List of observations returned from the BrAPI service (chunk)
     * @param observationsNeedingSync List of observations that need created or updated.
     * @param currentCount            Current number of observations processed.
     * @param done                    Whether all chunks have been processed.
     * @return UploadError indicating the result of processing the response.
     */
    private UploadError processResponse(List<Observation> observations, List<Observation> observationsNeedingSync, int currentCount, boolean done) {
        UploadError retVal = UploadError.NONE;
        SimpleDateFormat timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZZZZZ",
                Locale.getDefault());
        String syncTime = timeStamp.format(Calendar.getInstance().getTime());

        //first return a failure if the chunked processsing is complete but the expected observations processed does not match the actual processed
        if (done && currentCount != observationsNeedingSync.size()) {

            retVal = UploadError.WRONG_NUM_OBSERVATIONS_RETURNED;

        } else { //otherwise save the chunked data as it comes in from the server

            for (Observation converted : observations) {

                // find observation with matching keys and update observationDbId
                int firstIndex = BrapiExportUtil.Companion.firstIndexOfDbId(observationsNeedingSync, converted);
                int lastIndex = BrapiExportUtil.Companion.lastIndexOfDbId(observationsNeedingSync, converted);

                if (firstIndex == -1) {
                    retVal = UploadError.MISSING_OBSERVATION_IN_RESPONSE;
                } else if (firstIndex != lastIndex) {
                    retVal = UploadError.MULTIPLE_OBSERVATIONS_PER_VARIABLE;
                } else {
                    Observation update = observationsNeedingSync.get(firstIndex);
                    update.setDbId(converted.getDbId());
                    update.setLastSyncedTime(syncTime);
                    observationsNeedingSync.set(firstIndex, update);
                    // TODO: if image data part of observation then store images on BrAPI host
                    // a new BrAPI service using the images endpoints is needed
                }
            }

            if (retVal == UploadError.NONE) {
                dataHelper.updateObservations(observations);
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

    private void processCreateObservationsResponse(List<Observation> observationDbIds, boolean done) {
        UploadError error = processResponse(observationDbIds, newObservations, newObservationsExportedCount, done);
        updateObservationsError = error;
    }

    private void processUpdateObservationsResponse(List<Observation> observationDbIds, boolean done) {
        UploadError error = processResponse(observationDbIds, editedObservations, editedObservationsExportedCount, done);
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
        List<Observation> brapiObservations = dataHelper.getBrapiObservations(fieldId, hostURL);
        List<Observation> userCreatedTraitObservations = dataHelper.getLocalObservations(fieldId);
        List<Observation> wrongSourceObservations = dataHelper.getWrongSourceObservations(hostURL);

        List<FieldBookImage> images = dataHelper.getImageObservations(this, hostURL);
        imagesNew.clear();
        imagesEditedIncomplete.clear();
        List<FieldBookImage> userCreatedTraitImages = dataHelper.getUserTraitImageObservations(this, fieldId);
        List<FieldBookImage> wrongSourceImages = dataHelper.getWrongSourceImageObservations(this, hostURL);

        for (Observation observation : brapiObservations) {
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

        FieldObject field = dataHelper.getFieldObject(fieldId);

        runOnUiThread(() -> {
            ((TextView) findViewById(R.id.brapistudyValue)).setText(field.getAlias());
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
        });
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    private void moveToNextField() {
        currentFieldIndex++;
        if (currentFieldIndex < fieldIds.size()) {
            fieldId = fieldIds.get(currentFieldIndex);
            reset();
            loadStatistics();
            showExportButton();
        } else {
            Toast.makeText(this, R.string.all_fields_processed, Toast.LENGTH_SHORT).show();
            finish();
        }
    }

    private void showExportButton() {

        runOnUiThread(() -> {
            Button exportButton = findViewById(R.id.brapi_export_btn);
            Button nextFieldButton = findViewById(R.id.next_field_btn);
            Button closeButton = findViewById(R.id.close_btn);

            exportButton.setVisibility(View.VISIBLE);
            nextFieldButton.setVisibility(View.GONE);
            closeButton.setVisibility(View.GONE);
        });
    }

    private void showNextFieldButton() {

        runOnUiThread(() -> {
            Button exportButton = findViewById(R.id.brapi_export_btn);
            Button nextFieldButton = findViewById(R.id.next_field_btn);
            Button closeButton = findViewById(R.id.close_btn);

            exportButton.setVisibility(View.GONE);
            nextFieldButton.setVisibility(View.VISIBLE);
            closeButton.setVisibility(View.GONE);
        });
    }

    private void showCloseButton() {

        runOnUiThread(() -> {
            Button exportButton = findViewById(R.id.brapi_export_btn);
            Button nextFieldButton = findViewById(R.id.next_field_btn);
            Button closeButton = findViewById(R.id.close_btn);

            exportButton.setVisibility(View.GONE);
            nextFieldButton.setVisibility(View.GONE);
            closeButton.setVisibility(View.VISIBLE);
        });
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