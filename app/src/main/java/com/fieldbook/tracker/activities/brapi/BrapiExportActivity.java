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
import androidx.fragment.app.FragmentActivity;

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
import com.fieldbook.tracker.preferences.GeneralKeys;
import com.fieldbook.tracker.utilities.BrapiExportUtil;
import com.fieldbook.tracker.utilities.Utils;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;
import java.util.Map;

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

    private static final String PERFORMANCE_TAG = "BrapiExportPerformance";
    private static final String MEMORY_TAG = "BrapiExportMemory";
    private static final String FLOW_TAG = "BrapiExportFlow";

    private BrAPIService brAPIService;
    private List<Observation> newObservations;
    private List<Observation> editedObservations;
    private List<Observation> newImageObservations;
    private List<Observation> syncedImageObservations;
    private List<Observation> editedImageObservations;
    private List<Observation> incompleteImageObservations;
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
        Log.d(FLOW_TAG, "Starting BrapiExportActivity");
        logMemoryUsage("onCreate start");
        
        super.onCreate(savedInstanceState);

        if (Utils.isConnected(this)) {
            if (BrAPIService.hasValidBaseUrl(this)) {
                Log.d(FLOW_TAG, "Network connected and BrAPI base URL is valid");
                
                requestWindowFeature(Window.FEATURE_NO_TITLE);
                setContentView(R.layout.dialog_brapi_export);

                // Extract the fieldIds from the intent
                Intent intent = getIntent();
                if (intent != null && intent.hasExtra(FIELD_IDS)) {
                    fieldIds = intent.getIntegerArrayListExtra(FIELD_IDS);
                    currentFieldIndex = 0;
                    if (fieldIds != null && !fieldIds.isEmpty()) {
                        fieldId = fieldIds.get(currentFieldIndex);
                        Log.d(FLOW_TAG, "Processing field ID: " + fieldId + " (1 of " + fieldIds.size() + ")");
                    } else {
                        Log.w(FLOW_TAG, "No field IDs provided in intent");
                    }
                } else {
                    Log.w(FLOW_TAG, "Intent missing FIELD_IDS extra");
                }

                brAPIService = BrAPIServiceFactory.getBrAPIService(this);

                // Initialize variables
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

                Button nextFieldButton = findViewById(R.id.next_field_btn);
                nextFieldButton.setOnClickListener(v -> moveToNextField());

                Button closeButton = findViewById(R.id.close_btn);
                closeButton.setOnClickListener(v -> finish());

            } else {
                Log.w(FLOW_TAG, "BrAPI base URL is not configured");
                Toast.makeText(getApplicationContext(), R.string.brapi_must_configure_url, Toast.LENGTH_SHORT).show();
                finish();
            }
        } else {
            Log.w(FLOW_TAG, "Device is offline");
            // Check if the user is connected. If not, pull from cache
            Toast.makeText(getApplicationContext(), R.string.device_offline_warning, Toast.LENGTH_SHORT).show();
            finish();
        }
        
        logMemoryUsage("onCreate end");
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

    private void logMemoryUsage(String location) {
        Runtime runtime = Runtime.getRuntime();
        long usedMemoryMB = (runtime.totalMemory() - runtime.freeMemory()) / 1048576L;
        long maxMemoryMB = runtime.maxMemory() / 1048576L;
        Log.d(MEMORY_TAG, location + " - Memory used: " + usedMemoryMB + "MB / Max: " + maxMemoryMB + "MB (" + (usedMemoryMB * 100 / maxMemoryMB) + "%)");
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

    // private void sendData() {
    //     Log.d(FLOW_TAG, "Starting data export process");
    //     Log.d(FLOW_TAG, "Observations to create: " + numNewObservations + ", to update: " + numEditedObservations);
    //     Log.d(FLOW_TAG, "Images to create: " + numNewImages + ", to update: " + (numEditedImages + numIncompleteImages));
    //     logMemoryUsage("Before sendData");
        
    //     AsyncTask.execute(() -> {
    //         if (numNewObservations > 0) {
    //             Log.d(FLOW_TAG, "Starting observation creation");
    //             try {
    //                 createObservations();
    //             } catch (InterruptedException e) {
    //                 Log.w(TAG, "Saving observations was interrupted", e);
    //             }
    //         } else {
    //             createObservationsComplete = true;
    //             uploadComplete();
    //         }

    //         if (numEditedObservations > 0) {
    //             Log.d(FLOW_TAG, "Starting observation updates");
    //             updateObservations();
    //         } else {
    //             updateObservationsComplete = true;
    //             uploadComplete();
    //         }

    //         if (numNewImages > 0) {
    //             Log.d(FLOW_TAG, "Loading " + imagesNew.size() + " new images");
    //             loadNewImages();
    //             Log.d(FLOW_TAG, "Starting upload of " + imagesNew.size() + " new images");
    //             postImages(imagesNew);
    //         }
            
    //         if (numEditedImages > 0 || numIncompleteImages > 0) {
    //             Log.d(FLOW_TAG, "Loading " + imagesEditedIncomplete.size() + " edited/incomplete images");
    //             loadEditedIncompleteImages();
    //             Log.d(FLOW_TAG, "Starting update of " + imagesEditedIncomplete.size() + " edited/incomplete images");
    //             putImages();
    //         }
    //     });
    // }

    // private void sendData() {
    //     AsyncTask.execute(() -> {
    //         if (numNewObservations > 0) {
    //             try {
    //                 createObservations();
    //             } catch (InterruptedException e) {
    //                 Log.w(TAG, "Saving observations was interrupted", e);
    //             }
    //         } else {
    //             createObservationsComplete = true;
    //             uploadComplete();
    //         }

    //         if (numEditedObservations > 0) {
    //             updateObservations();
    //         } else {
    //             updateObservationsComplete = true;
    //             uploadComplete();
    //         }

    //         // Load image details only when needed
    //         if (numNewImages > 0) {
    //             Log.d(TAG, "Loading new image details");
    //             long startTime = System.currentTimeMillis();
                
    //             // Get missing photo bitmap for image loading
    //             Bitmap missingPhoto = BitmapFactory.decodeResource(getResources(), R.drawable.missing_image);
                
    //             // Load image details for new images
    //             imagesNew = dataHelper.getImageDetails(this, newImageObservations, missingPhoto);
                
    //             Log.d(TAG, "Loaded " + imagesNew.size() + " new image details in " + (System.currentTimeMillis() - startTime) + "ms");
                
    //             // Load the actual image data
    //             loadNewImages();
    //             postImages(imagesNew);
    //         }
            
    //         if (numEditedImages > 0 || numIncompleteImages > 0) {
    //             Log.d(TAG, "Loading edited/incomplete image details");
    //             long startTime = System.currentTimeMillis();
                
    //             // Get missing photo bitmap for image loading
    //             Bitmap missingPhoto = BitmapFactory.decodeResource(getResources(), R.drawable.missing_image);
                
    //             // Combine edited and incomplete image observations
    //             List<Observation> combinedImageObservations = new ArrayList<>();
    //             combinedImageObservations.addAll(editedImageObservations);
    //             combinedImageObservations.addAll(incompleteImageObservations);
                
    //             // Load image details for edited/incomplete images
    //             imagesEditedIncomplete = dataHelper.getImageDetails(this, combinedImageObservations, missingPhoto);
                
    //             Log.d(TAG, "Loaded " + imagesEditedIncomplete.size() + " edited/incomplete image details in " + (System.currentTimeMillis() - startTime) + "ms");
                
    //             // Load the actual image data
    //             loadEditedIncompleteImages();
    //             putImages();
    //         }
    //     });
    // }

    private void sendData() {
        AsyncTask.execute(() -> {
            if (numNewObservations > 0) {
                try {
                    createObservations();
                } catch (InterruptedException e) {
                    Log.w(TAG, "Saving observations was interrupted", e);
                }
            } else {
                createObservationsComplete = true;
                uploadComplete();
            }

            if (numEditedObservations > 0) {
                updateObservations();
            } else {
                updateObservationsComplete = true;
                uploadComplete();
            }

            // Load image details only when needed
            if (numNewImages > 0) {
                Log.d(TAG, "Loading new image details");
                long startTime = System.currentTimeMillis();
                
                // Load image details for new images
                imagesNew = dataHelper.getImageDetails(this, newImageObservations);
                Log.d(TAG, "Loaded " + imagesNew.size() + " new image details in " + (System.currentTimeMillis() - startTime) + "ms");
                
                // Load the actual image data
                loadNewImages();
                postImages(imagesNew);
            }

            if (numEditedImages > 0 || numIncompleteImages > 0) {
                Log.d(TAG, "Loading edited/incomplete image details");
                long startTime = System.currentTimeMillis();
                
                // Combine edited and incomplete image observations
                List<Observation> combinedImageObservations = new ArrayList<>();
                combinedImageObservations.addAll(editedImageObservations);
                combinedImageObservations.addAll(incompleteImageObservations);
                
                // Load image details for edited/incomplete images
                imagesEditedIncomplete = dataHelper.getImageDetails(this, combinedImageObservations);
                Log.d(TAG, "Loaded " + imagesEditedIncomplete.size() + " edited/incomplete image details in " + (System.currentTimeMillis() - startTime) + "ms");
                
                // Load the actual image data
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

    // private void loadImage(FieldBookImage image) {
    //     Log.d(FLOW_TAG, "Loading image: " + image.getFileName());
    //     long startTime = System.currentTimeMillis();
    //     logMemoryUsage("Before loading image " + image.getFileName());
        
    //     try {
    //         image.loadImage(this);
    //         Log.d(PERFORMANCE_TAG, "Loaded image " + image.getFileName() + " in " + (System.currentTimeMillis() - startTime) + "ms");
    //     } catch (Exception e) {
    //         Log.e(TAG, "Failed to load image " + image.getFileName(), e);
    //         Utils.makeToast(this, getString(R.string.act_brapi_export_image_load_failed));
    //     }
        
    //     logMemoryUsage("After loading image " + image.getFileName());
    // }

    private void loadImage(FieldBookImage image) {
        try {
            Log.d(TAG, "Loading image: " + image.getFileName());
            long startTime = System.currentTimeMillis();
            
            image.loadImage(this);
            
            // Check if we loaded the actual image or the placeholder
            if (image.getImageData() != null && image.getImageData().length > 0) {
                Log.d(TAG, "Successfully loaded image: " + image.getFileName() + 
                    " (" + image.getWidth() + "x" + image.getHeight() + ", " + 
                    image.getFileSize() + " bytes) in " + 
                    (System.currentTimeMillis() - startTime) + "ms");
            } else {
                Log.w(TAG, "Failed to load image: " + image.getFileName() + 
                    ", using placeholder image instead");
            }
        } catch (Exception e) {
            Log.e(TAG, "Error loading image: " + image.getFileName(), e);
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
        Log.d(FLOW_TAG, "Starting to create " + newObservations.size() + " observations");
        long startTime = System.currentTimeMillis();
        
        int chunkSize = BrAPIService.getChunkSize(this);
        Log.d(PERFORMANCE_TAG, "Using chunk size of " + chunkSize + " for observations");
        
        brAPIService.createObservationsChunked(chunkSize, newObservations, (input, completedChunkNum, chunks, done) -> {
            Log.d(PERFORMANCE_TAG, "Created chunk " + completedChunkNum + " of " + chunks + " with " + input.size() + " observations in " + (System.currentTimeMillis() - startTime) + "ms");
            
            (BrapiExportActivity.this).runOnUiThread(() -> {
                processCreateObservationsResponse(input);
                
                numNewObservations -= input.size();
                numSyncedObservations += input.size();

                ((TextView) findViewById(R.id.brapiNumNewValue)).setText(String.valueOf(numNewObservations));
                ((TextView) findViewById(R.id.brapiNumSyncedValue)).setText(String.valueOf(numSyncedObservations));

                if(done) {
                    Log.d(FLOW_TAG, "All observation creation chunks completed");
                    createObservationsComplete = true;
                    uploadComplete();
                }
            });
        }, failureInput -> {
            Log.e(TAG, "Failed to create observations: error code " + failureInput);
            createObservationsError = processErrorCode(failureInput);
            createObservationsComplete = true;
            uploadComplete();
            return null;
        });
    }

    private void updateObservations() {
        Log.d(FLOW_TAG, "Starting to update " + editedObservations.size() + " observations");
        long startTime = System.currentTimeMillis();
        
        int chunkSize = BrAPIService.getChunkSize(this);
        Log.d(PERFORMANCE_TAG, "Using chunk size of " + chunkSize + " for observation updates");
        
        brAPIService.updateObservationsChunked(chunkSize, editedObservations, (input, completedChunkNum, chunks, done) -> {
            Log.d(PERFORMANCE_TAG, "Updated chunk " + completedChunkNum + " of " + chunks + " with " + input.size() + " observations in " + (System.currentTimeMillis() - startTime) + "ms");
            
            (BrapiExportActivity.this).runOnUiThread(() -> {
                processUpdateObservationsResponse(input);
                
                numEditedObservations -= input.size();
                numSyncedObservations += input.size();

                ((TextView) findViewById(R.id.brapiNumEditedValue)).setText(String.valueOf(numEditedObservations));
                ((TextView) findViewById(R.id.brapiNumSyncedValue)).setText(String.valueOf(numSyncedObservations));

                if(done) {
                    Log.d(FLOW_TAG, "All observation update chunks completed");
                    updateObservationsComplete = true;
                    uploadComplete();
                }
            });
        }, code -> {
            Log.e(TAG, "Failed to update observations: error code " + code);
            
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
        Log.d(FLOW_TAG, "Posting image metadata: " + imageData.getFileName());
        long startTime = System.currentTimeMillis();
        
        brAPIService.postImageMetaData(imageData,
                new Function<FieldBookImage, Void>() {
                    @Override
                    public Void apply(final FieldBookImage image) {
                        Log.d(PERFORMANCE_TAG, "Posted image metadata for " + imageData.getFileName() + " in " + (System.currentTimeMillis() - startTime) + "ms");
                        
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
                    Log.e(TAG, "Failed to post image metadata for " + imageData.getFileName() + ": error code " + code);
                    
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
                });
    }

    // Add logging to putImageContent
    private void putImageContent(final FieldBookImage image, final List<FieldBookImage> uploads) {
        Log.d(FLOW_TAG, "Uploading image content: " + image.getFileName());
        long startTime = System.currentTimeMillis();
        logMemoryUsage("Before uploading image content " + image.getFileName());
        
        brAPIService.putImageContent(image,
                new Function<FieldBookImage, Void>() {
                    @Override
                    public Void apply(final FieldBookImage responseImage) {
                        Log.d(PERFORMANCE_TAG, "Uploaded image content for " + image.getFileName() + " in " + (System.currentTimeMillis() - startTime) + "ms");
                        
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
                        Log.e(TAG, "Failed to upload image content for " + image.getFileName() + ": error code " + code);
                        
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
                });
        
        logMemoryUsage("After initiating image content upload " + image.getFileName());
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
        Log.d(FLOW_TAG, "Checking if upload is complete - Images: " + imagesComplete() + ", Observations: " + observationsComplete());
        Log.d(FLOW_TAG, "Image counts - New: " + numNewImages + ", Edited/Incomplete: " + (numEditedImages + numIncompleteImages));
        Log.d(FLOW_TAG, "Image updates - PostMetadata: " + postImageMetaDataUpdatesCount + ", PutContent: " + putImageContentUpdatesCount + ", PutMetadata: " + putImageMetaDataUpdatesCount);
        Log.d(FLOW_TAG, "Error states - Create: " + createObservationsError + ", Update: " + updateObservationsError + 
            ", PostImageMeta: " + postImageMetaDataError + ", PutImageContent: " + putImageContentError + 
            ", PutImageMeta: " + putImageMetaDataError);
        
        if (imagesComplete() && observationsComplete()) {
            Log.d(FLOW_TAG, "Upload process completed");
            hideSaving();

            // show upload status
            if (createObservationsError == UploadError.API_UNAUTHORIZED_ERROR ||
                    updateObservationsError == UploadError.API_UNAUTHORIZED_ERROR ||
                    postImageMetaDataError == UploadError.API_UNAUTHORIZED_ERROR ||
                    putImageContentError == UploadError.API_UNAUTHORIZED_ERROR ||
                    putImageMetaDataError == UploadError.API_UNAUTHORIZED_ERROR) {
                Log.d(FLOW_TAG, "Authentication error detected, showing auth dialog");
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
                        Log.e(TAG, "Failed to show auth dialog", e);
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

                final String finalMessage = message;
                Log.d(FLOW_TAG, "Showing upload result message: " + finalMessage);
                runOnUiThread(() -> {
                    Toast.makeText(this.getApplicationContext(), finalMessage, Toast.LENGTH_LONG).show();
                });
            }

            if (currentFieldIndex < fieldIds.size() - 1) {
                Log.d(FLOW_TAG, "More fields to process, showing next field button");
                showNextFieldButton();
            } else {
                Log.d(FLOW_TAG, "All fields processed, showing close button");
                loadStatistics();
                reset();
                showCloseButton();
            }
        }
    }

    private Boolean imagesComplete() {
        boolean complete = postImageMetaDataUpdatesCount == numNewImages &&
                putImageContentUpdatesCount == (numNewImages + numEditedImages + numIncompleteImages) &&
                putImageMetaDataUpdatesCount == (numEditedImages + numIncompleteImages);
        
        Log.d(FLOW_TAG, "Images complete check: " + complete + 
            " (PostMeta: " + postImageMetaDataUpdatesCount + "/" + numNewImages + 
            ", PutContent: " + putImageContentUpdatesCount + "/" + (numNewImages + numEditedImages + numIncompleteImages) + 
            ", PutMeta: " + putImageMetaDataUpdatesCount + "/" + (numEditedImages + numIncompleteImages) + ")");
        
        return complete;
    }

    private Boolean observationsComplete() {
        boolean complete = createObservationsComplete && updateObservationsComplete;
        Log.d(FLOW_TAG, "Observations complete check: " + complete + 
            " (Create: " + createObservationsComplete + 
            ", Update: " + updateObservationsComplete + ")");
        return complete;
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

    private UploadError processResponse(List<Observation> observationDbIds, List<Observation> observationsNeedingSync) {
        UploadError retVal = UploadError.NONE;
        SimpleDateFormat timeStamp = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSSZZZZZ",
                Locale.getDefault());
        String syncTime = timeStamp.format(Calendar.getInstance().getTime());

        if (observationDbIds.size() != observationsNeedingSync.size()) {
            retVal = UploadError.WRONG_NUM_OBSERVATIONS_RETURNED;
        } else {
            // TODO: updated equals key to the fieldbook db id, this might affect future syncing feature
            // Also would be nice to have a cleaner 'find' mechanism
            // For now, just use observationUnitDbId and observationVariableId to key off
            // Won't work for multiple observations of the same variable which we want to support in the future

            for (Observation converted : observationDbIds) {
                // find observation with matching keys and update observationDbId
                //int first_index = observationsNeedingSync.indexOf(converted);
                //int last_index = observationsNeedingSync.lastIndexOf(converted);
                int first_index = BrapiExportUtil.Companion.firstIndexOfDbId(observationsNeedingSync, converted);
                int last_index = BrapiExportUtil.Companion.lastIndexOfDbId(observationsNeedingSync, converted);
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

    // private void loadStatistics() {

    //     Log.d(PERFORMANCE_TAG, "Starting loadStatistics");
    //     long startTime = System.currentTimeMillis();
    //     logMemoryUsage("Before loadStatistics");

    //     numNewObservations = 0;
    //     numSyncedObservations = 0;
    //     numEditedObservations = 0;
    //     numNewImages = 0;
    //     numEditedImages = 0;
    //     numSyncedImages = 0;
    //     numIncompleteImages = 0;

    //     // String hostURL = BrAPIService.getHostUrl(this);
    //     // List<Observation> observations = dataHelper.getObservations(fieldId, hostURL);
    //     // List<Observation> userCreatedTraitObservations = dataHelper.getUserTraitObservations(fieldId);
    //     // List<Observation> wrongSourceObservations = dataHelper.getWrongSourceObservations(hostURL);

    //     // List<FieldBookImage> images = dataHelper.getImageObservations(this, hostURL);
    //     // imagesNew.clear();
    //     // imagesEditedIncomplete.clear();
    //     // List<FieldBookImage> userCreatedTraitImages = dataHelper.getUserTraitImageObservations(this, fieldId);
    //     // List<FieldBookImage> wrongSourceImages = dataHelper.getWrongSourceImageObservations(this, hostURL);

    //     String hostURL = BrAPIService.getHostUrl(this);

    //     Log.d(PERFORMANCE_TAG, "Fetching observations from database");
    //     long dbStartTime = System.currentTimeMillis();
    //     List<Observation> observations = dataHelper.getObservations(fieldId, hostURL);
    //     Log.d(PERFORMANCE_TAG, "Fetched " + observations.size() + " observations in " + (System.currentTimeMillis() - dbStartTime) + "ms");
        
    //     dbStartTime = System.currentTimeMillis();
    //     List<Observation> userCreatedTraitObservations = dataHelper.getUserTraitObservations(fieldId);
    //     Log.d(PERFORMANCE_TAG, "Fetched " + userCreatedTraitObservations.size() + " user created observations in " + (System.currentTimeMillis() - dbStartTime) + "ms");
        
    //     dbStartTime = System.currentTimeMillis();
    //     List<Observation> wrongSourceObservations = dataHelper.getWrongSourceObservations(hostURL);
    //     Log.d(PERFORMANCE_TAG, "Fetched " + wrongSourceObservations.size() + " wrong source observations in " + (System.currentTimeMillis() - dbStartTime) + "ms");

    //     dbStartTime = System.currentTimeMillis();
    //     List<FieldBookImage> images = dataHelper.getImageObservations(this, hostURL);
    //     Log.d(PERFORMANCE_TAG, "Fetched " + images.size() + " images in " + (System.currentTimeMillis() - dbStartTime) + "ms");
        
    //     imagesNew.clear();
    //     imagesEditedIncomplete.clear();
        
    //     dbStartTime = System.currentTimeMillis();
    //     List<FieldBookImage> userCreatedTraitImages = dataHelper.getUserTraitImageObservations(this, fieldId);
    //     Log.d(PERFORMANCE_TAG, "Fetched " + userCreatedTraitImages.size() + " user created images in " + (System.currentTimeMillis() - dbStartTime) + "ms");
        
    //     dbStartTime = System.currentTimeMillis();
    //     List<FieldBookImage> wrongSourceImages = dataHelper.getWrongSourceImageObservations(this, hostURL);
    //     Log.d(PERFORMANCE_TAG, "Fetched " + wrongSourceImages.size() + " wrong source images in " + (System.currentTimeMillis() - dbStartTime) + "ms");

    //     Log.d(PERFORMANCE_TAG, "Processing observations and images");
    //     long processStartTime = System.currentTimeMillis();

    //     for (Observation observation : observations) {
    //         switch (observation.getStatus()) {
    //             case NEW:
    //                 numNewObservations++;
    //                 newObservations.add(observation);
    //                 break;
    //             case SYNCED:
    //                 numSyncedObservations++;
    //                 break;
    //             case EDITED:
    //                 numEditedObservations++;
    //                 editedObservations.add(observation);
    //                 break;
    //         }
    //     }
    //     Log.d(PERFORMANCE_TAG, "Processed observations in " + (System.currentTimeMillis() - processStartTime) + "ms");

    //     processStartTime = System.currentTimeMillis();
    //     for (FieldBookImage image : images) {
    //         switch (image.getStatus()) {
    //             case NEW:
    //                 numNewImages++;
    //                 imagesNew.add(image);
    //                 break;
    //             case SYNCED:
    //                 numSyncedImages++;
    //                 break;
    //             case EDITED:
    //                 numEditedImages++;
    //                 imagesEditedIncomplete.add(image);
    //                 break;
    //             case INCOMPLETE:
    //                 numIncompleteImages++;
    //                 imagesEditedIncomplete.add(image);
    //                 break;
    //         }
    //     }

    //     Log.d(PERFORMANCE_TAG, "Processed images in " + (System.currentTimeMillis() - processStartTime) + "ms");
    //     dbStartTime = System.currentTimeMillis();

    //     FieldObject field = dataHelper.getFieldObject(fieldId);

    //     Log.d(PERFORMANCE_TAG, "Fetched field object in " + (System.currentTimeMillis() - dbStartTime) + "ms");
    //     Log.d(PERFORMANCE_TAG, "Updating UI with statistics");
    //     long uiStartTime = System.currentTimeMillis();  // Added the missing variable

    //     runOnUiThread(() -> {
    //         ((TextView) findViewById(R.id.brapistudyValue)).setText(field.getExp_alias());
    //         ((TextView) findViewById(R.id.brapiNumNewValue)).setText(String.valueOf(numNewObservations));
    //         ((TextView) findViewById(R.id.brapiNumSyncedValue)).setText(String.valueOf(numSyncedObservations));
    //         ((TextView) findViewById(R.id.brapiNumEditedValue)).setText(String.valueOf(numEditedObservations));
    //         ((TextView) findViewById(R.id.brapiUserCreatedValue)).setText(String.valueOf(userCreatedTraitObservations.size()));
    //         ((TextView) findViewById(R.id.brapiWrongSource)).setText(String.valueOf(wrongSourceObservations.size()));

    //         ((TextView) findViewById(R.id.brapiNumNewImagesValue)).setText(String.valueOf(numNewImages));
    //         ((TextView) findViewById(R.id.brapiNumSyncedImagesValue)).setText(String.valueOf(numSyncedImages));
    //         ((TextView) findViewById(R.id.brapiNumEditedImagesValue)).setText(String.valueOf(numEditedImages));
    //         ((TextView) findViewById(R.id.brapiNumIncompleteImagesValue)).setText(String.valueOf(numIncompleteImages));

    //         ((TextView) findViewById(R.id.brapiUserCreatedImagesValue)).setText(String.valueOf(userCreatedTraitImages.size()));
    //         ((TextView) findViewById(R.id.brapiWrongSourceImages)).setText(String.valueOf(wrongSourceImages.size()));
    //         Log.d(PERFORMANCE_TAG, "UI update completed in " + (System.currentTimeMillis() - uiStartTime) + "ms");
    //     });

    //     Log.d(PERFORMANCE_TAG, "loadStatistics completed in " + (System.currentTimeMillis() - startTime) + "ms");
    //     logMemoryUsage("After loadStatistics");
    // }

    @SuppressWarnings("unchecked")
    private void loadStatistics() {
        Log.d(TAG, "Starting loadStatistics");
        long startTime = System.currentTimeMillis();

        String hostURL = BrAPIService.getHostUrl(this);
        
        // Get all categorized observations in a single query
        Map<String, List<Observation>> exportData = dataHelper.getBrAPIExportData(fieldId, hostURL);
        
        // Get the lists from the map
        newObservations = exportData.get("newObservations");
        List<Observation> syncedObservations = exportData.get("syncedObservations");
        editedObservations = exportData.get("editedObservations");
        newImageObservations = exportData.get("newImageObservations");
        syncedImageObservations = exportData.get("syncedImageObservations");
        editedImageObservations = exportData.get("editedImageObservations");
        incompleteImageObservations = exportData.get("incompleteImageObservations");
        List<Observation> userCreatedTraitObservations = exportData.get("userCreatedTraitObservations");
        List<Observation> wrongSourceObservations = exportData.get("wrongSourceObservations");
        List<Observation> userCreatedImageObservations = exportData.get("userCreatedImageObservations");
        List<Observation> wrongSourceImageObservations = exportData.get("wrongSourceImageObservations");
        
        // Set counts based on list sizes
        numNewObservations = newObservations.size();
        numSyncedObservations = syncedObservations.size();
        numEditedObservations = editedObservations.size();
        numNewImages = newImageObservations.size();
        numSyncedImages = syncedImageObservations.size();
        numEditedImages = editedImageObservations.size();
        numIncompleteImages = incompleteImageObservations.size();
        
        // Store the image observation lists for later use
        this.newImageObservations = newImageObservations;
        this.editedImageObservations = editedImageObservations;
        this.incompleteImageObservations = incompleteImageObservations;
        
        // Clear image lists since we don't need them yet
        imagesNew.clear();
        imagesEditedIncomplete.clear();
        
        FieldObject field = dataHelper.getFieldObject(fieldId);
        
        Log.d(TAG, "loadStatistics completed in " + (System.currentTimeMillis() - startTime) + "ms");

        runOnUiThread(() -> {
            ((TextView) findViewById(R.id.brapistudyValue)).setText(field.getExp_alias());
            ((TextView) findViewById(R.id.brapiNumNewValue)).setText(String.valueOf(numNewObservations));
            ((TextView) findViewById(R.id.brapiNumSyncedValue)).setText(String.valueOf(numSyncedObservations));
            ((TextView) findViewById(R.id.brapiNumEditedValue)).setText(String.valueOf(numEditedObservations));
            ((TextView) findViewById(R.id.brapiUserCreatedValue)).setText(String.valueOf(userCreatedTraitObservations.size()));
            ((TextView) findViewById(R.id.brapiWrongSource)).setText(String.valueOf(wrongSourceObservations.size()));

            ((TextView) findViewById(R.id.brapiNumNewImagesValue)).setText(String.valueOf(numNewImages));
            ((TextView) findViewById(R.id.brapiNumSyncedImagesValue)).setText(String.valueOf(numSyncedImages));
            ((TextView) findViewById(R.id.brapiNumEditedImagesValue)).setText(String.valueOf(numEditedImages));
            ((TextView) findViewById(R.id.brapiNumIncompleteImagesValue)).setText(String.valueOf(numIncompleteImages));

            ((TextView) findViewById(R.id.brapiUserCreatedImagesValue)).setText(String.valueOf(userCreatedImageObservations.size()));
            ((TextView) findViewById(R.id.brapiWrongSourceImages)).setText(String.valueOf(wrongSourceImageObservations.size()));
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
            Toast.makeText(this, "All fields processed", Toast.LENGTH_SHORT).show();
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